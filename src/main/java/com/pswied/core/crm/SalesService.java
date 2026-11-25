package com.pswied.core.crm;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic, in-memory sales and campaign fixtures for the optional showcase slice. */
public final class SalesService {
    private final List<Opportunity> opportunities = List.of(
            new Opportunity("OPP-1001", "CUST-1001", "Priority relationship review", "QUALIFICATION", 180_000_000, "IDR", "relationship-manager"),
            new Opportunity("OPP-1002", "CUST-1002", "Home financing discussion", "PROPOSAL", 650_000_000, "IDR", "relationship-manager"),
            new Opportunity("OPP-1003", "CUST-1003", "Business banking review", "DISCOVERY", 240_000_000, "IDR", "relationship-manager"),
            new Opportunity("OPP-1004", "CUST-1004", "Card upgrade follow-up", "CLOSED_WON", 36_000_000, "IDR", "relationship-manager"));

    private final List<Campaign> campaigns = List.of(
            new Campaign("CMP-1001", "Everyday banking check-in", "CUSTOMER_RETENTION", Set.of("CUST-1001", "CUST-1003")),
            new Campaign("CMP-1002", "Digital banking adoption", "DIGITAL_ENGAGEMENT", Set.of("CUST-1002", "CUST-1004")));

    private final List<CampaignResponse> responses = List.of(
            new CampaignResponse("CMP-1001", "CUST-1001", "Requested a relationship review", "EMAIL", "RESPONDED"),
            new CampaignResponse("CMP-1002", "CUST-1004", "Opened the digital banking guide", "MOBILE_BANKING", "ENGAGED"),
            new CampaignResponse("CMP-1001", "CUST-1003", "Asked for a product consultation", "TELEPHONE", "RESPONDED"));

    public Map<String, Object> dashboard(CrmAdapter crm, List<String> permittedCustomerIds) {
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

    private record Opportunity(String id, String customerId, String name, String stage, long amount,
                               String currency, String owner) {
    }

    private record Campaign(String id, String name, String type, Set<String> customerIds) {
    }

    private record CampaignResponse(String campaignId, String customerId, String summary,
                                    String channel, String status) {
    }
}
