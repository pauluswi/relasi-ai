package com.pswied.core.crm;

import java.util.ArrayList;
import java.util.HashSet;
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
    private final List<Campaign> campaigns = new ArrayList<>();
    private final List<CampaignResponse> responses = new ArrayList<>();
    private final AtomicInteger nextResponseNumber = new AtomicInteger(1004);
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
        campaigns.clear();
        Set<String> allCustomers = Set.of("CUST-1001", "CUST-1002", "CUST-1003", "CUST-1004");
        campaigns.add(new Campaign("CMP-1001", "Everyday banking check-in", "CUSTOMER_RETENTION", allCustomers,
                new HashSet<>(Set.of("CUST-1001", "CUST-1003"))));
        campaigns.add(new Campaign("CMP-1002", "Digital banking adoption", "DIGITAL_ENGAGEMENT", allCustomers,
                new HashSet<>(Set.of("CUST-1002", "CUST-1004"))));
        responses.clear();
        responses.add(new CampaignResponse("RSP-1001", "CMP-1001", "CUST-1001", "Requested a relationship review", "EMAIL", "RESPONDED"));
        responses.add(new CampaignResponse("RSP-1002", "CMP-1002", "CUST-1004", "Opened the digital banking guide", "MOBILE_BANKING", "ENGAGED"));
        responses.add(new CampaignResponse("RSP-1003", "CMP-1001", "CUST-1003", "Asked for a product consultation", "TELEPHONE", "RESPONDED"));
        nextResponseNumber.set(1004);
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
            Set<String> eligibleCustomerIds = campaign.eligibleCustomerIds().stream()
                    .filter(permittedCustomerIds::contains).collect(Collectors.toSet());
            Set<String> invitedCustomerIds = campaign.customerIds().stream()
                    .filter(permittedCustomerIds::contains).collect(Collectors.toSet());
            long responseCount = responses.stream().filter(response -> response.campaignId().equals(campaign.id()))
                    .filter(response -> invitedCustomerIds.contains(response.customerId())).count();
            List<Map<String, Object>> enrollments = invitedCustomerIds.stream().sorted()
                    .map(customerId -> {
                        CampaignResponse response = responseFor(campaign.id(), customerId);
                        Map<String, Object> enrollment = new java.util.LinkedHashMap<>();
                        enrollment.put("customerId", customerId);
                        enrollment.put("customerName", customers.get(customerId));
                        enrollment.put("response", response == null ? null : Map.of(
                                "id", response.id(), "summary", response.summary(), "channel", response.channel(), "status", response.status()));
                        return enrollment;
                    }).toList();
            return Map.<String, Object>of("id", campaign.id(), "name", campaign.name(), "type", campaign.type(),
                    "eligible", eligibleCustomerIds.size(), "invited", invitedCustomerIds.size(), "responses", responseCount,
                    "engagementRate", invitedCustomerIds.isEmpty() ? 0 : Math.round(responseCount * 100.0 / invitedCustomerIds.size()),
                    "availableCustomerIds", eligibleCustomerIds.stream().filter(id -> !invitedCustomerIds.contains(id)).sorted().toList(),
                    "enrollments", enrollments);
        }).filter(campaign -> (int) campaign.get("eligible") > 0).toList();
        List<Map<String, Object>> scopedResponses = responses.stream()
                .filter(item -> permittedCustomerIds.contains(item.customerId()))
                .map(item -> Map.<String, Object>of("id", item.id(), "campaignId", item.campaignId(), "customerId", item.customerId(),
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

    public synchronized void enroll(String actorId, String campaignId, String customerId,
                                    List<String> permittedCustomerIds) {
        if (!permittedCustomerIds.contains(customerId)) throw new CampaignNotFoundException();
        Campaign campaign = campaign(campaignId);
        if (!campaign.eligibleCustomerIds().contains(customerId)) throw new CampaignNotFoundException();
        require(campaign.customerIds().add(customerId), "Customer is already enrolled in this campaign.");
        audit.record(actorId, "CAMPAIGN_CUSTOMER_ENROLLED", campaignId + "/" + customerId, "SUCCESS");
    }

    public synchronized CampaignResponse recordResponse(String actorId, String campaignId, String customerId,
                                                        String summary, String channel, String status,
                                                        List<String> permittedCustomerIds) {
        if (!permittedCustomerIds.contains(customerId)) throw new CampaignNotFoundException();
        Campaign campaign = campaign(campaignId);
        require(campaign.customerIds().contains(customerId), "Enroll the customer in this campaign before recording a response.");
        String normalizedSummary = summary == null ? "" : summary.strip();
        String normalizedChannel = channel == null ? "" : channel.strip().toUpperCase();
        String normalizedStatus = status == null ? "" : status.strip().toUpperCase();
        require(!normalizedSummary.isEmpty() && normalizedSummary.length() <= 500,
                "Response summary is required and must be 500 characters or fewer.");
        require(Set.of("EMAIL", "TELEPHONE", "BRANCH", "WEB_CHAT", "MOBILE_BANKING").contains(normalizedChannel),
                "Choose a supported response channel.");
        require(Set.of("ENGAGED", "RESPONDED", "DECLINED", "FOLLOW_UP").contains(normalizedStatus),
                "Choose a supported response status.");
        CampaignResponse current = responseFor(campaignId, customerId);
        CampaignResponse next = new CampaignResponse(current == null ? "RSP-%04d".formatted(nextResponseNumber.getAndIncrement()) : current.id(),
                campaignId, customerId, normalizedSummary, normalizedChannel, normalizedStatus);
        responses.removeIf(item -> item.campaignId().equals(campaignId) && item.customerId().equals(customerId));
        responses.add(next);
        audit.record(actorId, "CAMPAIGN_RESPONSE_RECORDED", campaignId + "/" + customerId, "SUCCESS:" + normalizedStatus);
        return next;
    }

    private Campaign campaign(String id) {
        return campaigns.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(CampaignNotFoundException::new);
    }

    private CampaignResponse responseFor(String campaignId, String customerId) {
        return responses.stream().filter(item -> item.campaignId().equals(campaignId) && item.customerId().equals(customerId))
                .findFirst().orElse(null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public record Opportunity(String id, String customerId, String name, String stage, long amount,
                              String currency, String owner) {
    }

    public static final class OpportunityNotFoundException extends RuntimeException {
    }

    public record CampaignResponse(String id, String campaignId, String customerId, String summary,
                                   String channel, String status) {
    }

    public static final class CampaignNotFoundException extends RuntimeException {
    }

    private record Campaign(String id, String name, String type, Set<String> eligibleCustomerIds,
                            Set<String> customerIds) {
    }
}
