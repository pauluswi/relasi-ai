package com.pswied.core.crm;

import com.pswied.core.crm.CrmModels.Contact;
import com.pswied.core.crm.CrmModels.Customer;
import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.CrmModels.CustomerSummary;
import com.pswied.core.crm.CrmModels.Interaction;
import com.pswied.core.crm.CrmModels.ProductRelationship;
import com.pswied.core.crm.CrmModels.ServiceCase;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Deterministic in-memory adapter using a Dynamics-style CRM boundary. */
public final class DynamicsStyleMockCrmAdapter implements CrmAdapter {
    private final Map<String, Customer360> customers = Map.of(
            "CUST-1001", customer360(
                    new Customer("CUST-1001", "REL-240001", "Alya Pratama", "1991-04-12", "PERSONAL", "ACTIVE",
                            "alya.pratama@example.test", "+62 811-0000-1001", "Jakarta", "MOBILE_BANKING", "LOW"),
                    List.of(new Contact("CON-1001", "Alya Pratama", "SELF", "alya.pratama@example.test",
                            "+62 811-0000-1001", "MOBILE_BANKING")),
                    List.of(
                            new ProductRelationship("PRD-1001", "SAVINGS", "Relasi Savings", "ACTIVE", "•••• 1042", "IDR", 245_500_000),
                            new ProductRelationship("PRD-1002", "DEBIT_CARD", "Relasi Debit", "ACTIVE", "•••• 1042", "IDR", 0)),
                    List.of(),
                    List.of()),
            "CUST-1002", customer360(
                    new Customer("CUST-1002", "REL-240002", "Bima Santoso", "1985-09-03", "SMALL_BUSINESS", "ACTIVE",
                            "bima.santoso@example.test", "+62 812-0000-1002", "Bandung", "EMAIL", "MEDIUM"),
                    List.of(new Contact("CON-1002", "Bima Santoso", "OWNER", "bima.santoso@example.test",
                            "+62 812-0000-1002", "EMAIL"),
                            new Contact("CON-1003", "Nadia Santoso", "AUTHORIZED_CONTACT", "nadia.santoso@example.test",
                                    "+62 812-0000-1003", "TELEPHONE")),
                    List.of(
                            new ProductRelationship("PRD-1003", "BUSINESS_ACCOUNT", "Relasi Business Current", "ACTIVE", "•••• 7721", "IDR", 1_250_000_000),
                            new ProductRelationship("PRD-1004", "WORKING_CAPITAL_LOAN", "Micro Working Capital", "ACTIVE", "LOAN-•••-2880", "IDR", 0)),
                    List.of(),
                    List.of()),
            "CUST-1003", customer360(
                    new Customer("CUST-1003", "REL-240003", "Citra Wibowo", "1978-01-26", "PRIORITY", "ACTIVE",
                            "citra.wibowo@example.test", "+62 813-0000-1003", "Surabaya", "TELEPHONE", "LOW"),
                    List.of(new Contact("CON-1004", "Citra Wibowo", "SELF", "citra.wibowo@example.test",
                            "+62 813-0000-1003", "TELEPHONE")),
                    List.of(
                            new ProductRelationship("PRD-1005", "SAVINGS", "Relasi Priority Savings", "ACTIVE", "•••• 4108", "IDR", 7_850_000_000L),
                            new ProductRelationship("PRD-1006", "CREDIT_CARD", "Relasi Platinum Card", "ACTIVE", "•••• 9033", "IDR", 0),
                            new ProductRelationship("PRD-1007", "TIME_DEPOSIT", "Flexible Deposit", "ACTIVE", "TD-•••-4410", "IDR", 2_000_000_000)),
                    List.of(),
                    List.of()),
            "CUST-1004", customer360(
                    new Customer("CUST-1004", "REL-240004", "Dimas Saputra", "1994-07-08", "PERSONAL", "ACTIVE",
                            "dimas.saputra@example.test", "+62 821-0000-1004", "Yogyakarta", "WEB_CHAT", "MEDIUM"),
                    List.of(new Contact("CON-1005", "Dimas Saputra", "SELF", "dimas.saputra@example.test",
                            "+62 821-0000-1004", "WEB_CHAT")),
                    List.of(new ProductRelationship("PRD-1008", "SAVINGS", "Relasi Digital Savings", "ACTIVE", "•••• 6659", "IDR", 35_750_000)),
                    List.of(),
                    List.of())
    );

    @Override
    public List<CustomerSummary> searchCustomers(String query, List<String> permittedCustomerIds) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return customers.values().stream()
                .map(Customer360::customer)
                .filter(customer -> permittedCustomerIds.contains(customer.id()))
                .filter(customer -> normalized.isEmpty()
                        || customer.fullName().toLowerCase(Locale.ROOT).contains(normalized)
                        || customer.id().toLowerCase(Locale.ROOT).contains(normalized)
                        || customer.customerNumber().toLowerCase(Locale.ROOT).contains(normalized)
                        || customer.segment().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(java.util.Comparator.comparing(Customer::fullName))
                .map(customer -> new CustomerSummary(customer.id(), customer.customerNumber(), customer.fullName(),
                        customer.segment(), customer.status(), customer.city()))
                .toList();
    }

    @Override
    public Optional<Customer360> getCustomer360(String customerId, List<String> permittedCustomerIds) {
        if (!permittedCustomerIds.contains(customerId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(customers.get(customerId));
    }

    private static Customer360 customer360(Customer customer, List<Contact> contacts,
                                           List<ProductRelationship> products, List<Interaction> interactions,
                                           List<ServiceCase> cases) {
        return new Customer360(customer, contacts, products, interactions, cases);
    }
}
