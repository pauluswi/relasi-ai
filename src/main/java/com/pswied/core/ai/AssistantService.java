package com.pswied.core.ai;

import com.pswied.core.ai.AssistantResponse.TraceStep;
import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.knowledge.KnowledgeService;
import com.pswied.core.knowledge.KnowledgeService.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates deterministic NLP, optional authorized CRM context, and the mock LLM. */
public final class AssistantService {
    private final NlpEngine nlpEngine;
    private final LlmProvider llmProvider;
    private final KnowledgeService knowledgeService;
    private final String crmProfile;

    public AssistantService(NlpEngine nlpEngine, LlmProvider llmProvider, KnowledgeService knowledgeService, String crmProfile) {
        this.nlpEngine = nlpEngine;
        this.llmProvider = llmProvider;
        this.knowledgeService = knowledgeService;
        this.crmProfile = crmProfile;
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
        trace.add(new TraceStep("CRM_ADAPTER", "LOCAL_MOCK", 0,
                "CRM data served through the configured " + crmProfile + "-style in-memory adapter."));

        boolean knowledgeQuestion = isKnowledgeQuestion(message, analysis);
        List<RetrievedChunk> sources = List.of();
        boolean grounded = false;
        boolean abstained = false;
        String answer;
        if (knowledgeQuestion) {
            long retrievalStarted = System.nanoTime();
            sources = knowledgeService.search(message, 3);
            long retrievalDuration = elapsedMillis(retrievalStarted);
            String refs = sources.stream().map(RetrievedChunk::reference).collect(java.util.stream.Collectors.joining(", "));
            trace.add(new TraceStep("RAG_RETRIEVAL", sources.isEmpty() ? "NO_EVIDENCE" : "COMPLETED",
                    retrievalDuration, sources.isEmpty() ? "No reliable supporting source met the relevance threshold."
                    : "Retrieved " + sources.size() + " source chunks: " + refs));

            if (sources.isEmpty()) {
                abstained = true;
                answer = "I couldn't find reliable supporting information in the bundled synthetic knowledge sources, so I can't answer this policy or procedure question. Try a more specific question or consult an authorized banking source.";
                trace.add(new TraceStep("CONTEXT_ASSEMBLY", "SKIPPED", 0, "No supporting evidence was available."));
                trace.add(new TraceStep("MOCK_LLM", "SKIPPED", 0, "Generation skipped to avoid an unsupported answer."));
            } else {
                grounded = true;
                trace.add(new TraceStep("CONTEXT_ASSEMBLY", "COMPLETED", 0,
                        "Assembled bounded context from " + sources.size() + " retrieved chunks."));
                long llmStarted = System.nanoTime();
                answer = llmProvider.generate(message, analysis, customerContext, sources);
                trace.add(new TraceStep("MOCK_LLM", "COMPLETED", elapsedMillis(llmStarted),
                        "Generated deterministic response from retrieved evidence."));
            }
        } else {
            trace.add(new TraceStep("RAG_RETRIEVAL", "SKIPPED", 0, "Request handled as a CRM or service-assistance task."));
            long llmStarted = System.nanoTime();
            answer = llmProvider.generate(message, analysis, customerContext, List.of());
            trace.add(new TraceStep("MOCK_LLM", "COMPLETED", elapsedMillis(llmStarted), "Generated deterministic mock response."));
        }

        trace.add(new TraceStep("MCP_TOOL_DISCOVERY", "SKIPPED", 0, "No MCP tool was needed for this request."));
        trace.add(new TraceStep("MCP_TOOL_CALL", "SKIPPED", 0, "No MCP tool was invoked."));
        trace.add(new TraceStep("FINAL_RESPONSE", abstained ? "ABSTAINED" : "COMPLETED", 0,
                abstained ? "Returned an abstention because no reliable source was found." : "Prepared the response for the authorized user."));

        return new AssistantResponse(message, answer, llmProvider.providerId(),
                customerContext == null ? null : customerContext.customer().id(), analysis, grounded, abstained,
                sources, trace, elapsedMillis(startedAt));
    }

    public NlpResult analyze(String message) {
        return nlpEngine.analyze(message);
    }

    private static boolean isKnowledgeQuestion(String message, NlpResult analysis) {
        String text = message.toLowerCase(java.util.Locale.ROOT);
        if ("TRANSACTION_COMPLAINT".equals(analysis.intent())) return false;
        if (contains(text, "summarize this customer", "customer summary", "relationship with the bank",
                "customer 360", "ringkas nasabah", "rangkuman nasabah", "hubungan nasabah",
                "case status", "service case status", "status kasus", "status pengaduan")) return false;
        return contains(text, "procedure", "policy", "handling", "guideline", "guidelines", "steps to", "process for",
                "how do i", "how to", "what is the process", "what is the policy", "what is the procedure",
                "prosedur", "kebijakan", "panduan", "cara menangani", "bagaimana menangani")
                || text.startsWith("what is ") || text.startsWith("what are ") || text.startsWith("can you explain ");
    }

    private static boolean contains(String text, String... phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
