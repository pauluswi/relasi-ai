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
        String provider = llmProvider.providerId();
        if (isNextBestActionQuestion(message)) {
            provider = "DETERMINISTIC_NBA_RULES";
            if (customerContext == null) {
                answer = "Select an in-scope customer first so I can review their profile, products, cases, and recent interactions.";
                trace.add(new TraceStep("NEXT_BEST_ACTION_CONTEXT", "MISSING_CUSTOMER", 0,
                        "Customer-specific evidence is required before making an advisory recommendation."));
                trace.add(new TraceStep("RAG_RETRIEVAL", "SKIPPED", 0, "No customer context is available for product guidance retrieval."));
                trace.add(new TraceStep("CONTEXT_ASSEMBLY", "SKIPPED", 0, "No customer evidence is available."));
                trace.add(new TraceStep("MOCK_LLM", "SKIPPED", 0, "Recommendations are produced by deterministic advisory rules."));
            } else {
                long retrievalStarted = System.nanoTime();
                String productTerms = customerContext.products().stream().map(product -> product.productType().replace('_', ' '))
                        .collect(java.util.stream.Collectors.joining(" "));
                sources = knowledgeService.search("customer product relationship review " + productTerms, 3);
                grounded = !sources.isEmpty();
                trace.add(new TraceStep("NEXT_BEST_ACTION_CONTEXT", "COMPLETED", 0,
                        "Reviewed customer segment " + customerContext.customer().segment() + ", "
                                + customerContext.products().size() + " products, " + customerContext.interactions().size()
                                + " interactions, and " + customerContext.cases().size() + " cases."));
                String refs = sources.stream().map(RetrievedChunk::reference).collect(java.util.stream.Collectors.joining(", "));
                trace.add(new TraceStep("RAG_RETRIEVAL", grounded ? "COMPLETED" : "NO_EVIDENCE",
                        elapsedMillis(retrievalStarted), grounded ? "Retrieved synthetic product guidance: " + refs
                                : "No bundled product guidance matched the customer's product relationships."));
                trace.add(new TraceStep("CONTEXT_ASSEMBLY", "COMPLETED", 0,
                        "Assembled authorized profile, product, interaction, case, and retrieved guidance evidence."));
                trace.add(new TraceStep("MOCK_LLM", "SKIPPED", 0,
                        "Recommendations are generated with deterministic rules and remain advisory."));
                answer = nextBestActionAnswer(customerContext, sources);
            }
        } else if (knowledgeQuestion) {
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

        return new AssistantResponse(message, answer, provider,
                customerContext == null ? null : customerContext.customer().id(), analysis, grounded, abstained,
                sources, trace, elapsedMillis(startedAt));
    }

    public NlpResult analyze(String message) {
        return nlpEngine.analyze(message);
    }

    private static boolean isNextBestActionQuestion(String message) {
        String text = message.toLowerCase(java.util.Locale.ROOT);
        return contains(text, "what should i do next", "what should i do for this customer", "next best action",
                "what is the next best action", "apa yang harus saya lakukan selanjutnya", "apa langkah berikutnya untuk nasabah");
    }

    private static String nextBestActionAnswer(Customer360 customer, List<RetrievedChunk> sources) {
        StringBuilder answer = new StringBuilder("Advisory next steps for ")
                .append(customer.customer().fullName()).append(" (segment: ")
                .append(customer.customer().segment().replace('_', ' ').toLowerCase()).append("):\n\n");
        int number = 1;
        List<com.pswied.core.crm.CrmModels.ServiceCase> activeCases = customer.cases().stream()
                .filter(serviceCase -> List.of("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER").contains(serviceCase.status())).toList();
        if (!activeCases.isEmpty()) {
            var serviceCase = activeCases.getFirst();
            answer.append(number++).append(". Follow up on ").append(serviceCase.reference()).append(" — ")
                    .append(serviceCase.subject()).append(" is ").append(serviceCase.status().replace('_', ' ').toLowerCase())
                    .append(" at ").append(serviceCase.priority().toLowerCase())
                    .append(" priority. Confirm the investigation owner and the next customer update.");
            if (serviceCase.escalated()) answer.append(" The case is escalated.");
            answer.append("\n\n");
        }
        if (!customer.interactions().isEmpty()) {
            var interaction = customer.interactions().getFirst();
            answer.append(number++).append(". Continue from the latest interaction: “")
                    .append(interaction.summary()).append("” via ")
                    .append(interaction.channel().replace('_', ' ').toLowerCase())
                    .append(". Check that the recorded outcome still reflects the customer's needs.\n\n");
        }
        if (!customer.products().isEmpty()) {
            var product = customer.products().getFirst();
            answer.append(number++).append(". Review the customer's ").append(product.displayName())
                    .append(" relationship in a needs conversation. Ask about goals, access needs, and relevant time horizon; use current approved product terms before discussing options.");
            if (!sources.isEmpty()) answer.append(" The synthetic product review guide is cited below.");
            answer.append("\n\n");
        }
        if (number == 1) answer.append("Confirm the customer's current goals and preferred follow-up channel before proposing a next step.\n\n");
        answer.append("These suggestions are advisory. Verify customer instructions and suitability requirements; the demo does not determine eligibility or recommend a specific product change.");
        return answer.toString().strip();
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
