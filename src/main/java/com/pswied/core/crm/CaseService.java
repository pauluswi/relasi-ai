package com.pswied.core.crm;

import com.pswied.core.crm.CrmModels.CaseEvent;
import com.pswied.core.crm.CrmModels.ServiceCase;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Mutable, in-memory service-case use cases for the synthetic showcase. */
public final class CaseService {
    private static final Set<String> CATEGORIES = Set.of(
            "PAYMENT_COMPLAINT", "ACCOUNT_INQUIRY", "LOAN_INQUIRY", "CARD_ISSUE",
            "MOBILE_BANKING_ISSUE", "TRANSACTION_DISPUTE", "GENERAL_BANKING_INQUIRY");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "NORMAL", "LOW");
    private static final Set<String> ACTIVE_STATUSES = Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER");

    private final Map<String, ServiceCase> cases = new ConcurrentHashMap<>();
    private final AtomicInteger nextNumber = new AtomicInteger(61);
    private final AuditService audit;

    public CaseService(AuditService audit) {
        this.audit = audit;
        seedCases();
    }

    public synchronized void reset() {
        cases.clear();
        nextNumber.set(61);
        seedCases();
    }

    public List<ServiceCase> list(List<String> permittedCustomerIds, String customerId, String status) {
        String normalizedStatus = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? null : status.strip().toUpperCase(Locale.ROOT);
        return cases.values().stream()
                .filter(serviceCase -> permittedCustomerIds.contains(serviceCase.customerId()))
                .filter(serviceCase -> customerId == null || customerId.isBlank() || customerId.equals(serviceCase.customerId()))
                .filter(serviceCase -> normalizedStatus == null || normalizedStatus.equals(serviceCase.status()))
                .sorted(Comparator.comparing(ServiceCase::createdAt).reversed())
                .toList();
    }

    public Optional<ServiceCase> get(String reference, List<String> permittedCustomerIds) {
        ServiceCase serviceCase = cases.get(reference);
        return serviceCase != null && permittedCustomerIds.contains(serviceCase.customerId())
                ? Optional.of(serviceCase) : Optional.empty();
    }

    public synchronized ServiceCase create(String actorId, String customerId, String category,
                                           String priority, String subject, String description) {
        String normalizedCategory = normalize(category);
        String normalizedPriority = normalize(priority);
        require(CATEGORIES.contains(normalizedCategory), "Unsupported case category.");
        require(PRIORITIES.contains(normalizedPriority), "Priority must be HIGH, NORMAL, or LOW.");
        require(subject != null && !subject.isBlank() && subject.strip().length() <= 120,
                "Subject is required and must be 120 characters or fewer.");
        require(description != null && !description.isBlank() && description.strip().length() <= 2000,
                "Description is required and must be 2,000 characters or fewer.");

        Instant now = Instant.now();
        String reference = "CASE-2026-%04d".formatted(nextNumber.getAndIncrement());
        String created = now.toString();
        String deadline = now.plus(slaFor(normalizedPriority)).toString();
        ServiceCase serviceCase = new ServiceCase(reference, customerId, normalizedCategory, subject.strip(),
                description.strip(), "OPEN", normalizedPriority, actorId, created, created, deadline,
                null, false, List.of(new CaseEvent(created, actorId, "CREATED",
                "Case opened with " + normalizedPriority + " priority.")));
        cases.put(reference, serviceCase);
        audit.record(actorId, "CASE_CREATED", reference + "/" + customerId, "SUCCESS");
        return serviceCase;
    }

    public synchronized ServiceCase updateStatus(String actorId, String reference, String status, String resolution,
                                                 List<String> permittedCustomerIds) {
        ServiceCase current = validateStatusChange(reference, status, resolution, permittedCustomerIds);
        String nextStatus = normalize(status);
        String normalizedResolution = resolution == null ? null : resolution.strip();

        String timestamp = Instant.now().toString();
        String details = "Status changed from " + current.status() + " to " + nextStatus + ".";
        ServiceCase updated = copy(current, nextStatus, current.priority(), current.assignedTo(), timestamp,
                "RESOLVED".equals(nextStatus) ? normalizedResolution : current.resolution(), current.escalated(),
                append(current.history(), new CaseEvent(timestamp, actorId, "STATUS_CHANGED", details)));
        cases.put(reference, updated);
        audit.record(actorId, "CASE_STATUS_CHANGED", reference, "SUCCESS:" + nextStatus);
        return updated;
    }

    public synchronized ServiceCase validateStatusChange(String reference, String status, String resolution,
                                                         List<String> permittedCustomerIds) {
        ServiceCase current = requireCase(reference, permittedCustomerIds);
        String nextStatus = normalize(status);
        require(Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED").contains(nextStatus),
                "Unsupported case status.");
        require(allowedTransition(current.status(), nextStatus),
                "Case cannot transition from " + current.status() + " to " + nextStatus + ".");
        String normalizedResolution = resolution == null ? null : resolution.strip();
        require(!"RESOLVED".equals(nextStatus) || (normalizedResolution != null && !normalizedResolution.isBlank()),
                "A resolution note is required when resolving a case.");
        require(normalizedResolution == null || normalizedResolution.length() <= 2000,
                "Resolution note must be 2,000 characters or fewer.");
        return current;
    }

    public synchronized ServiceCase assign(String actorId, String reference, String assigneeId,
                                           List<String> permittedCustomerIds) {
        ServiceCase current = validateAssignment(reference, assigneeId, permittedCustomerIds);
        String timestamp = Instant.now().toString();
        ServiceCase updated = copy(current, current.status(), current.priority(), assigneeId, timestamp,
                current.resolution(), current.escalated(),
                append(current.history(), new CaseEvent(timestamp, actorId, "ASSIGNED", "Assigned to " + assigneeId + ".")));
        cases.put(reference, updated);
        audit.record(actorId, "CASE_ASSIGNED", reference + "/" + assigneeId, "SUCCESS");
        return updated;
    }

    public synchronized ServiceCase validateAssignment(String reference, String assigneeId,
                                                       List<String> permittedCustomerIds) {
        ServiceCase current = requireCase(reference, permittedCustomerIds);
        require(ACTIVE_STATUSES.contains(current.status()), "Only active cases can be assigned.");
        require("service-officer".equals(assigneeId), "Assignee must be a seeded customer service officer.");
        return current;
    }

    public synchronized ServiceCase escalate(String actorId, String reference, List<String> permittedCustomerIds) {
        ServiceCase current = requireCase(reference, permittedCustomerIds);
        require(ACTIVE_STATUSES.contains(current.status()), "Only active cases can be escalated.");
        require(!current.escalated(), "Case is already escalated.");
        require(Instant.parse(current.slaDeadline()).isBefore(Instant.now()), "Case SLA has not expired.");
        String timestamp = Instant.now().toString();
        ServiceCase updated = copy(current, current.status(), "HIGH", current.assignedTo(), timestamp,
                current.resolution(), true,
                append(current.history(), new CaseEvent(timestamp, actorId, "ESCALATED", "SLA expired; priority raised to HIGH.")));
        cases.put(reference, updated);
        audit.record(actorId, "CASE_ESCALATED", reference, "SUCCESS");
        return updated;
    }

    private ServiceCase requireCase(String reference, List<String> permittedCustomerIds) {
        ServiceCase serviceCase = cases.get(reference);
        if (serviceCase == null || !permittedCustomerIds.contains(serviceCase.customerId())) {
            throw new CaseNotFoundException();
        }
        return serviceCase;
    }

    private static boolean allowedTransition(String current, String next) {
        return switch (current) {
            case "OPEN" -> Set.of("IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED").contains(next);
            case "IN_PROGRESS" -> Set.of("WAITING_FOR_CUSTOMER", "RESOLVED").contains(next);
            case "WAITING_FOR_CUSTOMER" -> Set.of("IN_PROGRESS", "RESOLVED").contains(next);
            case "RESOLVED" -> Set.of("CLOSED", "IN_PROGRESS").contains(next);
            default -> false;
        };
    }

    private static Duration slaFor(String priority) {
        return switch (priority) {
            case "HIGH" -> Duration.ofHours(4);
            case "LOW" -> Duration.ofHours(72);
            default -> Duration.ofHours(24);
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static List<CaseEvent> append(List<CaseEvent> history, CaseEvent event) {
        List<CaseEvent> updated = new ArrayList<>(history);
        updated.add(event);
        return List.copyOf(updated);
    }

    private static ServiceCase copy(ServiceCase source, String status, String priority, String assignedTo,
                                    String updatedAt, String resolution, boolean escalated, List<CaseEvent> history) {
        return new ServiceCase(source.reference(), source.customerId(), source.category(), source.subject(),
                source.description(), status, priority, assignedTo, source.createdAt(), updatedAt,
                source.slaDeadline(), resolution, escalated, history);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private void seedCases() {
        String officer = "service-officer";
        String created = "2026-11-20T09:00:00Z";
        String resolved = "2026-11-22T04:45:00Z";
        cases.put("CASE-2026-0042", new ServiceCase("CASE-2026-0042", "CUST-1002", "ACCOUNT_INQUIRY",
                "October statement request", "Customer requested an account statement for October.", "RESOLVED",
                "NORMAL", officer, created, resolved, "2026-11-21T09:00:00Z", "Statement sent to the registered email.",
                false, List.of(new CaseEvent(created, officer, "CREATED", "Case opened."),
                new CaseEvent(resolved, officer, "STATUS_CHANGED", "Resolved after statement was sent."))));

        String mobileCreated = "2026-11-23T01:40:00Z";
        String mobileUpdated = "2026-11-23T01:52:00Z";
        cases.put("CASE-2026-0058", new ServiceCase("CASE-2026-0058", "CUST-1004", "MOBILE_BANKING_ISSUE",
                "Unable to sign in to mobile banking", "Customer reported an unsuccessful mobile banking sign-in.",
                "IN_PROGRESS", "HIGH", officer, mobileCreated, mobileUpdated, "2026-11-23T05:40:00Z", null,
                false, List.of(new CaseEvent(mobileCreated, officer, "CREATED", "Case opened from web chat."),
                new CaseEvent(mobileUpdated, officer, "STATUS_CHANGED", "Investigation started."))));

        String paymentCreated = "2026-11-24T02:10:00Z";
        String paymentUpdated = "2026-11-24T03:00:00Z";
        cases.put("CASE-2026-0059", new ServiceCase("CASE-2026-0059", "CUST-1001", "PAYMENT_COMPLAINT",
                "BI-FAST transfer not received", "Customer reported a transfer that the recipient has not received.", "OPEN",
                "HIGH", officer, paymentCreated, paymentUpdated, "2026-11-24T06:10:00Z", null, false,
                List.of(new CaseEvent(paymentCreated, officer, "CREATED", "Payment complaint recorded."),
                        new CaseEvent(paymentUpdated, officer, "STATUS_CHANGED", "Awaiting transaction review."))));

        String disputeCreated = "2026-11-24T05:20:00Z";
        cases.put("CASE-2026-0060", new ServiceCase("CASE-2026-0060", "CUST-1003", "TRANSACTION_DISPUTE",
                "Business transfer under investigation", "Customer requested an update on a business transfer dispute.", "IN_PROGRESS",
                "NORMAL", officer, disputeCreated, disputeCreated, "2026-11-25T05:20:00Z", null, false,
                List.of(new CaseEvent(disputeCreated, officer, "CREATED", "Transfer dispute recorded for investigation."))));
    }

    public static final class CaseNotFoundException extends RuntimeException {
    }
}
