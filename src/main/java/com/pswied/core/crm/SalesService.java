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
    private static final Set<String> CAMPAIGN_TYPES = Set.of("CUSTOMER_RETENTION", "DIGITAL_ENGAGEMENT", "PRODUCT_AWARENESS", "SERVICE_FOLLOW_UP");
    private static final Set<String> CAMPAIGN_STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED", "COMPLETED");
    private final List<Opportunity> opportunities = new ArrayList<>();
    private final AtomicInteger nextNumber = new AtomicInteger(1005);
    private final List<Lead> leads = new ArrayList<>();
    private final AtomicInteger nextLeadNumber = new AtomicInteger(1003);
    private final List<Campaign> campaigns = new ArrayList<>();
    private final List<CampaignResponse> responses = new ArrayList<>();
    private final AtomicInteger nextResponseNumber = new AtomicInteger(1004);
    private final AtomicInteger nextCampaignNumber = new AtomicInteger(1003);
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
        leads.clear();
        leads.add(new Lead("LEAD-1001", "CUST-1001", "Everyday savings review", "SAVINGS", "NEW", "service-officer"));
        leads.add(new Lead("LEAD-1002", "CUST-1003", "Business cash management", "CASH_MANAGEMENT", "QUALIFIED", "relationship-manager"));
        nextLeadNumber.set(1003);
        campaigns.clear();
        Set<String> allCustomers = Set.of("CUST-1001", "CUST-1002", "CUST-1003", "CUST-1004");
        campaigns.add(new Campaign("CMP-1001", "Everyday banking check-in", "CUSTOMER_RETENTION", "ACTIVE", allCustomers,
                new HashSet<>(Set.of("CUST-1001", "CUST-1003"))));
        campaigns.add(new Campaign("CMP-1002", "Digital banking adoption", "DIGITAL_ENGAGEMENT", "ACTIVE", allCustomers,
                new HashSet<>(Set.of("CUST-1002", "CUST-1004"))));
        nextCampaignNumber.set(1003);
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
        List<Map<String, Object>> scopedLeads = leads.stream()
                .filter(item -> permittedCustomerIds.contains(item.customerId()))
                .map(item -> Map.<String, Object>of("id", item.id(), "customerId", item.customerId(),
                        "customerName", customers.get(item.customerId()), "name", item.name(),
                        "productInterest", item.productInterest(), "status", item.status(), "owner", item.owner()))
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
            Map<String, Object> campaignSummary = new java.util.LinkedHashMap<>();
            campaignSummary.put("id", campaign.id());
            campaignSummary.put("name", campaign.name());
            campaignSummary.put("type", campaign.type());
            campaignSummary.put("status", campaign.status());
            campaignSummary.put("eligible", eligibleCustomerIds.size());
            campaignSummary.put("invited", invitedCustomerIds.size());
            campaignSummary.put("responses", responseCount);
            campaignSummary.put("engagementRate", invitedCustomerIds.isEmpty() ? 0 : Math.round(responseCount * 100.0 / invitedCustomerIds.size()));
            campaignSummary.put("audienceCustomerIds", eligibleCustomerIds.stream().sorted().toList());
            campaignSummary.put("availableCustomerIds", eligibleCustomerIds.stream().filter(id -> !invitedCustomerIds.contains(id)).sorted().toList());
            campaignSummary.put("enrollments", enrollments);
            return campaignSummary;
        }).filter(campaign -> (int) campaign.get("eligible") > 0).toList();
        List<Map<String, Object>> scopedResponses = responses.stream()
                .filter(item -> permittedCustomerIds.contains(item.customerId()))
                .map(item -> Map.<String, Object>of("id", item.id(), "campaignId", item.campaignId(), "customerId", item.customerId(),
                        "customerName", customers.get(item.customerId()), "summary", item.summary(),
                        "channel", item.channel(), "status", item.status()))
                .toList();
        return Map.of("leads", scopedLeads, "opportunities", scopedOpportunities,
                "campaigns", scopedCampaigns, "responses", scopedResponses);
    }

    public synchronized Lead createLead(String actorId, String customerId, String name, String productInterest,
                                        List<String> permittedCustomerIds) {
        if (!permittedCustomerIds.contains(customerId)) throw new LeadNotFoundException();
        String normalizedName = name == null ? "" : name.strip();
        String normalizedInterest = productInterest == null ? "" : productInterest.strip();
        require(!normalizedName.isEmpty() && normalizedName.length() <= 120, "Lead name is required and must be 120 characters or fewer.");
        require(!normalizedInterest.isEmpty() && normalizedInterest.length() <= 100,
                "Product interest is required and must be 100 characters or fewer.");
        Lead lead = new Lead("LEAD-%04d".formatted(nextLeadNumber.getAndIncrement()), customerId,
                normalizedName, normalizedInterest, "NEW", actorId);
        leads.add(lead);
        audit.record(actorId, "LEAD_CREATED", lead.id() + "/" + customerId, "SUCCESS");
        return lead;
    }

    public synchronized Lead updateLeadStatus(String actorId, String id, String status,
                                              List<String> permittedCustomerIds) {
        Lead current = lead(id, permittedCustomerIds);
        String nextStatus = status == null ? "" : status.strip().toUpperCase();
        require(Set.of("QUALIFIED", "DISQUALIFIED").contains(nextStatus), "Choose Qualified or Disqualified.");
        require("NEW".equals(current.status()), "Only new leads can be qualified or disqualified.");
        Lead updated = new Lead(current.id(), current.customerId(), current.name(), current.productInterest(), nextStatus, current.owner());
        leads.set(leads.indexOf(current), updated);
        audit.record(actorId, "LEAD_STATUS_CHANGED", id, "SUCCESS:" + nextStatus);
        return updated;
    }

    public synchronized Lead updateLeadDetails(String actorId, String id, String name, String productInterest,
                                               List<String> permittedCustomerIds) {
        Lead current = lead(id, permittedCustomerIds);
        require("NEW".equals(current.status()), "Only new leads can be edited.");
        String normalizedName = name == null ? "" : name.strip();
        String normalizedInterest = productInterest == null ? "" : productInterest.strip();
        require(!normalizedName.isEmpty() && normalizedName.length() <= 120,
                "Lead name is required and must be 120 characters or fewer.");
        require(!normalizedInterest.isEmpty() && normalizedInterest.length() <= 100,
                "Product interest is required and must be 100 characters or fewer.");
        Lead updated = new Lead(current.id(), current.customerId(), normalizedName, normalizedInterest, current.status(), current.owner());
        leads.set(leads.indexOf(current), updated);
        audit.record(actorId, "LEAD_DETAILS_UPDATED", id, "SUCCESS");
        return updated;
    }

    public synchronized LeadConversion convertLead(String actorId, String id, long amount,
                                                   List<String> permittedCustomerIds) {
        Lead current = lead(id, permittedCustomerIds);
        require("QUALIFIED".equals(current.status()), "Only qualified leads can be converted.");
        Opportunity opportunity = create(actorId, current.customerId(), current.productInterest() + " opportunity",
                amount, permittedCustomerIds);
        Lead converted = new Lead(current.id(), current.customerId(), current.name(), current.productInterest(), "CONVERTED", current.owner());
        leads.set(leads.indexOf(current), converted);
        audit.record(actorId, "LEAD_CONVERTED", id + "/" + opportunity.id(), "SUCCESS");
        return new LeadConversion(converted, opportunity);
    }

    private Lead lead(String id, List<String> permittedCustomerIds) {
        Lead current = leads.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(LeadNotFoundException::new);
        if (!permittedCustomerIds.contains(current.customerId())) throw new LeadNotFoundException();
        return current;
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
        require("ACTIVE".equals(campaign.status()), "Campaign must be active before enrolling customers.");
        if (!campaign.eligibleCustomerIds().contains(customerId)) throw new CampaignNotFoundException();
        require(campaign.customerIds().add(customerId), "Customer is already enrolled in this campaign.");
        audit.record(actorId, "CAMPAIGN_CUSTOMER_ENROLLED", campaignId + "/" + customerId, "SUCCESS");
    }

    public synchronized CampaignCreated createCampaign(String actorId, String name, String type, List<String> audienceCustomerIds,
                                                        List<String> permittedCustomerIds) {
        String normalizedName = name == null ? "" : name.strip();
        String normalizedType = type == null ? "" : type.strip().toUpperCase();
        require(!normalizedName.isEmpty() && normalizedName.length() <= 100,
                "Campaign name is required and must be 100 characters or fewer.");
        require(CAMPAIGN_TYPES.contains(normalizedType), "Choose a supported campaign type.");
        Set<String> audience = audienceCustomerIds.stream().map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        require(!audience.isEmpty(), "Select at least one in-scope customer for the campaign audience.");
        require(permittedCustomerIds.containsAll(audience), "Campaign audience must stay within your customer scope.");
        String id = "CMP-%04d".formatted(nextCampaignNumber.getAndIncrement());
        Campaign campaign = new Campaign(id, normalizedName, normalizedType, "DRAFT", audience, new HashSet<>());
        campaigns.add(campaign);
        audit.record(actorId, "CAMPAIGN_CREATED", id + "/audience=" + audience.size(), "SUCCESS");
        return new CampaignCreated(campaign.id(), campaign.name(), campaign.type(), campaign.eligibleCustomerIds().size(), campaign.status());
    }

    public synchronized CampaignCreated updateCampaignStatus(String actorId, String campaignId, String status,
                                                              List<String> permittedCustomerIds) {
        Campaign current = campaign(campaignId);
        if (current.eligibleCustomerIds().stream().noneMatch(permittedCustomerIds::contains)) throw new CampaignNotFoundException();
        String nextStatus = status == null ? "" : status.strip().toUpperCase();
        require(CAMPAIGN_STATUSES.contains(nextStatus), "Choose a supported campaign status.");
        require(!current.status().equals(nextStatus), "Campaign already has that status.");
        boolean allowed = switch (current.status()) {
            case "DRAFT" -> "ACTIVE".equals(nextStatus);
            case "ACTIVE" -> Set.of("PAUSED", "COMPLETED").contains(nextStatus);
            case "PAUSED" -> Set.of("ACTIVE", "COMPLETED").contains(nextStatus);
            default -> false;
        };
        require(allowed, "Campaign status transition is not allowed.");
        Campaign updated = new Campaign(current.id(), current.name(), current.type(), nextStatus,
                current.eligibleCustomerIds(), current.customerIds());
        campaigns.set(campaigns.indexOf(current), updated);
        audit.record(actorId, "CAMPAIGN_STATUS_CHANGED", campaignId, "SUCCESS:" + nextStatus);
        return new CampaignCreated(updated.id(), updated.name(), updated.type(), updated.eligibleCustomerIds().size(), updated.status());
    }

    public synchronized CampaignCreated updateCampaignAudience(String actorId, String campaignId,
                                                                List<String> audienceCustomerIds,
                                                                List<String> permittedCustomerIds) {
        Campaign current = campaign(campaignId);
        if (current.eligibleCustomerIds().stream().noneMatch(permittedCustomerIds::contains)) throw new CampaignNotFoundException();
        require("DRAFT".equals(current.status()), "Only draft campaigns can have their audience changed.");
        require(current.customerIds().isEmpty(), "Campaign audience cannot change after enrollment.");
        Set<String> audience = audienceCustomerIds.stream().map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        require(!audience.isEmpty(), "Select at least one in-scope customer for the campaign audience.");
        require(permittedCustomerIds.containsAll(audience), "Campaign audience must stay within your customer scope.");
        Campaign updated = new Campaign(current.id(), current.name(), current.type(), current.status(), audience, current.customerIds());
        campaigns.set(campaigns.indexOf(current), updated);
        audit.record(actorId, "CAMPAIGN_AUDIENCE_UPDATED", campaignId + "/audience=" + audience.size(), "SUCCESS");
        return new CampaignCreated(updated.id(), updated.name(), updated.type(), updated.eligibleCustomerIds().size(), updated.status());
    }

    public synchronized CampaignResponse recordResponse(String actorId, String campaignId, String customerId,
                                                        String summary, String channel, String status,
                                                        List<String> permittedCustomerIds) {
        if (!permittedCustomerIds.contains(customerId)) throw new CampaignNotFoundException();
        Campaign campaign = campaign(campaignId);
        require(Set.of("ACTIVE", "PAUSED").contains(campaign.status()), "Responses can only be recorded for active or paused campaigns.");
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

    public record Lead(String id, String customerId, String name, String productInterest,
                       String status, String owner) {
    }

    public record LeadConversion(Lead lead, Opportunity opportunity) {
    }

    public static final class LeadNotFoundException extends RuntimeException {
    }

    public static final class OpportunityNotFoundException extends RuntimeException {
    }

    public record CampaignResponse(String id, String campaignId, String customerId, String summary,
                                   String channel, String status) {
    }

    public record CampaignCreated(String id, String name, String type, int audienceSize, String status) {
    }

    public static final class CampaignNotFoundException extends RuntimeException {
    }

    private record Campaign(String id, String name, String type, String status, Set<String> eligibleCustomerIds,
                            Set<String> customerIds) {
    }
}
