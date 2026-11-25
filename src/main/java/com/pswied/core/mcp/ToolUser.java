package com.pswied.core.mcp;

import java.util.List;

public record ToolUser(String id, String role, List<String> customerScope) {
    public ToolUser {
        customerScope = List.copyOf(customerScope);
    }

    public boolean canAccess(String customerId) {
        return customerScope.contains(customerId);
    }
}
