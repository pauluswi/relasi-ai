package com.pswied.core.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bundled synthetic knowledge corpus with deterministic in-process retrieval. */
public final class KnowledgeService {
    private static final List<String> BUNDLED_RESOURCES = List.of(
            "/knowledge/bi-fast-unsuccessful-transaction.md",
            "/knowledge/card-dispute.md",
            "/knowledge/mobile-banking-login.md");
    private static final Pattern TOKEN = Pattern.compile("(?U)[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*");
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "for", "from", "how", "i", "in", "is", "it", "of", "on", "or", "the", "this", "to", "what", "when", "where", "which", "who", "with", "you", "your", "apa", "bagaimana", "dan", "di", "ini", "untuk", "yang", "dengan", "cara", "prosedur", "penanganan", "melakukan");

    private final List<KnowledgeDocument> documents;
    private final List<KnowledgeChunk> chunks;

    public KnowledgeService() {
        this.documents = loadDocuments();
        this.chunks = documents.stream().flatMap(document -> chunk(document).stream()).toList();
    }

    public List<KnowledgeDocument> documents() {
        return documents;
    }

    public int chunkCount(String documentId) {
        return (int) chunks.stream().filter(chunk -> chunk.documentId().equals(documentId)).count();
    }

    public List<RetrievedChunk> search(String query, int limit) {
        if (query == null || query.isBlank() || limit < 1) return List.of();
        Set<String> terms = queryTerms(query);
        if (terms.isEmpty()) return List.of();

        return chunks.stream()
                .map(chunk -> score(chunk, terms))
                .filter(result -> result.score() >= 0.34)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed()
                        .thenComparing(RetrievedChunk::reference))
                .limit(Math.min(limit, 5))
                .toList();
    }

    private RetrievedChunk score(KnowledgeChunk chunk, Set<String> queryTerms) {
        Set<String> contentTerms = tokens(chunk.content());
        Set<String> headingTerms = tokens(chunk.title() + " " + chunk.section());
        int matched = 0;
        int headingMatches = 0;
        for (String term : queryTerms) {
            if (contentTerms.contains(term) || headingTerms.contains(term)) matched++;
            if (headingTerms.contains(term)) headingMatches++;
        }
        double coverage = (double) matched / queryTerms.size();
        double headingCoverage = (double) headingMatches / queryTerms.size();
        double score = Math.min(1.0, coverage * 0.78 + headingCoverage * 0.22);
        return new RetrievedChunk(chunk.documentId(), chunk.chunkId(), chunk.title(), chunk.source(), chunk.section(),
                chunk.effectiveDate(), chunk.content(), score, chunk.documentId() + "#" + chunk.chunkId());
    }

    private static Set<String> queryTerms(String query) {
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("bi\\s+fast", "bi-fast");
        Set<String> terms = tokens(normalized);
        terms.removeAll(STOP_WORDS);
        if (terms.contains("unsuccessful")) terms.add("failed");
        if (terms.contains("failed")) terms.add("unsuccessful");
        if (terms.contains("login")) terms.add("sign-in");
        return terms;
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    private static List<KnowledgeDocument> loadDocuments() {
        List<KnowledgeDocument> loaded = new ArrayList<>();
        for (String resource : BUNDLED_RESOURCES) {
            try (var input = KnowledgeService.class.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing bundled knowledge resource: " + resource);
                loaded.add(parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), resource));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load bundled knowledge resource: " + resource, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private static KnowledgeDocument parse(String markdown, String resource) {
        String[] lines = markdown.split("\\R", -1);
        Map<String, String> metadata = new LinkedHashMap<>();
        int cursor = 0;
        for (; cursor < lines.length; cursor++) {
            String line = lines[cursor].strip();
            if ("---".equals(line)) {
                cursor++;
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) metadata.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        String body = String.join("\n", java.util.Arrays.copyOfRange(lines, cursor, lines.length)).strip();
        String id = requireMetadata(metadata, "id", resource);
        return new KnowledgeDocument(id, requireMetadata(metadata, "title", resource),
                requireMetadata(metadata, "source", resource), requireMetadata(metadata, "section", resource),
                requireMetadata(metadata, "effective-date", resource), body, resource);
    }

    private static String requireMetadata(Map<String, String> metadata, String name, String resource) {
        String value = metadata.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing '" + name + "' in " + resource);
        return value;
    }

    private static List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        List<String> paragraphs = java.util.Arrays.stream(document.content().split("\\n\\s*\\n"))
                .map(String::strip).filter(paragraph -> !paragraph.isBlank()).toList();
        List<KnowledgeChunk> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int wordCount = 0;
        int chunkNumber = 1;
        for (String paragraph : paragraphs) {
            int paragraphWords = paragraph.split("\\s+").length;
            if (wordCount > 0 && wordCount + paragraphWords > 100) {
                result.add(toChunk(document, chunkNumber++, current.toString()));
                current.setLength(0);
                wordCount = 0;
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(paragraph);
            wordCount += paragraphWords;
        }
        if (current.length() > 0) result.add(toChunk(document, chunkNumber, current.toString()));
        return List.copyOf(result);
    }

    private static KnowledgeChunk toChunk(KnowledgeDocument document, int number, String content) {
        return new KnowledgeChunk(document.id(), "%s-C%02d".formatted(document.id(), number), document.title(),
                document.source(), document.section(), document.effectiveDate(), content);
    }

    public record KnowledgeDocument(String id, String title, String source, String section,
                                    String effectiveDate, String content, String resource) {
        public Map<String, Object> summaryJson(int chunkCount) {
            return Map.of("id", id, "title", title, "source", source, "section", section,
                    "effectiveDate", effectiveDate, "chunkCount", chunkCount);
        }
    }

    public record KnowledgeChunk(String documentId, String chunkId, String title, String source,
                                 String section, String effectiveDate, String content) {
    }

    public record RetrievedChunk(String documentId, String chunkId, String title, String source,
                                 String section, String effectiveDate, String content, double score, String reference) {
        public Map<String, Object> asJsonValue() {
            return Map.of("documentId", documentId, "chunkId", chunkId, "title", title, "source", source,
                    "section", section, "effectiveDate", effectiveDate, "content", content,
                    "score", score, "reference", reference);
        }
    }
}
