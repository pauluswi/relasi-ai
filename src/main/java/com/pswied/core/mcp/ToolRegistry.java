package com.pswied.core.mcp;

import com.pswied.core.crm.AuditService;
import com.pswied.core.crm.CaseService;
import com.pswied.core.crm.CrmAdapter;
import com.pswied.core.crm.CrmJson;
import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.InteractionService;
import com.pswied.core.knowledge.KnowledgeService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** In-process MCP-compatible tool discovery and invocation with confirmed writes. */
public final class ToolRegistry {
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);
    private final CrmAdapter crm;
    private final CaseService cases;
    private final InteractionService interactions;
    private final KnowledgeService knowledge;
    private final AuditService audit;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
    private final Map<String, PendingAction> pendingActions = new ConcurrentHashMap<>();

    public ToolRegistry(CrmAdapter crm, CaseService cases, InteractionService interactions,
                        KnowledgeService knowledge, AuditService audit) {
        this.crm = crm;
        this.cases = cases;
        this.interactions = interactions;
        this.knowledge = knowledge;
        this.audit = audit;
        registerTools();
    }

    public List<Map<String, Object>> discover() {
        return tools.values().stream().map(tool -> tool.definition().asJsonValue()).toList();
    }

    public void clearPendingConfirmations() {
        pendingActions.clear();
    }

    public InvocationResult invoke(ToolUser user, String toolName, Map<String, String> submittedArguments) {
        Instant now = Instant.now();
        pendingActions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        RegisteredTool tool = tools.get(toolName);
        if (tool == null) {
            audit.record(user.id(), "MCP_TOOL_REJECTED", toolName, "TOOL_NOT_FOUND");
            throw new ToolInvocationException(404, "tool_not_found", "Unknown tool.");
        }
        String confirmationToken = submittedArguments.get("confirmationToken");
        Map<String, String> arguments = submittedArguments.entrySet().stream()
                .filter(entry -> !"confirmationToken".equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> second, LinkedHashMap::new));

        if (confirmationToken != null && !confirmationToken.isBlank() && !arguments.isEmpty()) {
            throw new ToolInvocationException(400, "confirmation_payload_not_allowed",
                    "Confirmation uses the reviewed server-stored proposal; do not submit new tool arguments.");
        }

        if (tool.definition().readOnly()) {
            if (confirmationToken != null && !confirmationToken.isBlank()) {
                throw new ToolInvocationException(400, "unexpected_confirmation", "Read-only tools do not use confirmation tokens.");
            }
            try {
                validate(tool.definition(), arguments);
            } catch (ToolInvocationException exception) {
                audit.record(user.id(), "MCP_TOOL_FAILED", toolName, exception.code());
                throw exception;
            }
            try {
                Map<String, Object> result = tool.execute().apply(new InvocationContext(user, arguments));
                audit.record(user.id(), "MCP_TOOL_INVOKED", toolName, "SUCCESS");
                return new InvocationResult(200, Map.of("tool", toolName, "readOnly", true, "status", "COMPLETED", "result", result));
            } catch (ToolInvocationException exception) {
                audit.record(user.id(), "MCP_TOOL_DENIED", toolName, exception.code());
                throw exception;
            } catch (CaseService.CaseNotFoundException exception) {
                audit.record(user.id(), "MCP_TOOL_DENIED", toolName, "CASE_NOT_FOUND");
                throw new ToolInvocationException(404, "case_not_found", "Case was not found in the user's scope.");
            } catch (IllegalArgumentException exception) {
                audit.record(user.id(), "MCP_TOOL_FAILED", toolName, "VALIDATION_FAILED");
                throw new ToolInvocationException(400, "invalid_arguments", exception.getMessage());
            }
        }

        try {
            requireRole(tool.definition(), user);
        } catch (ToolInvocationException exception) {
            audit.record(user.id(), "MCP_WRITE_PREPARATION_DENIED", toolName, exception.code());
            throw exception;
        }
        if (confirmationToken != null && !confirmationToken.isBlank()) {
            return confirm(user, tool, confirmationToken);
        }
        try {
            validate(tool.definition(), arguments);
            Map<String, Object> preview = tool.preview().apply(new InvocationContext(user, arguments));
            String token = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plus(CONFIRMATION_TTL);
            pendingActions.put(token, new PendingAction(user.id(), toolName, Map.copyOf(arguments), preview, expiresAt));
            audit.record(user.id(), "MCP_WRITE_PREPARED", toolName, "AWAITING_CONFIRMATION");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tool", toolName);
            response.put("readOnly", false);
            response.put("status", "AWAITING_CONFIRMATION");
            response.put("confirmationRequired", true);
            response.put("confirmationToken", token);
            response.put("expiresAt", expiresAt.toString());
            response.put("preview", preview);
            return new InvocationResult(202, response);
        } catch (ToolInvocationException exception) {
            audit.record(user.id(), "MCP_WRITE_PREPARATION_DENIED", toolName, exception.code());
            throw exception;
        } catch (CaseService.CaseNotFoundException exception) {
            audit.record(user.id(), "MCP_WRITE_PREPARATION_DENIED", toolName, "CASE_NOT_FOUND");
            throw new ToolInvocationException(404, "case_not_found", "Case was not found in the user's scope.");
        } catch (IllegalArgumentException exception) {
            audit.record(user.id(), "MCP_WRITE_PREPARATION_FAILED", toolName, "VALIDATION_FAILED");
            throw new ToolInvocationException(400, "invalid_arguments", exception.getMessage());
        }
    }

    private InvocationResult confirm(ToolUser user, RegisteredTool requestedTool, String token) {
        PendingAction pending = pendingActions.get(token);
        if (pending == null) throw new ToolInvocationException(409, "confirmation_expired", "Confirmation is invalid, expired, or already used.");
        if (!pending.actorId().equals(user.id())) {
            audit.record(user.id(), "MCP_WRITE_CONFIRMATION_DENIED", requestedTool.definition().name(), "ACTOR_MISMATCH");
            throw new ToolInvocationException(403, "confirmation_actor_mismatch", "Only the user who reviewed this preview can confirm it.");
        }
        if (!pending.toolName().equals(requestedTool.definition().name())) {
            throw new ToolInvocationException(409, "confirmation_tool_mismatch", "Confirmation does not match this tool.");
        }
        if (pending.expiresAt().isBefore(Instant.now())) {
            pendingActions.remove(token, pending);
            throw new ToolInvocationException(409, "confirmation_expired", "Confirmation expired. Prepare a new preview.");
        }
        requireRole(requestedTool.definition(), user);
        validate(requestedTool.definition(), pending.arguments());
        if (!pendingActions.remove(token, pending)) {
            throw new ToolInvocationException(409, "confirmation_already_used", "This confirmation has already been used.");
        }
        try {
            Map<String, Object> result = requestedTool.execute().apply(new InvocationContext(user, pending.arguments()));
            audit.record(user.id(), "MCP_WRITE_CONFIRMED", requestedTool.definition().name(), "SUCCESS");
            return new InvocationResult(200, Map.of("tool", requestedTool.definition().name(), "readOnly", false,
                    "status", "COMPLETED", "result", result));
        } catch (ToolInvocationException exception) {
            audit.record(user.id(), "MCP_WRITE_EXECUTION_DENIED", requestedTool.definition().name(), exception.code());
            throw exception;
        } catch (CaseService.CaseNotFoundException exception) {
            audit.record(user.id(), "MCP_WRITE_EXECUTION_DENIED", requestedTool.definition().name(), "CASE_NOT_FOUND");
            throw new ToolInvocationException(404, "case_not_found", "Case was not found in the user's scope.");
        } catch (IllegalArgumentException exception) {
            audit.record(user.id(), "MCP_WRITE_EXECUTION_FAILED", requestedTool.definition().name(), "VALIDATION_FAILED");
            throw new ToolInvocationException(400, "invalid_arguments", exception.getMessage());
        }
    }

    private void registerTools() {
        read("search_customer", "Search authorized synthetic customer profiles.",
                List.of(parameter("query", true, 100, "Name, customer identifier, relationship number, or segment.")), context -> {
                    var found = crm.searchCustomers(context.arguments().get("query"), context.user().customerScope());
                    return Map.of("customers", found.stream().map(CrmJson::customerSummary).toList(), "total", found.size());
                });

        read("get_customer_360", "Retrieve the authorized Customer 360 profile.",
                List.of(parameter("customerId", true, 40, "Synthetic customer identifier.")), context ->
                        Map.of("customer", CrmJson.customer360(customer360(context.user(), context.arguments().get("customerId")))));

        read("get_customer_cases", "Retrieve cases for an authorized customer.",
                List.of(parameter("customerId", true, 40, "Synthetic customer identifier.")), context -> {
                    requireCustomer(context.user(), context.arguments().get("customerId"));
                    var found = cases.list(context.user().customerScope(), context.arguments().get("customerId"), null);
                    return Map.of("cases", found.stream().map(CrmJson::caseSummary).toList(), "total", found.size());
                });

        read("search_unresolved_payment_complaints", "Find unresolved payment complaints only within the user's customer scope and group the matching cases by customer.",
                List.of(), context -> {
                    List<Map<String, Object>> customerGroups = new ArrayList<>();
                    int totalCases = 0;
                    for (var customer : crm.searchCustomers("", context.user().customerScope())) {
                        var matchingCases = cases.list(context.user().customerScope(), customer.id(), null).stream()
                                .filter(serviceCase -> List.of("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER").contains(serviceCase.status()))
                                .filter(serviceCase -> List.of("PAYMENT_COMPLAINT", "TRANSACTION_DISPUTE").contains(serviceCase.category()))
                                .map(CrmJson::caseSummary).toList();
                        if (!matchingCases.isEmpty()) {
                            totalCases += matchingCases.size();
                            customerGroups.add(Map.of("customer", CrmJson.customerSummary(customer), "cases", matchingCases,
                                    "unresolvedCaseCount", matchingCases.size()));
                        }
                    }
                    return Map.of("customers", customerGroups, "totalCustomers", customerGroups.size(), "totalCases", totalCases,
                            "scope", "authorized-customer-scope");
                });

        read("get_payment_status", "Retrieve mock payment status for an authorized synthetic transaction.",
                List.of(parameter("transactionReference", true, 50, "Synthetic transaction reference.")), context -> {
                    String reference = context.arguments().get("transactionReference");
                    PaymentStatus payment = paymentStatus(reference);
                    if (payment != null && !context.user().canAccess(payment.customerId())) notFound("transaction_not_found");
                    if (payment == null) return Map.of("transactionReference", reference, "status", "UNKNOWN", "source", "MOCK_PAYMENT_SIMULATOR");
                    return Map.of("transactionReference", reference, "customerId", payment.customerId(),
                            "status", payment.status(), "lastUpdated", payment.lastUpdated(), "source", "MOCK_PAYMENT_SIMULATOR");
                });

        read("search_knowledge", "Search bundled synthetic knowledge documents and return cited passages.",
                List.of(parameter("query", true, 300, "Knowledge question or search phrase.")), context -> {
                    var results = knowledge.search(context.arguments().get("query"), 5).stream()
                            .map(KnowledgeService.RetrievedChunk::asJsonValue).toList();
                    return Map.of("results", results, "total", results.size(), "mode", "synthetic-mock");
                });

        write("create_service_case", "Prepare and, after explicit confirmation, create an in-scope service case.",
                List.of(parameter("customerId", true, 40, "Customer identifier."),
                        enumeration("category", true, "Supported case category.", "PAYMENT_COMPLAINT", "ACCOUNT_INQUIRY", "LOAN_INQUIRY", "CARD_ISSUE", "MOBILE_BANKING_ISSUE", "TRANSACTION_DISPUTE", "GENERAL_BANKING_INQUIRY"),
                        enumeration("priority", true, "Case priority.", "HIGH", "NORMAL", "LOW"),
                        parameter("subject", true, 120, "Short case title."),
                        parameter("description", true, 2000, "Case description.")),
                context -> {
                    requireCustomer(context.user(), context.arguments().get("customerId"));
                    requireNonBlank(context.arguments(), "subject");
                    requireNonBlank(context.arguments(), "description");
                    return Map.of("customerId", context.arguments().get("customerId"), "category", context.arguments().get("category"),
                            "priority", context.arguments().get("priority"), "subject", context.arguments().get("subject"),
                            "description", context.arguments().get("description"), "requestedBy", context.user().id());
                }, context -> {
                    requireCustomer(context.user(), context.arguments().get("customerId"));
                    var created = cases.create(context.user().id(), context.arguments().get("customerId"),
                            context.arguments().get("category"), context.arguments().get("priority"),
                            context.arguments().get("subject"), context.arguments().get("description"));
                    return Map.of("case", CrmJson.serviceCase(created));
                });

        write("update_case_status", "Prepare and, after explicit confirmation, update an in-scope case status.",
                List.of(parameter("caseReference", true, 50, "Synthetic case reference."),
                        enumeration("status", true, "Requested case status.", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"),
                        parameter("resolution", false, 2000, "Required when status is RESOLVED.")),
                context -> {
                    var serviceCase = cases.validateStatusChange(context.arguments().get("caseReference"),
                            context.arguments().get("status"), context.arguments().get("resolution"), context.user().customerScope());
                    return Map.of("caseReference", serviceCase.reference(), "customerId", serviceCase.customerId(),
                            "currentStatus", serviceCase.status(), "requestedStatus", context.arguments().get("status"),
                            "resolution", context.arguments().getOrDefault("resolution", ""), "requestedBy", context.user().id());
                }, context -> {
                    var updated = cases.updateStatus(context.user().id(), context.arguments().get("caseReference"),
                            context.arguments().get("status"), context.arguments().get("resolution"), context.user().customerScope());
                    return Map.of("case", CrmJson.serviceCase(updated));
                });

        write("assign_case", "Prepare and, after explicit confirmation, assign an in-scope case to a service officer.",
                List.of("CRM_SUPERVISOR", "CRM_ADMINISTRATOR"),
                List.of(parameter("caseReference", true, 50, "Synthetic case reference."),
                        enumeration("assigneeId", true, "Seeded customer service officer.", "service-officer")),
                context -> {
                    var serviceCase = cases.validateAssignment(context.arguments().get("caseReference"),
                            context.arguments().get("assigneeId"), context.user().customerScope());
                    return Map.of("caseReference", serviceCase.reference(), "customerId", serviceCase.customerId(),
                            "currentAssignee", serviceCase.assignedTo(), "requestedAssignee", context.arguments().get("assigneeId"),
                            "requestedBy", context.user().id());
                }, context -> {
                    var updated = cases.assign(context.user().id(), context.arguments().get("caseReference"),
                            context.arguments().get("assigneeId"), context.user().customerScope());
                    return Map.of("case", CrmJson.serviceCase(updated));
                });

        read("get_customer_products", "Retrieve product relationships for an authorized customer.",
                List.of(parameter("customerId", true, 40, "Synthetic customer identifier.")), context -> {
                    Customer360 customer = customer360(context.user(), context.arguments().get("customerId"));
                    return Map.of("customerId", customer.customer().id(), "products",
                            customer.products().stream().map(product -> Map.of("id", product.id(), "productType", product.productType(),
                                    "displayName", product.displayName(), "status", product.status(),
                                    "maskedReference", product.maskedReference(), "currency", product.currency(),
                                    "availableBalance", product.availableBalance())).toList());
                });
    }

    private void read(String name, String description, List<ToolParameter> parameters,
                      Function<InvocationContext, Map<String, Object>> execute) {
        ToolDefinition definition = new ToolDefinition(name, description, true, List.of(), parameters);
        tools.put(name, new RegisteredTool(definition, null, execute));
    }

    private void write(String name, String description, List<ToolParameter> parameters,
                       Function<InvocationContext, Map<String, Object>> preview,
                       Function<InvocationContext, Map<String, Object>> execute) {
        write(name, description, List.of("CUSTOMER_SERVICE_OFFICER", "CRM_SUPERVISOR", "CRM_ADMINISTRATOR"),
                parameters, preview, execute);
    }

    private void write(String name, String description, List<String> allowedRoles, List<ToolParameter> parameters,
                       Function<InvocationContext, Map<String, Object>> preview,
                       Function<InvocationContext, Map<String, Object>> execute) {
        ToolDefinition definition = new ToolDefinition(name, description, false, allowedRoles, parameters);
        tools.put(name, new RegisteredTool(definition, preview, execute));
    }

    private Customer360 customer360(ToolUser user, String customerId) {
        var base = crm.getCustomer360(customerId, user.customerScope());
        if (base.isEmpty()) notFound("customer_not_found");
        Customer360 customer = base.orElseThrow();
        return new Customer360(customer.customer(), customer.contacts(), customer.products(),
                interactions.list(customerId, user.customerScope()), cases.list(user.customerScope(), customerId, null));
    }

    private void requireCustomer(ToolUser user, String customerId) {
        customer360(user, customerId);
    }

    private static PaymentStatus paymentStatus(String reference) {
        return switch (reference) {
            case "TXN-DEMO-1001" -> new PaymentStatus("CUST-1001", "SETTLED", "2026-11-23T02:20:00Z");
            case "TXN-DEMO-1002" -> new PaymentStatus("CUST-1002", "PENDING", "2026-11-24T01:15:00Z");
            case "TXN-DEMO-1004" -> new PaymentStatus("CUST-1004", "FAILED", "2026-11-23T02:00:00Z");
            default -> null;
        };
    }

    private static void validate(ToolDefinition definition, Map<String, String> arguments) {
        Set<String> allowed = definition.parameters().stream().map(ToolParameter::name).collect(Collectors.toSet());
        for (String key : arguments.keySet()) {
            if (!allowed.contains(key)) throw new ToolInvocationException(400, "unknown_argument", "Unknown argument: " + key);
        }
        for (ToolParameter parameter : definition.parameters()) {
            String value = arguments.get(parameter.name());
            if (parameter.required() && (value == null || value.isBlank())) {
                throw new ToolInvocationException(400, "missing_argument", "Required argument is missing: " + parameter.name());
            }
            if (value == null) continue;
            if (value.length() > parameter.maxLength()) {
                throw new ToolInvocationException(400, "argument_too_long", parameter.name() + " is too long.");
            }
            if (!parameter.enumValues().isEmpty() && !parameter.enumValues().contains(value)) {
                throw new ToolInvocationException(400, "invalid_enum", "Unsupported value for " + parameter.name() + ".");
            }
        }
    }

    private static void requireRole(ToolDefinition definition, ToolUser user) {
        if (!definition.allowedRoles().contains(user.role())) {
            throw new ToolInvocationException(403, "forbidden", "User role is not authorized for this write tool.");
        }
    }

    private static void requireNonBlank(Map<String, String> arguments, String name) {
        if (arguments.get(name) == null || arguments.get(name).isBlank()) {
            throw new ToolInvocationException(400, "missing_argument", "Required argument is missing: " + name);
        }
    }

    private static void notFound(String code) {
        throw new ToolInvocationException(404, code, "Requested record was not found in the user's scope.");
    }

    private static ToolParameter parameter(String name, boolean required, int maxLength, String description) {
        return new ToolParameter(name, required, maxLength, description, List.of());
    }

    private static ToolParameter enumeration(String name, boolean required, String description, String... values) {
        return new ToolParameter(name, required, 2000, description, List.of(values));
    }

    private record InvocationContext(ToolUser user, Map<String, String> arguments) {
    }

    private record RegisteredTool(ToolDefinition definition,
                                  Function<InvocationContext, Map<String, Object>> preview,
                                  Function<InvocationContext, Map<String, Object>> execute) {
    }

    private record PendingAction(String actorId, String toolName, Map<String, String> arguments,
                                 Map<String, Object> preview, Instant expiresAt) {
    }

    private record PaymentStatus(String customerId, String status, String lastUpdated) {
    }

    private record ToolParameter(String name, boolean required, int maxLength, String description,
                                 List<String> enumValues) {
        Map<String, Object> asJsonValue() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "string");
            result.put("description", description);
            result.put("maxLength", maxLength);
            if (!enumValues.isEmpty()) result.put("enum", enumValues);
            return result;
        }
    }

    private record ToolDefinition(String name, String description, boolean readOnly,
                                  List<String> allowedRoles, List<ToolParameter> parameters) {
        Map<String, Object> asJsonValue() {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (ToolParameter parameter : parameters) properties.put(parameter.name(), parameter.asJsonValue());
            List<String> required = parameters.stream().filter(ToolParameter::required).map(ToolParameter::name).toList();
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", required);
            schema.put("additionalProperties", false);
            return Map.of("name", name, "description", description, "readOnly", readOnly,
                    "allowedRoles", allowedRoles, "inputSchema", schema);
        }
    }

    public record InvocationResult(int statusCode, Map<String, Object> payload) {
    }

    public static final class ToolInvocationException extends RuntimeException {
        private final int statusCode;
        private final String code;

        public ToolInvocationException(int statusCode, String code, String message) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
        }

        public int statusCode() { return statusCode; }
        public String code() { return code; }
    }
}
