package com.pswied.core.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssistantResponse(
        String input,
        String answer,
        String provider,
        String customerId,
        NlpResult nlp,
        List<TraceStep> trace,
        long durationMs) {

    public AssistantResponse {
        trace = List.copyOf(trace);
    }

    public Map<String, Object> asJsonValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        result.put("answer", answer);
        result.put("provider", provider);
        result.put("customerId", customerId);
        result.put("nlp", Map.of(
                "intent", nlp.intent(),
                "confidence", nlp.confidence(),
                "sentiment", nlp.sentiment(),
                "language", nlp.language(),
                "suggestedCaseCategory", nlp.suggestedCaseCategory(),
                "suggestedPriority", nlp.suggestedPriority(),
                "entities", nlp.entities().stream().map(entity -> Map.of("type", entity.type(), "value", entity.value())).toList()));
        result.put("trace", trace.stream().map(TraceStep::asJsonValue).toList());
        result.put("durationMs", durationMs);
        return result;
    }

    public record TraceStep(String stage, String status, long durationMs, String detail) {
        Map<String, Object> asJsonValue() {
            return Map.of("stage", stage, "status", status, "durationMs", durationMs, "detail", detail);
        }
    }
}
