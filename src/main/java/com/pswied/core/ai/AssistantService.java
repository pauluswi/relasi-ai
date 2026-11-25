package com.pswied.core.ai;

import com.pswied.core.ai.AssistantResponse.TraceStep;
import com.pswied.core.crm.CrmModels.Customer360;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates deterministic NLP, optional authorized CRM context, and the mock LLM. */
public final class AssistantService {
    private final NlpEngine nlpEngine;
    private final LlmProvider llmProvider;

    public AssistantService(NlpEngine nlpEngine, LlmProvider llmProvider) {
        this.nlpEngine = nlpEngine;
        this.llmProvider = llmProvider;
    }

    public AssistantResponse respond(String message, Customer360 customerContext) {
        long startedAt = System.nanoTime();
        List<TraceStep> trace = new ArrayList<>();
        trace.add(new TraceStep("INPUT_RECEIVED", "COMPLETED", 0, "Natural-language request received."));

        long nlpStarted = System.nanoTime();
        NlpResult analysis = nlpEngine.analyze(message);
        long nlpDuration = elapsedMillis(nlpStarted);
        trace.add(new TraceStep("NLP_ANALYSIS", "COMPLETED", nlpDuration,
                "Intent " + analysis.intent() + "; sentiment " + analysis.sentiment() + "; " + analysis.entities().size() + " entities."));

        if (customerContext != null) {
            trace.add(new TraceStep("CRM_CONTEXT", "COMPLETED", 0,
                    "Loaded authorized Customer 360 for " + customerContext.customer().id() + " with "
                            + customerContext.products().size() + " products, " + customerContext.interactions().size()
                            + " interactions, and " + customerContext.cases().size() + " cases."));
        } else {
            trace.add(new TraceStep("CRM_CONTEXT", "SKIPPED", 0, "No customer context was selected."));
        }

        long llmStarted = System.nanoTime();
        String answer = llmProvider.generate(message, analysis, customerContext);
        long llmDuration = elapsedMillis(llmStarted);
        trace.add(new TraceStep("MOCK_LLM", "COMPLETED", llmDuration, "Generated deterministic mock response."));

        return new AssistantResponse(message, answer, llmProvider.providerId(),
                customerContext == null ? null : customerContext.customer().id(), analysis, trace, elapsedMillis(startedAt));
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
