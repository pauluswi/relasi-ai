package com.pswied.core.crm;

import com.pswied.core.crm.CrmModels.Customer;
import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.CrmModels.CustomerSummary;

import java.util.List;
import java.util.Optional;

/** Provider-neutral read contract for the initial CRM showcase slice. */
public interface CrmAdapter {
    List<CustomerSummary> searchCustomers(String query, List<String> permittedCustomerIds);

    Optional<Customer360> getCustomer360(String customerId, List<String> permittedCustomerIds);
}
