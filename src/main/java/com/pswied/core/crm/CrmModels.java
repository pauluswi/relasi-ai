package com.pswied.core.crm;

import java.util.List;

public final class CrmModels {
    private CrmModels() {
    }

    public record Customer(
            String id,
            String customerNumber,
            String fullName,
            String dateOfBirth,
            String segment,
            String status,
            String email,
            String mobile,
            String city,
            String preferredChannel,
            String riskIndicator) {
    }

    public record CustomerSummary(
            String id,
            String customerNumber,
            String fullName,
            String segment,
            String status,
            String city) {
    }

    public record Contact(
            String id,
            String fullName,
            String relationship,
            String email,
            String mobile,
            String preferredChannel) {
    }

    public record ProductRelationship(
            String id,
            String productType,
            String displayName,
            String status,
            String maskedReference,
            String currency,
            long availableBalance) {
    }

    public record Interaction(
            String id,
            String channel,
            String occurredAt,
            String summary,
            String outcome,
            String relatedCaseReference) {
    }

    public record ServiceCase(
            String reference,
            String category,
            String subject,
            String status,
            String priority,
            String updatedAt) {
    }

    public record Customer360(
            Customer customer,
            List<Contact> contacts,
            List<ProductRelationship> products,
            List<Interaction> interactions,
            List<ServiceCase> cases) {
        public Customer360 {
            contacts = List.copyOf(contacts);
            products = List.copyOf(products);
            interactions = List.copyOf(interactions);
            cases = List.copyOf(cases);
        }
    }
}
