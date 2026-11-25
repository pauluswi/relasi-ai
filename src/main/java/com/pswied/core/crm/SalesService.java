package com.pswied.core.crm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Deterministic, in-memory sales and campaign fixtures for the optional showcase slice. */
public final class SalesService {
    private static final Set<String> STAGES = Set.of("DISCOVERY", "QUALIFICATION", "PROPOSAL", "CLOSED_WON", "CLOSED_LOST");
    private final List<Opportunity> opportunities = new ArrayList<>();
    private final AtomicInteger nextNumber = new AtomicInteger(1005);
    private final List<Campaign> campaigns = List.of(
            new Campaign("CMP-1001", "Everyday banking check-in", "CUSTOMER_RETENTION", Set.of("CUST-1001", "CUST-1003")),
            new Campaign("CMP-1002", "Digital banking adoption", "DIGITAL_ENGAGEMENT", Set.of("CUST-1002", "CUST-1004")));
    private final List<CampaignResponse> responses = List.of(
            new CampaignResponse("CMP-1001", "CUST-1001", "Requested a relationship review", "EMAIL", "RESPONDED"),
            new CampaignResponse("CMP-1002", "CUST-1004", "Opened the digital banking guide", "MOBILE_BANKING", "ENGAGED"),
            new CampaignResponse("CMP-1001", "CUST-1003", "Asked for a product consultation", "TELEPHONE", "RESPONDED"));
    private final AuditService audit;

    public SalesService(AuditService audit) {
        this.audit = audit;
        reset();
    }

    public synchronized void reset() {
        opportunities.clear();
        opportunities.add(new Opportunity("OPP-1001", "CUST-1001", "Priority relationship review", "QUALIFICATION", 180_000_000, "IDR", "relationship-manager"));
        opportunities.add(new Opportunity("OPP-1002", "CUST-1002", "Home financing discussion", "PROPOSAL", 650_000_000, "IDR", "relationship-manager"));
        opportunities.add(new Opportunity("OPP-1003", "CUST-1003", "Business banking review", "DISCOVERY", 240_000_000, "IDR", "relationship-manager"));
        opportunities.add(new Opportunity("OPP-1004", "CUST-1004", "Card upgrade follow-up", "CLOSED_WON", 36_000_000, "IDR", "relationship-manager"));
        nextNumber.set(1005);
    }

    public synchronized Map<String, Object> dashboard(CrmAdapter crm, List<String> permittedCustomerIds) {
        var customers = crm.searchCustomers("", permittedCustomerIds).stream()
                .collect(Collectors.toMap(CrmModels.CustomerSummary::id, CrmModels.CustomerSummary::fullName));
        List<Map<String, Object>> scopedOpportunities = opportunities.stream()
                .filter(item -> permittedCustomerIds.contains(item.customerId()))
                .map(item -> Map.<String, Object>of(
                        "id", item.id(), "customerId", item.customerId(), "customerName", customers.get(item.customerId()),
                        "name", item.name(), "stage", item.stage(), "amount", item.amount(), "currency", item.currency(), "owner", item.owner()))
                .toList();
        List<Map<String, Object>> scopedCampaigns = campaigns.stream().map(campaign -> {
            Set<String> invitedCustomerIds = campaign.customerIds().stream()
                    .filter(permittedCustomerIds::contains).collect(Collectors.toSet());
            long responseCount = responses.stream().filter(response -> response.campaignId().equals(campaign.id()))
                    .filter(response -> invitedCustomerIds.contains(response.customerId())).count();
            return Map.<String, Object>of("id", campaign.id(), "name", campaign.name(), "type", campaign.type(),
                    "invited", invitedCustomerIds.size(), "responses", responseCount,
                    "engagementRate", invitedCustomerIds.isEmpty() ? 0 : Math.round(responseCount * 100.0 / invitedCustomerIds.size()));
        }).filter(campaign -> (int) campaign.get("invited") > 0).toList();
        List<Map<String, Object>> scopedResponses = responses.stream()
                .filter(item -> permittedCustomerIds.contains(item.customerId()))
                .map(item -> Map.<String, Object>of("campaignId", item.campaignId(), "customerId", item.customerId(),
                        "customerName", customers.get(item.customerId()), "summary", item.summary(),
                        "channel", item.channel(), "status", item.status()))
                .toList();
        return Map.of("opportunities", scopedOpportunities, "campaigns", scopedCampaigns, "responses", scopedResponses);
    }

    public synchronized Opportunity create(String actorId, String customerId, String name, long amount,
                                           List<String> permittedCustomerIds) {
        if (!permittedCustomerIds.contains(customerId)) throw new OpportunityNotFoundException();
        String normalizedName = name == null ? "" : name.strip();
        require(!normalizedName.isEmpty() && normalizedName.length() <= 120,
                "Opportunity name is required and must be 120 characters or fewer.");
        require(amount > 0 && amount <= 10_000_000_000_000L,
                "Opportunity amount must be greater than zero and no more than IDR 10 trillion.");
        String id = "OPP-%04d".formatted(nextNumber.getAndIncrement());
        Opportunity opportunity = new Opportunity(id, customerId, normalizedName, "DISCOVERY", amount, "IDR", actorId);
        opportunities.add(opportunity);
        audit.record(actorId, "OPPORTUNITY_CREATED", id + "/" + customerId, "SUCCESS");
        return opportunity;
    }

    public synchronized Opportunity updateStage(String actorId, String id, String stage,
                                                List<String> permittedCustomerIds) {
        Opportunity current = opportunities.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(OpportunityNotFoundException::new);
        if (!permittedCustomerIds.contains(current.customerId())) throw new OpportunityNotFoundException();
        String normalizedStage = stage == null ? "" : stage.strip().toUpperCase();
        require(STAGES.contains(normalizedStage), "Choose a supported opportunity stage.");
        Opportunity updated = new Opportunity(current.id(), current.customerId(), current.name(), normalizedStage,
                current.amount(), current.currency(), current.owner());
        opportunities.set(opportunities.indexOf(current), updated);
        audit.record(actorId, "OPPORTUNITY_STAGE_CHANGED", id, "SUCCESS:" + normalizedStage);
        return updated;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public record Opportunity(String id, String customerId, String name, String stage, long amount,
                              String currency, String owner) {
    }

    public static final class OpportunityNotFoundException extends RuntimeException {
    }

    private record Campaign(String id, String name, String type, Set<String> customerIds) {
    }

    private record CampaignResponse(String campaignId, String customerId, String summary,
                                    String channel, String status) {
    }
}
