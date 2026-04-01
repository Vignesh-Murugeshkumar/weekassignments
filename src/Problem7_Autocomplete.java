import java.util.*;
import java.util.stream.Collectors;

/**
 * Problem 7: Autocomplete System for Search Engine
 *
 * Concepts: Trie + HashMap hybrid, frequency counting, prefix matching,
 *           top-K results with min-heap, typo correction (edit distance).
 */
public class Problem7_Autocomplete {

    /**
     * Trie node. Each node stores children in a HashMap and optionally a frequency count.
     */
    static class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord = false;
        long frequency = 0;
    }

    private final TrieNode root = new TrieNode();

    // Full query -> frequency (for fast lookup and updates)
    private final Map<String, Long> queryFrequency = new HashMap<>();

    // Cache for popular prefix results (prefix -> top results)
    private final Map<String, List<SuggestionResult>> prefixCache = new LinkedHashMap<>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<SuggestionResult>> eldest) {
            return size() > 10_000;
        }
    };

    static class SuggestionResult implements Comparable<SuggestionResult> {
        final String query;
        final long frequency;

        SuggestionResult(String query, long frequency) {
            this.query = query;
            this.frequency = frequency;
        }

        @Override
        public int compareTo(SuggestionResult other) {
            return Long.compare(this.frequency, other.frequency); // min-heap order
        }

        @Override
        public String toString() {
            return String.format("\"%s\" (%,d searches)", query, frequency);
        }
    }

    /**
     * Insert or update a query with its frequency. O(L) where L = query length.
     */
    public void addQuery(String query, long frequency) {
        String normalized = query.toLowerCase().trim();
        if (normalized.isEmpty()) return;

        // Update trie
        TrieNode node = root;
        for (char c : normalized.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isEndOfWord = true;
        node.frequency = frequency;

        // Update frequency map
        queryFrequency.put(normalized, frequency);

        // Invalidate affected prefix cache entries
        for (int i = 1; i <= normalized.length(); i++) {
            prefixCache.remove(normalized.substring(0, i));
        }
    }

    /**
     * Update (increment) frequency for a query. Called when a user performs a search.
     */
    public long updateFrequency(String query) {
        String normalized = query.toLowerCase().trim();
        long newFreq = queryFrequency.merge(normalized, 1L, Long::sum);

        // Update trie node
        TrieNode node = findNode(normalized);
        if (node != null && node.isEndOfWord) {
            node.frequency = newFreq;
        } else {
            addQuery(normalized, newFreq);
        }

        // Invalidate cache
        for (int i = 1; i <= normalized.length(); i++) {
            prefixCache.remove(normalized.substring(0, i));
        }

        return newFreq;
    }

    /**
     * Search for top K suggestions matching a prefix. Uses trie traversal + min-heap.
     */
    public List<SuggestionResult> search(String prefix, int topK) {
        String normalized = prefix.toLowerCase().trim();

        // Check cache first
        List<SuggestionResult> cached = prefixCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        // Navigate to prefix node
        TrieNode prefixNode = findNode(normalized);
        if (prefixNode == null) {
            return Collections.emptyList();
        }

        // Collect all completions from this prefix using DFS
        PriorityQueue<SuggestionResult> minHeap = new PriorityQueue<>();
        collectCompletions(prefixNode, new StringBuilder(normalized), minHeap, topK);

        // Extract results in descending order
        List<SuggestionResult> results = new ArrayList<>(minHeap);
        results.sort(Comparator.comparingLong((SuggestionResult s) -> s.frequency).reversed());

        // Cache the result
        prefixCache.put(normalized, results);

        return results;
    }

    /**
     * Suggest spelling corrections using edit distance 1 (for typos).
     */
    public List<SuggestionResult> suggestCorrections(String query, int topK) {
        String normalized = query.toLowerCase().trim();

        // Generate all strings at edit distance 1
        Set<String> candidates = getEdits(normalized);

        // Filter to existing queries and rank by frequency
        return candidates.stream()
                .filter(queryFrequency::containsKey)
                .map(c -> new SuggestionResult(c, queryFrequency.get(c)))
                .sorted(Comparator.comparingLong((SuggestionResult s) -> s.frequency).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Find the trie node for a given prefix.
     */
    private TrieNode findNode(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return null;
        }
        return node;
    }

    /**
     * DFS to collect all completions from a trie node, keeping only top K via min-heap.
     */
    private void collectCompletions(TrieNode node, StringBuilder currentWord,
                                    PriorityQueue<SuggestionResult> minHeap, int topK) {
        if (node.isEndOfWord) {
            SuggestionResult result = new SuggestionResult(currentWord.toString(), node.frequency);
            if (minHeap.size() < topK) {
                minHeap.offer(result);
            } else if (node.frequency > minHeap.peek().frequency) {
                minHeap.poll();
                minHeap.offer(result);
            }
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            currentWord.append(entry.getKey());
            collectCompletions(entry.getValue(), currentWord, minHeap, topK);
            currentWord.deleteCharAt(currentWord.length() - 1);
        }
    }

    /**
     * Generate all strings within edit distance 1 (deletions, insertions, replacements, transpositions).
     */
    private Set<String> getEdits(String word) {
        Set<String> edits = new HashSet<>();
        char[] alphabet = "abcdefghijklmnopqrstuvwxyz0123456789 ".toCharArray();

        // Deletions
        for (int i = 0; i < word.length(); i++) {
            edits.add(word.substring(0, i) + word.substring(i + 1));
        }

        // Transpositions
        for (int i = 0; i < word.length() - 1; i++) {
            char[] chars = word.toCharArray();
            char tmp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = tmp;
            edits.add(new String(chars));
        }

        // Replacements
        for (int i = 0; i < word.length(); i++) {
            for (char c : alphabet) {
                edits.add(word.substring(0, i) + c + word.substring(i + 1));
            }
        }

        // Insertions
        for (int i = 0; i <= word.length(); i++) {
            for (char c : alphabet) {
                edits.add(word.substring(0, i) + c + word.substring(i));
            }
        }

        edits.remove(word); // Remove the original
        return edits;
    }

    public int getTotalQueries() {
        return queryFrequency.size();
    }

    // ======================== DEMO ========================
    public static void main(String[] args) {
        Problem7_Autocomplete autocomplete = new Problem7_Autocomplete();

        System.out.println("=== Autocomplete System ===\n");

        // Populate with query data
        System.out.println("Loading search query database...");
        autocomplete.addQuery("java tutorial", 1_234_567);
        autocomplete.addQuery("javascript", 987_654);
        autocomplete.addQuery("java download", 456_789);
        autocomplete.addQuery("java 21 features", 234_567);
        autocomplete.addQuery("java streams", 189_000);
        autocomplete.addQuery("java hashmap", 178_000);
        autocomplete.addQuery("java collections", 167_000);
        autocomplete.addQuery("java spring boot", 312_000);
        autocomplete.addQuery("java interview questions", 445_000);
        autocomplete.addQuery("java vs python", 298_000);
        autocomplete.addQuery("python tutorial", 1_100_000);
        autocomplete.addQuery("python download", 500_000);
        autocomplete.addQuery("python machine learning", 890_000);
        autocomplete.addQuery("react tutorial", 700_000);
        autocomplete.addQuery("react hooks", 650_000);
        autocomplete.addQuery("rust programming", 240_000);
        autocomplete.addQuery("ruby on rails", 180_000);

        System.out.printf("Loaded %d queries%n%n", autocomplete.getTotalQueries());

        // Search by prefix
        System.out.println("=== search(\"jav\") ===");
        List<SuggestionResult> results = autocomplete.search("jav", 10);
        int rank = 1;
        for (SuggestionResult r : results) {
            System.out.printf("  %d. %s%n", rank++, r);
        }

        System.out.println("\n=== search(\"python\") ===");
        results = autocomplete.search("python", 5);
        rank = 1;
        for (SuggestionResult r : results) {
            System.out.printf("  %d. %s%n", rank++, r);
        }

        System.out.println("\n=== search(\"r\") ===");
        results = autocomplete.search("r", 5);
        rank = 1;
        for (SuggestionResult r : results) {
            System.out.printf("  %d. %s%n", rank++, r);
        }

        // Update frequency (trending search)
        System.out.println("\n=== Frequency Updates (Trending) ===");
        for (int i = 0; i < 5; i++) {
            long freq = autocomplete.updateFrequency("java 21 features");
            System.out.printf("updateFrequency(\"java 21 features\") -> %,d%n", freq);
        }

        // Typo correction
        System.out.println("\n=== Typo Correction ===");
        System.out.println("suggestCorrections(\"jva tutorial\"):");
        List<SuggestionResult> corrections = autocomplete.suggestCorrections("jva tutorial", 5);
        for (SuggestionResult c : corrections) {
            System.out.printf("  Did you mean: %s%n", c);
        }

        System.out.println("\nsuggestCorrections(\"pythn\"):");
        corrections = autocomplete.suggestCorrections("pythn", 5);
        for (SuggestionResult c : corrections) {
            System.out.printf("  Did you mean: %s%n", c);
        }

        // Benchmark
        System.out.println("\n=== Benchmark: 100,000 prefix searches ===");
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            autocomplete.search("jav", 10);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("100,000 searches in %.2f ms (%.3f ms/search)%n",
                elapsed / 1_000_000.0, elapsed / 1_000_000.0 / 100_000);
    }
}
