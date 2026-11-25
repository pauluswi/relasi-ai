package com.pswied.core.ai;

import java.util.List;

public record NlpResult(
        String intent,
        double confidence,
        String sentiment,
        String language,
        String suggestedCaseCategory,
        String suggestedPriority,
        List<Entity> entities) {

    public NlpResult {
        entities = List.copyOf(entities);
    }

    public record Entity(String type, String value) {
    }
}
