import java.util.*;

public class PlagiarismDetector {

    // ngram -> set of document IDs
    private HashMap<String, Set<String>> ngramIndex = new HashMap<>();

    private int N = 5; // size of n-gram

    // Break document into n-grams
    private List<String> generateNGrams(String text) {

        String[] words = text.toLowerCase().split("\\s+");
        List<String> ngrams = new ArrayList<>();

        for (int i = 0; i <= words.length - N; i++) {

            StringBuilder sb = new StringBuilder();

            for (int j = 0; j < N; j++) {
                sb.append(words[i + j]).append(" ");
            }

            ngrams.add(sb.toString().trim());
        }

        return ngrams;
    }

    // Add document to database
    public void addDocument(String docId, String text) {

        List<String> ngrams = generateNGrams(text);

        for (String ngram : ngrams) {

            ngramIndex.putIfAbsent(ngram, new HashSet<>());

            ngramIndex.get(ngram).add(docId);
        }
    }

    // Analyze new document
    public void analyzeDocument(String docId, String text) {

        List<String> ngrams = generateNGrams(text);

        HashMap<String, Integer> matchCounts = new HashMap<>();

        for (String ngram : ngrams) {

            if (ngramIndex.containsKey(ngram)) {

                for (String otherDoc : ngramIndex.get(ngram)) {

                    matchCounts.put(
                        otherDoc,
                        matchCounts.getOrDefault(otherDoc, 0) + 1
                    );
                }
            }
        }

        int total = ngrams.size();

        for (String doc : matchCounts.keySet()) {

            int matches = matchCounts.get(doc);

            double similarity = (matches * 100.0) / total;

            System.out.println("Matches with " + doc + ": " + matches);
            System.out.printf("Similarity: %.2f%%\n", similarity);

            if (similarity > 30) {
                System.out.println("⚠ POSSIBLE PLAGIARISM\n");
            }
        }
    }

    public static void main(String[] args) {

        PlagiarismDetector detector = new PlagiarismDetector();

        // Existing documents
        detector.addDocument(
            "essay_089.txt",
            "machine learning improves systems by learning from data automatically"
        );

        detector.addDocument(
            "essay_092.txt",
            "deep learning and machine learning are important areas of artificial intelligence"
        );

        // New submission
        detector.analyzeDocument(
            "essay_123.txt",
            "machine learning improves systems by learning from large datasets automatically"
        );
    }
}