package com.pswied.core.crm;

import com.pswied.core.crm.CrmModels.Interaction;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory interaction history and recording use cases. */
public final class InteractionService {
    private static final Set<String> CHANNELS = Set.of("WEB_CHAT", "EMAIL", "TELEPHONE", "BRANCH", "MOBILE_BANKING");
    private static final Set<String> CATEGORIES = Set.of(
            "PAYMENT", "ACCOUNT", "LOAN", "CARD", "MOBILE_BANKING", "TRANSACTION", "GENERAL");

    private final Map<String, Interaction> interactions = new ConcurrentHashMap<>();
    private final AtomicInteger nextNumber = new AtomicInteger(1007);
    private final AuditService audit;

    public InteractionService(AuditService audit) {
        this.audit = audit;
        seedInteractions();
    }

    public List<Interaction> list(String customerId, List<String> permittedCustomerIds) {
        if (customerId != null && !permittedCustomerIds.contains(customerId)) {
            return List.of();
        }
        return interactions.values().stream()
                .filter(interaction -> customerId == null || interaction.customerId().equals(customerId))
                .filter(interaction -> permittedCustomerIds.contains(interaction.customerId()))
                .sorted(Comparator.comparing(Interaction::occurredAt).reversed())
                .toList();
    }

    public synchronized Interaction create(String actorId, String customerId, String channel,
                                           String category, String summary, String outcome,
                                           String relatedCaseReference, CaseService caseService,
                                           List<String> permittedCustomerIds) {
        String normalizedChannel = normalize(channel);
        String normalizedCategory = normalize(category);
        require(CHANNELS.contains(normalizedChannel), "Unsupported interaction channel.");
        require(CATEGORIES.contains(normalizedCategory), "Unsupported interaction category.");
        require(summary != null && !summary.isBlank() && summary.strip().length() <= 2000,
                "Interaction content is required and must be 2,000 characters or fewer.");
        require(outcome != null && !outcome.isBlank() && outcome.strip().length() <= 500,
                "Interaction outcome is required and must be 500 characters or fewer.");

        String caseReference = relatedCaseReference == null || relatedCaseReference.isBlank()
                ? null : relatedCaseReference.strip();
        if (caseReference != null) {
            var relatedCase = caseService.get(caseReference, permittedCustomerIds)
                    .orElseThrow(CaseService.CaseNotFoundException::new);
            require(customerId.equals(relatedCase.customerId()), "Related case must belong to the selected customer.");
        }

        String id = "INT-%04d".formatted(nextNumber.getAndIncrement());
        Interaction interaction = new Interaction(id, customerId, normalizedChannel, normalizedCategory,
                Instant.now().toString(), summary.strip(), outcome.strip(), caseReference);
        interactions.put(id, interaction);
        audit.record(actorId, "INTERACTION_RECORDED", id + "/" + customerId, "SUCCESS");
        return interaction;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private void seedInteractions() {
        addSeed("INT-1001", "CUST-1001", "MOBILE_BANKING", "ACCOUNT", "2026-11-23T02:15:00Z",
                "Asked about daily transfer limits", "Information provided", null);
        addSeed("INT-1002", "CUST-1001", "TELEPHONE", "CARD", "2026-11-19T07:30:00Z",
                "Confirmed debit card delivery address", "Address confirmed", null);
        addSeed("INT-1003", "CUST-1002", "EMAIL", "ACCOUNT", "2026-11-22T04:10:00Z",
                "Requested account statement for October", "Statement sent", "CASE-2026-0042");
        addSeed("INT-1004", "CUST-1003", "BRANCH", "ACCOUNT", "2026-11-21T03:00:00Z",
                "Discussed renewal of time deposit", "Follow-up scheduled", null);
        addSeed("INT-1005", "CUST-1003", "TELEPHONE", "GENERAL", "2026-11-20T09:20:00Z",
                "Requested callback from relationship manager", "Callback scheduled", null);
        addSeed("INT-1006", "CUST-1004", "WEB_CHAT", "MOBILE_BANKING", "2026-11-23T01:40:00Z",
                "Reported a mobile banking login issue", "Case opened", "CASE-2026-0058");
    }

    private void addSeed(String id, String customerId, String channel, String category, String occurredAt,
                         String summary, String outcome, String relatedCaseReference) {
        interactions.put(id, new Interaction(id, customerId, channel, category, occurredAt,
                summary, outcome, relatedCaseReference));
    }
}
