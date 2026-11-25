package com.pswied.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small offline NLP mock for repeatable demonstration phrases. */
public final class DeterministicNlpEngine implements NlpEngine {
    private static final Pattern CUSTOMER_ID = Pattern.compile("(?i)\\bCUST-\\d{4,}\\b");
    private static final Pattern TRANSACTION_REFERENCE = Pattern.compile("(?i)\\b(?:TRX|TXN|REF)[-_]?[A-Z0-9-]{4,}\\b");
    private static final Pattern AMOUNT = Pattern.compile("(?i)(?:\\bIDR\\s*|\\bRp\\.?\\s*|\\bUSD\\s*|\\bEUR\\s*)\\d[\\d.,]*(?:\\s*(?:million|m|juta|ribu))?|\\b\\d[\\d.,]*\\s*(?:million|juta|ribu)\\b");
    private static final Pattern DATE = Pattern.compile("(?i)\\b(?:yesterday|today|kemarin|hari ini|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?))\\b");
    private static final Pattern ACCOUNT_REFERENCE = Pattern.compile("(?i)\\b(?:account|rekening|acct)\\s*(?:number|no\\.?)?\\s*[:#-]?\\s*([A-Z0-9-]{5,})");

    @Override
    public NlpResult analyze(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        String intent = intent(normalized);
        String sentiment = "UNRESOLVED_PAYMENT_CASE_SEARCH".equals(intent) ? "NEUTRAL" : sentiment(normalized);
        String category = caseCategory(intent);
        String priority = "URGENT_FRUSTRATED".equals(sentiment)
                || ("TRANSACTION_COMPLAINT".equals(intent) && "NEGATIVE".equals(sentiment)) ? "HIGH" : "NORMAL";
        List<NlpResult.Entity> entities = new ArrayList<>();
        addFirst(entities, CUSTOMER_ID, text, "CUSTOMER_ID", 0);
        addFirst(entities, TRANSACTION_REFERENCE, text, "TRANSACTION_REFERENCE", 0);
        addFirst(entities, AMOUNT, text, "AMOUNT", 0);
        addFirst(entities, DATE, text, "TRANSACTION_DATE", 0);
        addFirst(entities, ACCOUNT_REFERENCE, text, "ACCOUNT_REFERENCE", 1);
        addProductEntities(entities, normalized);
        if (!"GENERAL".equals(category)) entities.add(new NlpResult.Entity("COMPLAINT_CATEGORY", category));

        return new NlpResult(intent, confidence(intent), sentiment, language(normalized), category, priority, entities);
    }

    private static String intent(String text) {
        if (containsAny(text, "find customers who have unresolved", "unresolved payment complaints",
                "find customers with unresolved payment", "summarize their cases", "cari nasabah dengan pengaduan pembayaran yang belum selesai")) {
            return "UNRESOLVED_PAYMENT_CASE_SEARCH";
        }
        boolean failedTransfer = containsAny(text, "not received", "hasn't received", "haven't received", "not arrived",
                "belum diterima", "belum masuk", "tidak masuk", "gagal transfer", "transfer gagal", "failed transfer",
                "failed payment", "unsuccessful bi-fast", "bi-fast transaction failed");
        if (failedTransfer) return "TRANSACTION_COMPLAINT";
        if (containsAny(text, "loan", "credit facility", "pinjaman", "kredit", "angsuran")) return "LOAN_INQUIRY";
        if (containsAny(text, "debit card", "credit card", "card issue", " kartu", "kartu", "atm card")) return "CARD_ISSUE";
        if (containsAny(text, "account", "rekening", "balance", "saldo", "statement", "mutasi")) return "ACCOUNT_INQUIRY";
        if (containsAny(text, "payment", "transfer", "bi-fast", "transaction", "pembayaran", "transaksi", "remittance")) return "PAYMENT_INQUIRY";
        if (containsAny(text, "product", "produk", "savings", "tabungan", "deposit", "mortgage")) return "PRODUCT_INQUIRY";
        return "GENERAL_INQUIRY";
    }

    private static String sentiment(String text) {
        if (containsAny(text, "urgent", "frustrated", "angry", "immediately", "asap", "segera", "mendesak", "marah", "kecewa")) {
            return "URGENT_FRUSTRATED";
        }
        if (containsAny(text, "not received", "hasn't received", "haven't received", "not working", "unable", "failed", "problem",
                "complaint", "belum", "tidak", "gagal", "masalah", "keluhan", "error")) {
            return "NEGATIVE";
        }
        if (containsAny(text, "thank you", "thanks", "great", "good", "excellent", "terima kasih", "bagus", "puas")) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private static String language(String text) {
        if (containsAny(text, "yang", "untuk", "saya", "rekening", "tolong", "belum", "tidak", "transfer", "bagaimana")) return "id";
        if (containsAny(text, "und", "nicht", "bitte", "konto", "überweisung", "ich", "ist")) return "de";
        return "en";
    }

    private static String caseCategory(String intent) {
        return switch (intent) {
            case "TRANSACTION_COMPLAINT" -> "TRANSACTION_DISPUTE";
            case "PAYMENT_INQUIRY" -> "PAYMENT_COMPLAINT";
            case "LOAN_INQUIRY" -> "LOAN_INQUIRY";
            case "ACCOUNT_INQUIRY" -> "ACCOUNT_INQUIRY";
            case "CARD_ISSUE" -> "CARD_ISSUE";
            default -> "GENERAL_BANKING_INQUIRY";
        };
    }

    private static double confidence(String intent) {
        return "GENERAL_INQUIRY".equals(intent) ? 0.55 : "TRANSACTION_COMPLAINT".equals(intent) ? 0.96 : 0.88;
    }

    private static void addFirst(List<NlpResult.Entity> entities, Pattern pattern, String text, String type, int group) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) entities.add(new NlpResult.Entity(type, matcher.group(group).strip()));
    }

    private static void addProductEntities(List<NlpResult.Entity> entities, String text) {
        List<String> products = List.of("BI-FAST", "mobile banking", "debit card", "credit card", "savings", "tabungan", "loan", "pinjaman");
        for (String product : products) {
            if (text.contains(product)) entities.add(new NlpResult.Entity("PRODUCT_NAME", product));
        }
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }
}
