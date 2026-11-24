package com.pswied.core.crm;

import com.pswied.core.crm.CrmModels.Contact;
import com.pswied.core.crm.CrmModels.Customer;
import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.CrmModels.CustomerSummary;
import com.pswied.core.crm.CrmModels.Interaction;
import com.pswied.core.crm.CrmModels.ProductRelationship;
import com.pswied.core.crm.CrmModels.ServiceCase;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CrmJson {
    private CrmJson() {
    }

    public static Map<String, Object> customer(Customer customer) {
        return object(
                "id", customer.id(),
                "customerNumber", customer.customerNumber(),
                "fullName", customer.fullName(),
                "dateOfBirth", customer.dateOfBirth(),
                "segment", customer.segment(),
                "status", customer.status(),
                "email", customer.email(),
                "mobile", customer.mobile(),
                "city", customer.city(),
                "preferredChannel", customer.preferredChannel(),
                "riskIndicator", customer.riskIndicator());
    }

    public static Map<String, Object> customerSummary(CustomerSummary customer) {
        return object("id", customer.id(), "customerNumber", customer.customerNumber(), "fullName", customer.fullName(),
                "segment", customer.segment(), "status", customer.status(), "city", customer.city());
    }

    public static Map<String, Object> customer360(Customer360 customer360) {
        return object(
                "customer", customer(customer360.customer()),
                "contacts", customer360.contacts().stream().map(CrmJson::contact).toList(),
                "products", customer360.products().stream().map(CrmJson::product).toList(),
                "interactions", customer360.interactions().stream().map(CrmJson::interaction).toList(),
                "cases", customer360.cases().stream().map(CrmJson::serviceCase).toList());
    }

    public static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return "{" + map.entrySet().stream()
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + stringify(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",")) + "}";
        }
        if (value instanceof Iterable<?> values) {
            return "[" + java.util.stream.StreamSupport.stream(values.spliterator(), false)
                    .map(CrmJson::stringify)
                    .collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static Map<String, Object> contact(Contact contact) {
        return object("id", contact.id(), "fullName", contact.fullName(), "relationship", contact.relationship(),
                "email", contact.email(), "mobile", contact.mobile(), "preferredChannel", contact.preferredChannel());
    }

    private static Map<String, Object> product(ProductRelationship product) {
        return object("id", product.id(), "productType", product.productType(), "displayName", product.displayName(),
                "status", product.status(), "maskedReference", product.maskedReference(), "currency", product.currency(),
                "availableBalance", product.availableBalance());
    }

    private static Map<String, Object> interaction(Interaction interaction) {
        return object("id", interaction.id(), "channel", interaction.channel(), "occurredAt", interaction.occurredAt(),
                "summary", interaction.summary(), "outcome", interaction.outcome(),
                "relatedCaseReference", interaction.relatedCaseReference());
    }

    private static Map<String, Object> serviceCase(ServiceCase serviceCase) {
        return object("reference", serviceCase.reference(), "category", serviceCase.category(),
                "subject", serviceCase.subject(), "status", serviceCase.status(), "priority", serviceCase.priority(),
                "updatedAt", serviceCase.updatedAt());
    }

    private static Map<String, Object> object(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object fields must be key/value pairs.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
