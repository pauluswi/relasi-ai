package com.pswied.core.ai;

import com.pswied.core.crm.CrmModels.Customer360;
import com.pswied.core.crm.CrmModels.ProductRelationship;
import com.pswied.core.crm.CrmModels.ServiceCase;
import com.pswied.core.knowledge.KnowledgeService.RetrievedChunk;

import java.util.List;

/** Deterministic response provider for the local demonstration scenarios. */
public final class MockLlmProvider implements LlmProvider {
    @Override
    public String generate(String userMessage, NlpResult analysis, Customer360 customerContext,
                           List<RetrievedChunk> knowledgeContext) {
        if (!knowledgeContext.isEmpty()) return groundedKnowledgeAnswer(knowledgeContext);
        String normalized = userMessage.toLowerCase(java.util.Locale.ROOT);
        if (isCustomerSummary(normalized)) return customerSummary(customerContext);
        if (isCaseStatusQuestion(normalized) && customerContext != null) return caseStatusSummary(customerContext.cases());
        if ("TRANSACTION_COMPLAINT".equals(analysis.intent())) return transactionComplaintResponse(analysis);
        if ("id".equals(analysis.language())) {
            return "Saya memahami pertanyaan Anda sebagai " + analysis.intent().replace('_', ' ').toLowerCase()
                    + ". Petugas dapat membantu memeriksa informasi ini dan membuat kasus layanan jika diperlukan."
                    + " Respons ini dibuat oleh mock lokal dan bukan konfirmasi transaksi.";
        }
        return "I classified this request as " + analysis.intent().replace('_', ' ').toLowerCase()
                + ". A service officer can review the details and create a service case if needed."
                + " This deterministic mock response does not confirm a payment or account transaction.";
    }

    @Override
    public String providerId() {
        return "DETERMINISTIC_MOCK_LLM";
    }

    private static String transactionComplaintResponse(NlpResult analysis) {
        String amount = entityValue(analysis, "AMOUNT");
        String date = entityValue(analysis, "TRANSACTION_DATE");
        StringBuilder answer = new StringBuilder("This appears to be a transfer or transaction complaint.");
        if (amount != null) answer.append(" Reported amount: ").append(amount).append('.');
        if (date != null) answer.append(" Reported date: ").append(date).append('.');
        answer.append(" A service officer should verify the transaction through the authorized banking process and can open a ")
                .append(analysis.suggestedCaseCategory().replace('_', ' ').toLowerCase())
                .append(" case (suggested priority: ").append(analysis.suggestedPriority()).append(").")
                .append(" This local mock does not check payment rails or confirm settlement.");
        return answer.toString();
    }

    private static String customerSummary(Customer360 context) {
        if (context == null) return "Select a customer in your authorized scope to request a Customer 360 summary.";
        var customer = context.customer();
        String products = context.products().stream().map(ProductRelationship::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
        List<ServiceCase> openCases = context.cases().stream()
                .filter(serviceCase -> !List.of("RESOLVED", "CLOSED").contains(serviceCase.status())).toList();
        String caseSummary = openCases.isEmpty() ? "No open service cases are recorded."
                : "Open cases: " + openCases.stream().map(serviceCase -> serviceCase.reference() + " ("
                + serviceCase.status().replace('_', ' ').toLowerCase() + ")").collect(java.util.stream.Collectors.joining(", ")) + ".";
        String latestInteraction = context.interactions().isEmpty() ? "No interactions are recorded."
                : "Most recent interaction: " + context.interactions().getFirst().summary() + ".";
        String nextAction = openCases.isEmpty()
                ? "Review the recent interaction and confirm whether follow-up is needed."
                : "Review open cases and update the customer on their progress.";
        return "Customer 360 summary for " + customer.fullName() + " (" + customer.customerNumber() + "). "
                + "Segment: " + customer.segment().replace('_', ' ').toLowerCase() + "; status: " + customer.status().toLowerCase() + ". "
                + "Products: " + (products.isBlank() ? "none recorded" : products) + ". "
                + caseSummary + " " + latestInteraction + " Simulated risk indicator: " + customer.riskIndicator() + ". "
                + "Suggested next action: " + nextAction;
    }

    private static String caseStatusSummary(List<ServiceCase> cases) {
        if (cases.isEmpty()) return "No service cases are recorded for the selected customer.";
        return "Service cases for the selected customer: " + cases.stream().map(serviceCase ->
                serviceCase.reference() + " is " + serviceCase.status().replace('_', ' ').toLowerCase()
                        + " (" + serviceCase.priority().toLowerCase() + " priority)")
                .collect(java.util.stream.Collectors.joining("; ")) + ".";
    }

    private static String groundedKnowledgeAnswer(List<RetrievedChunk> chunks) {
        StringBuilder answer = new StringBuilder("Based on the retrieved synthetic demo guidance:\n\n");
        for (RetrievedChunk chunk : chunks) {
            answer.append(chunk.content()).append("\n\n");
        }
        answer.append("This guidance is synthetic showcase content, not a real bank policy.");
        return answer.toString().strip();
    }

    private static boolean isCustomerSummary(String text) {
        return contains(text, "summarize this customer", "customer summary", "relationship with the bank",
                "customer 360", "summarize this customer's", "ringkas nasabah", "rangkuman nasabah", "hubungan nasabah");
    }

    private static boolean isCaseStatusQuestion(String text) {
        return contains(text, "case status", "service case", "open case", "status kasus", "status pengaduan");
    }

    private static String entityValue(NlpResult result, String type) {
        return result.entities().stream().filter(entity -> type.equals(entity.type())).map(NlpResult.Entity::value).findFirst().orElse(null);
    }

    private static boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
