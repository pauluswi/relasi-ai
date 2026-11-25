package com.pswied.core.crm;

import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.CrmModels.CustomerSummary;

import java.util.List;
import java.util.Optional;

/**
 * Salesforce-style in-memory adapter profile. It maps the same canonical showcase fixtures
 * through the provider-neutral contract so switching profiles cannot change demo outcomes.
 */
public final class SalesforceStyleMockCrmAdapter implements CrmAdapter {
    private final CrmAdapter fixtures = new DynamicsStyleMockCrmAdapter();

    @Override
    public List<CustomerSummary> searchCustomers(String query, List<String> permittedCustomerIds) {
        return fixtures.searchCustomers(query, permittedCustomerIds);
    }

    @Override
    public Optional<Customer360> getCustomer360(String customerId, List<String> permittedCustomerIds) {
        return fixtures.getCustomer360(customerId, permittedCustomerIds);
    }
}
