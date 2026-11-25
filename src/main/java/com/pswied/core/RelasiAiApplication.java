package com.pswied.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.pswied.core.crm.CrmAdapter;
import com.pswied.core.crm.CrmJson;
import com.pswied.core.crm.AuditService;
import com.pswied.core.crm.CaseService;
import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.DynamicsStyleMockCrmAdapter;
import com.pswied.core.crm.InteractionService;
import com.pswied.core.crm.SalesforceStyleMockCrmAdapter;
import com.pswied.core.ai.AssistantResponse;
import com.pswied.core.ai.AssistantService;
import com.pswied.core.ai.DeterministicNlpEngine;
import com.pswied.core.ai.MockLlmProvider;
import com.pswied.core.knowledge.KnowledgeService;
import com.pswied.core.mcp.ToolRegistry;
import com.pswied.core.mcp.ToolUser;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Starts the dependency-free, loopback-only RELASI AI showcase shell. */
public final class RelasiAiApplication {
    private static final String API_PREFIX = "/api/v1";
    private static final String CRM_PROFILE = configuredCrmProfile();
    private static final CrmAdapter CRM = createCrmAdapter(CRM_PROFILE);
    private static final AuditService AUDIT = new AuditService();
    private static final CaseService CASES = new CaseService(AUDIT);
    private static final InteractionService INTERACTIONS = new InteractionService(AUDIT);
    private static final KnowledgeService KNOWLEDGE = new KnowledgeService();
    private static final AssistantService ASSISTANT = new AssistantService(new DeterministicNlpEngine(), new MockLlmProvider(), KNOWLEDGE, CRM_PROFILE);
    private static final ToolRegistry TOOLS = new ToolRegistry(CRM, CASES, INTERACTIONS, KNOWLEDGE, AUDIT);

    private RelasiAiApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port;
        try {
            port = configuredPort();
        } catch (IllegalArgumentException exception) {
            System.err.println("Configuration error: " + exception.getMessage());
            System.exit(2);
            return;
        }
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (BindException exception) {
            System.err.printf("Startup error: cannot bind to 127.0.0.1:%d; the port may already be in use. Set RELASI_PORT to another port.%n", port);
            System.exit(1);
            return;
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", RelasiAiApplication::handle);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }, "relasi-ai-shutdown"));

        System.out.printf("RELASI AI local mock shell started at http://127.0.0.1:%d%n", port);
        System.out.println("No database, Docker, network connection, or credentials are used.");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int configuredPort() {
        String value = System.getenv().getOrDefault("RELASI_PORT", "8080");
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("RELASI_PORT must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("RELASI_PORT must be a valid port number.");
        }
    }

    private static String configuredCrmProfile() {
        String profile = System.getenv().getOrDefault("RELASI_CRM_ADAPTER", "DYNAMICS").strip().toUpperCase(Locale.ROOT);
        if (!profile.equals("DYNAMICS") && !profile.equals("SALESFORCE")) {
            throw new IllegalArgumentException("RELASI_CRM_ADAPTER must be DYNAMICS or SALESFORCE.");
        }
        return profile;
    }

    private static CrmAdapter createCrmAdapter(String profile) {
        return profile.equals("SALESFORCE") ? new SalesforceStyleMockCrmAdapter() : new DynamicsStyleMockCrmAdapter();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (IllegalArgumentException exception) {
            badRequest(exchange, "Malformed request encoding.");
        }
    }

    private static void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || "/index.html".equals(path)) {
            serveIndex(exchange);
            return;
        }

        if (!path.startsWith(API_PREFIX)) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            return;
        }

        if (path.equals(API_PREFIX + "/cases") || path.startsWith(API_PREFIX + "/cases/")) {
            handleCaseRequest(exchange, path);
            return;
        }
        if (path.equals(API_PREFIX + "/interactions")) {
            handleInteractionRequest(exchange);
            return;
        }
        if (path.equals(API_PREFIX + "/audit")) {
            handleAuditRequest(exchange);
            return;
        }
        if (path.equals(API_PREFIX + "/assistant/respond")) {
            handleAssistantRequest(exchange);
            return;
        }
        if (path.equals(API_PREFIX + "/knowledge/documents") || path.equals(API_PREFIX + "/knowledge/search")) {
            handleKnowledgeRequest(exchange, path);
            return;
        }
        if (path.equals(API_PREFIX + "/tools")) {
            handleToolDiscovery(exchange);
            return;
        }
        if (path.equals(API_PREFIX + "/reset")) {
            resetDemoState(exchange);
            return;
        }
        if (path.startsWith(API_PREFIX + "/tools/")) {
            handleToolInvocation(exchange, path.substring((API_PREFIX + "/tools/").length()));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }

        if (path.startsWith(API_PREFIX + "/customers/")) {
            serveCustomer360(exchange, path.substring((API_PREFIX + "/customers/").length()));
            return;
        }

        switch (path) {
            case API_PREFIX + "/health" -> send(exchange, 200, "application/json; charset=utf-8",
                    CrmJson.stringify(Map.of("status", "UP", "mode", "mock", "storage", "in-memory",
                            "crmAdapter", CRM_PROFILE, "integrations", List.of("local-mock-crm", "deterministic-mock-ai", "bundled-knowledge"))));
            case API_PREFIX + "/demo-users" -> send(exchange, 200, "application/json; charset=utf-8",
                    DemoUserDirectory.publicUsersJson());
            case API_PREFIX + "/session" -> serveSession(exchange);
            case API_PREFIX + "/customers" -> serveCustomerSearch(exchange);
            default -> send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
        }
    }

    private static void serveCustomerSearch(HttpExchange exchange) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }

        String query = queryParameter(exchange.getRequestURI().getRawQuery(), "q");
        if (query.length() > 100) {
            send(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":\"invalid_query\",\"message\":\"Search text must be 100 characters or fewer.\"}");
            return;
        }

        List<Map<String, Object>> customers = CRM.searchCustomers(query, user.customerScope()).stream()
                .map(CrmJson::customerSummary)
                .toList();
        send(exchange, 200, "application/json; charset=utf-8",
                CrmJson.stringify(Map.of("customers", customers, "total", customers.size())));
    }

    private static void serveCustomer360(HttpExchange exchange, String rawCustomerId) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }

        String customerId = URLDecoder.decode(rawCustomerId, StandardCharsets.UTF_8);
        var customer = CRM.getCustomer360(customerId, user.customerScope());
        if (customer.isEmpty()) {
            // Return the same response for unknown and out-of-scope IDs to avoid leaking record existence.
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"customer_not_found\"}");
            return;
        }
        Customer360 base = customer.get();
        Customer360 customer360 = new Customer360(base.customer(), base.contacts(), base.products(),
                INTERACTIONS.list(customerId, user.customerScope()), CASES.list(user.customerScope(), customerId, null));
        send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.customer360(customer360)));
    }

    private static void handleCaseRequest(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if (path.equals(API_PREFIX + "/cases")) {
            if ("GET".equals(method)) {
                listCases(exchange);
            } else if ("POST".equals(method)) {
                createCase(exchange);
            } else {
                methodNotAllowed(exchange, "GET, POST");
            }
            return;
        }

        String remainder = path.substring((API_PREFIX + "/cases/").length());
        String[] segments = remainder.split("/", -1);
        String reference = URLDecoder.decode(segments[0], StandardCharsets.UTF_8);
        if (segments.length == 1 && "GET".equals(method)) {
            getCase(exchange, reference);
        } else if (segments.length == 2 && "POST".equals(method)) {
            switch (segments[1]) {
                case "status" -> updateCaseStatus(exchange, reference);
                case "assignment" -> assignCase(exchange, reference);
                case "escalate" -> escalateCase(exchange, reference);
                default -> send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            }
        } else {
            methodNotAllowed(exchange, "GET, POST");
        }
    }

    private static void listCases(HttpExchange exchange) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        String customerId = queryParameter(exchange.getRequestURI().getRawQuery(), "customerId");
        String status = queryParameter(exchange.getRequestURI().getRawQuery(), "status");
        if (customerId.length() > 100 || (!status.isBlank() && !"ALL".equalsIgnoreCase(status)
                && !java.util.Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED")
                .contains(status.strip().toUpperCase(Locale.ROOT)))) {
            badRequest(exchange, "Invalid customerId or case status filter.");
            return;
        }
        List<Map<String, Object>> cases = CASES.list(user.customerScope(), customerId, status).stream()
                .map(CrmJson::caseSummary).toList();
        send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(Map.of("cases", cases, "total", cases.size())));
    }

    private static void getCase(HttpExchange exchange, String reference) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        var serviceCase = CASES.get(reference, user.customerScope());
        if (serviceCase.isEmpty()) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"case_not_found\"}");
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.serviceCase(serviceCase.get())));
    }

    private static void createCase(HttpExchange exchange) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!canHandleCases(user)) {
            forbidden(exchange);
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) return;
        String customerId = form.getOrDefault("customerId", "").strip();
        if (CRM.getCustomer360(customerId, user.customerScope()).isEmpty()) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"customer_not_found\"}");
            return;
        }
        try {
            var serviceCase = CASES.create(user.id(), customerId, form.get("category"), form.get("priority"),
                    form.get("subject"), form.get("description"));
            send(exchange, 201, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.serviceCase(serviceCase)));
        } catch (IllegalArgumentException exception) {
            badRequest(exchange, exception.getMessage());
        }
    }

    private static void updateCaseStatus(HttpExchange exchange, String reference) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!canHandleCases(user)) {
            forbidden(exchange);
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) return;
        try {
            var serviceCase = CASES.updateStatus(user.id(), reference, form.get("status"), form.get("resolution"), user.customerScope());
            send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.serviceCase(serviceCase)));
        } catch (CaseService.CaseNotFoundException exception) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"case_not_found\"}");
        } catch (IllegalArgumentException exception) {
            badRequest(exchange, exception.getMessage());
        }
    }

    private static void assignCase(HttpExchange exchange, String reference) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!isSupervisor(user)) {
            forbidden(exchange);
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) return;
        try {
            var serviceCase = CASES.assign(user.id(), reference, form.get("assigneeId"), user.customerScope());
            send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.serviceCase(serviceCase)));
        } catch (CaseService.CaseNotFoundException exception) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"case_not_found\"}");
        } catch (IllegalArgumentException exception) {
            badRequest(exchange, exception.getMessage());
        }
    }

    private static void escalateCase(HttpExchange exchange, String reference) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!isSupervisor(user)) {
            forbidden(exchange);
            return;
        }
        try {
            var serviceCase = CASES.escalate(user.id(), reference, user.customerScope());
            send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.serviceCase(serviceCase)));
        } catch (CaseService.CaseNotFoundException exception) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"case_not_found\"}");
        } catch (IllegalArgumentException exception) {
            badRequest(exchange, exception.getMessage());
        }
    }

    private static void handleInteractionRequest(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            listInteractions(exchange);
        } else if ("POST".equals(exchange.getRequestMethod())) {
            createInteraction(exchange);
        } else {
            methodNotAllowed(exchange, "GET, POST");
        }
    }

    private static void listInteractions(HttpExchange exchange) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        String customerId = queryParameter(exchange.getRequestURI().getRawQuery(), "customerId");
        if (!customerId.isBlank() && CRM.getCustomer360(customerId, user.customerScope()).isEmpty()) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"customer_not_found\"}");
            return;
        }
        List<Map<String, Object>> interactions = INTERACTIONS.list(customerId.isBlank() ? null : customerId,
                user.customerScope()).stream().map(CrmJson::interaction).toList();
        send(exchange, 200, "application/json; charset=utf-8",
                CrmJson.stringify(Map.of("interactions", interactions, "total", interactions.size())));
    }

    private static void createInteraction(HttpExchange exchange) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!canHandleCases(user)) {
            forbidden(exchange);
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) return;
        String customerId = form.getOrDefault("customerId", "").strip();
        if (CRM.getCustomer360(customerId, user.customerScope()).isEmpty()) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"customer_not_found\"}");
            return;
        }
        try {
            var interaction = INTERACTIONS.create(user.id(), customerId, form.get("channel"), form.get("category"),
                    form.get("summary"), form.get("outcome"), form.get("relatedCaseReference"), CASES, user.customerScope());
            send(exchange, 201, "application/json; charset=utf-8", CrmJson.stringify(CrmJson.interaction(interaction)));
        } catch (CaseService.CaseNotFoundException exception) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"case_not_found\"}");
        } catch (IllegalArgumentException exception) {
            badRequest(exchange, exception.getMessage());
        }
    }

    private static void handleAuditRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!isSupervisor(user)) {
            forbidden(exchange);
            return;
        }
        List<Map<String, Object>> events = AUDIT.events().stream().map(CrmJson::auditEvent).toList();
        send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(Map.of("events", events)));
    }

    private static void resetDemoState(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        if (!isSupervisor(user)) {
            forbidden(exchange);
            return;
        }
        CASES.reset();
        INTERACTIONS.reset();
        TOOLS.clearPendingConfirmations();
        AUDIT.reset();
        AUDIT.record(user.id(), "DEMO_STATE_RESET", "in-memory-demo", "SUCCESS");
        send(exchange, 200, "application/json; charset=utf-8",
                CrmJson.stringify(Map.of("status", "RESET", "message", "Mutable demo state and audit history were restored to seeded values.")));
    }

    private static void handleAssistantRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) return;
        String message = form.getOrDefault("message", "").strip();
        String customerId = form.getOrDefault("customerId", "").strip();
        if (message.isBlank() || message.length() > 2000) {
            badRequest(exchange, "Message is required and must be 2,000 characters or fewer.");
            return;
        }

        Customer360 customerContext = null;
        if (!customerId.isBlank()) {
            var base = CRM.getCustomer360(customerId, user.customerScope());
            if (base.isEmpty()) {
                send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"customer_not_found\"}");
                return;
            }
            Customer360 customer = base.get();
            customerContext = new Customer360(customer.customer(), customer.contacts(), customer.products(),
                    INTERACTIONS.list(customerId, user.customerScope()), CASES.list(user.customerScope(), customerId, null));
        }

        if (isCaseCreationRequest(message)) {
            if (customerContext == null) {
                badRequest(exchange, "Select an in-scope customer before asking the assistant to prepare a case.");
                return;
            }
            prepareAssistantCase(exchange, user, message, customerContext);
            return;
        }
        AssistantResponse response = ASSISTANT.respond(message, customerContext);
        send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(response.asJsonValue()));
    }

    private static void prepareAssistantCase(HttpExchange exchange, DemoUser user, String message,
                                             Customer360 customerContext) throws IOException {
        long startedAt = System.nanoTime();
        long nlpStarted = System.nanoTime();
        com.pswied.core.ai.NlpResult analysis = ASSISTANT.analyze(message);
        long nlpMillis = elapsedMillis(nlpStarted);
        String category = analysis.suggestedCaseCategory();
        String priority = isHighPriorityRequest(message) ? "HIGH" : analysis.suggestedPriority();
            Map<String, String> arguments = Map.of(
                    "customerId", customerContext.customer().id(),
                    "category", category,
                    "priority", priority,
                    "subject", caseSubject(category),
                    "description", message);
        try {
            // Discovery is performed against the in-process MCP-compatible registry before invocation.
            long discoveryStarted = System.nanoTime();
            List<Map<String, Object>> definitions = TOOLS.discover();
            if (definitions.stream().noneMatch(definition -> "create_service_case".equals(definition.get("name")))) {
                throw new ToolRegistry.ToolInvocationException(404, "tool_not_found", "Case creation tool is unavailable.");
            }
            AUDIT.record(user.id(), "MCP_TOOLS_DISCOVERED", "create_service_case", "SUCCESS");
            long discoveryMillis = elapsedMillis(discoveryStarted);
            var proposal = TOOLS.invoke(new ToolUser(user.id(), user.role(), user.customerScope()),
                    "create_service_case", arguments);
            List<AssistantResponse.TraceStep> trace = List.of(
                    new AssistantResponse.TraceStep("INPUT_RECEIVED", "COMPLETED", 0, "Case creation request received."),
                    new AssistantResponse.TraceStep("NLP_ANALYSIS", "COMPLETED", nlpMillis,
                            "Suggested category " + category + " and priority " + priority + "."),
                    new AssistantResponse.TraceStep("CRM_CONTEXT", "COMPLETED", 0,
                            "Loaded authorized Customer 360 for " + customerContext.customer().id() + "."),
                    new AssistantResponse.TraceStep("CRM_ADAPTER", "LOCAL_MOCK", 0,
                            "CRM data served through the configured " + CRM_PROFILE + "-style in-memory adapter."),
                    new AssistantResponse.TraceStep("RAG_RETRIEVAL", "SKIPPED", 0, "Case creation is a CRM action, not a knowledge question."),
                    new AssistantResponse.TraceStep("MCP_TOOL_DISCOVERY", "COMPLETED", discoveryMillis,
                            "Discovered create_service_case in the local tool registry."),
                    new AssistantResponse.TraceStep("MCP_WRITE_PREVIEW", "AWAITING_CONFIRMATION", 0,
                            "Prepared the exact case proposal; no case was created."),
                    new AssistantResponse.TraceStep("FINAL_RESPONSE", "AWAITING_CONFIRMATION", 0,
                            "Returned the proposal for explicit user review."));
            AssistantResponse response = new AssistantResponse(message,
                    "I prepared a service case for your review. No case has been created yet; confirm the exact preview below to continue.",
                    "DETERMINISTIC_MOCK_ORCHESTRATION", customerContext.customer().id(), analysis,
                    false, false, List.of(), trace, elapsedMillis(startedAt));
            Map<String, Object> responseBody = new LinkedHashMap<>(response.asJsonValue());
            responseBody.put("toolName", "create_service_case");
            responseBody.put("toolInvocation", proposal.payload());
            send(exchange, 200, "application/json; charset=utf-8", CrmJson.stringify(responseBody));
        } catch (ToolRegistry.ToolInvocationException exception) {
            send(exchange, exception.statusCode(), "application/json; charset=utf-8",
                    CrmJson.stringify(Map.of("error", exception.code(), "message", exception.getMessage())));
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static boolean isCaseCreationRequest(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.startsWith("please ")) text = text.substring("please ".length()).stripLeading();
        if (text.startsWith("tolong ")) text = text.substring("tolong ".length()).stripLeading();
        boolean imperative = text.startsWith("create ") || text.startsWith("open ") || text.startsWith("log ")
                || text.startsWith("raise ") || text.startsWith("buat ") || text.startsWith("buka ") || text.startsWith("catat ");
        return imperative && (text.contains("case") || text.contains("complaint") || text.contains("ticket")
                || text.contains("kasus") || text.contains("tiket"));
    }

    private static boolean isHighPriorityRequest(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        return text.contains("high-priority") || text.contains("high priority") || text.contains("urgent")
                || text.contains("prioritas tinggi") || text.contains("prioritas mendesak");
    }

    private static String caseSubject(String category) {
        return switch (category) {
            case "TRANSACTION_DISPUTE" -> "Transaction dispute reported to assistant";
            case "PAYMENT_COMPLAINT" -> "Payment complaint reported to assistant";
            case "LOAN_INQUIRY" -> "Loan inquiry reported to assistant";
            case "ACCOUNT_INQUIRY" -> "Account inquiry reported to assistant";
            case "CARD_ISSUE" -> "Card issue reported to assistant";
            case "MOBILE_BANKING_ISSUE" -> "Mobile banking issue reported to assistant";
            default -> "Customer service request from assistant";
        };
    }

    private static void handleKnowledgeRequest(HttpExchange exchange, String path) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        if (requestUser(exchange) == null) {
            unauthorized(exchange);
            return;
        }
        if ((API_PREFIX + "/knowledge/documents").equals(path)) {
            List<Map<String, Object>> documents = KNOWLEDGE.documents().stream()
                    .map(document -> document.summaryJson(KNOWLEDGE.chunkCount(document.id())))
                    .toList();
            send(exchange, 200, "application/json; charset=utf-8",
                    CrmJson.stringify(Map.of("documents", documents, "total", documents.size(), "mode", "synthetic-mock")));
            return;
        }
        String query = queryParameter(exchange.getRequestURI().getRawQuery(), "q");
        if (query.length() > 300) {
            badRequest(exchange, "Knowledge query must be 300 characters or fewer.");
            return;
        }
        List<Map<String, Object>> results = KNOWLEDGE.search(query, 5).stream()
                .map(KnowledgeService.RetrievedChunk::asJsonValue).toList();
        send(exchange, 200, "application/json; charset=utf-8",
                CrmJson.stringify(Map.of("query", query, "results", results, "total", results.size(), "mode", "synthetic-mock")));
    }

    private static void handleToolDiscovery(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        if (requestUser(exchange) == null) {
            unauthorized(exchange);
            return;
        }
        List<Map<String, Object>> tools = TOOLS.discover();
        AUDIT.record(requestUser(exchange).id(), "MCP_TOOLS_DISCOVERED", "tool-registry", "SUCCESS");
        send(exchange, 200, "application/json; charset=utf-8",
                CrmJson.stringify(Map.of("protocol", "MCP-compatible", "transport", "in-process",
                        "tools", tools, "total", tools.size())));
    }

    private static void handleToolInvocation(HttpExchange exchange, String encodedToolName) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        Map<String, String> arguments = readForm(exchange);
        if (arguments == null) return;
        String toolName = URLDecoder.decode(encodedToolName, StandardCharsets.UTF_8);
        try {
            var result = TOOLS.invoke(new ToolUser(user.id(), user.role(), user.customerScope()), toolName, arguments);
            send(exchange, result.statusCode(), "application/json; charset=utf-8", CrmJson.stringify(result.payload()));
        } catch (ToolRegistry.ToolInvocationException exception) {
            send(exchange, exception.statusCode(), "application/json; charset=utf-8",
                    CrmJson.stringify(Map.of("error", exception.code(), "message", exception.getMessage())));
        }
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            badRequest(exchange, "Content-Type must be application/x-www-form-urlencoded.");
            return null;
        }
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readNBytes(8193);
        }
        if (body.length > 8192) {
            send(exchange, 413, "application/json; charset=utf-8", "{\"error\":\"request_too_large\"}");
            return null;
        }
        Map<String, String> form = new LinkedHashMap<>();
        String encoded = new String(body, StandardCharsets.UTF_8);
        if (!encoded.isBlank()) {
            for (String pair : encoded.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length == 1 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                form.put(key, value);
            }
        }
        return form;
    }

    private static boolean canHandleCases(DemoUser user) {
        return isSupervisor(user) || "CUSTOMER_SERVICE_OFFICER".equals(user.role());
    }

    private static boolean isSupervisor(DemoUser user) {
        return "CRM_SUPERVISOR".equals(user.role()) || "CRM_ADMINISTRATOR".equals(user.role());
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"method_not_allowed\"}");
    }

    private static void forbidden(HttpExchange exchange) throws IOException {
        send(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"forbidden\"}");
    }

    private static void badRequest(HttpExchange exchange, String message) throws IOException {
        send(exchange, 400, "application/json; charset=utf-8",
                CrmJson.stringify(Map.of("error", "invalid_request", "message", message == null ? "Invalid request." : message)));
    }

    private static DemoUser requestUser(HttpExchange exchange) {
        return DemoUserDirectory.find(exchange.getRequestHeaders().getFirst("X-Demo-User"));
    }

    private static void unauthorized(HttpExchange exchange) throws IOException {
        send(exchange, 401, "application/json; charset=utf-8",
                "{\"error\":\"demo_user_required\",\"message\":\"Select a seeded demo user using X-Demo-User.\"}");
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return parts.length == 1 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static void serveSession(HttpExchange exchange) throws IOException {
        DemoUser user = requestUser(exchange);
        if (user == null) {
            unauthorized(exchange);
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", user.sessionJson());
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        try (var input = RelasiAiApplication.class.getResourceAsStream("/static/index.html")) {
            if (input == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "UI resource not found");
                return;
            }
            send(exchange, 200, "text/html; charset=utf-8", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

record DemoUser(String id, String displayName, String role, List<String> customerScope) {
    String publicJson() {
        return "{\"id\":" + quote(id) + ",\"displayName\":" + quote(displayName)
                + ",\"role\":" + quote(role) + "}";
    }

    String sessionJson() {
        String customers = customerScope.stream().map(DemoUser::quote).collect(java.util.stream.Collectors.joining(","));
        return "{\"user\":" + publicJson() + ",\"customerScope\":[" + customers + "],\"authentication\":\"local-demo\"}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

final class DemoUserDirectory {
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("service-officer", "Service Officer", "CUSTOMER_SERVICE_OFFICER", List.of("CUST-1001", "CUST-1002")),
            new DemoUser("relationship-manager", "Relationship Manager", "RELATIONSHIP_MANAGER", List.of("CUST-1003", "CUST-1004")),
            new DemoUser("supervisor", "CRM Supervisor", "CRM_SUPERVISOR", List.of("CUST-1001", "CUST-1002", "CUST-1003", "CUST-1004")),
            new DemoUser("administrator", "CRM Administrator", "CRM_ADMINISTRATOR", List.of("CUST-1001", "CUST-1002", "CUST-1003", "CUST-1004"))
    );

    private DemoUserDirectory() {
    }

    static DemoUser find(String id) {
        if (id == null) {
            return null;
        }
        return USERS.stream().filter(user -> user.id().equals(id)).findFirst().orElse(null);
    }

    static String publicUsersJson() {
        String users = USERS.stream().map(DemoUser::publicJson).collect(java.util.stream.Collectors.joining(","));
        return "{\"users\":[" + users + "]}";
    }
}
