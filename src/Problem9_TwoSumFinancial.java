import java.util.*;
import java.util.stream.Collectors;

/**
 * Problem 9: Two-Sum Problem Variants for Financial Transactions
 *
 * Concepts: Hash table for complement lookup, O(1) lookup for O(n) two-sum,
 *           time-window filtering, K-sum recursion, duplicate detection.
 */
public class Problem9_TwoSumFinancial {

    static class Transaction {
        final int id;
        final double amount;
        final String merchant;
        final String account;
        final long timestamp; // minutes from midnight for simplicity

        Transaction(int id, double amount, String merchant, String account, long timestamp) {
            this.id = id;
            this.amount = amount;
            this.merchant = merchant;
            this.account = account;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("{id:%d, amount:%.2f, merchant:\"%s\", account:\"%s\", time:%d}",
                    id, amount, merchant, account, timestamp);
        }
    }

    static class TransactionPair {
        final Transaction t1;
        final Transaction t2;

        TransactionPair(Transaction t1, Transaction t2) {
            this.t1 = t1;
            this.t2 = t2;
        }

        @Override
        public String toString() {
            return String.format("(id:%d + id:%d = %.2f)", t1.id, t2.id, t1.amount + t2.amount);
        }
    }

    static class DuplicateGroup {
        final double amount;
        final String merchant;
        final List<String> accounts;

        DuplicateGroup(double amount, String merchant, List<String> accounts) {
            this.amount = amount;
            this.merchant = merchant;
            this.accounts = accounts;
        }

        @Override
        public String toString() {
            return String.format("{amount:%.2f, merchant:\"%s\", accounts:%s}", amount, merchant, accounts);
        }
    }

    private final List<Transaction> transactions = new ArrayList<>();

    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    public void addTransaction(int id, double amount, String merchant, String account, long timestamp) {
        transactions.add(new Transaction(id, amount, merchant, account, timestamp));
    }

    /**
     * Classic Two-Sum: Find all pairs of transactions that sum to the target.
     * O(n) using a HashMap for complement lookup.
     */
    public List<TransactionPair> findTwoSum(double target) {
        List<TransactionPair> result = new ArrayList<>();
        // Map from amount -> list of transactions with that amount
        Map<Long, List<Transaction>> amountMap = new HashMap<>();

        for (Transaction t : transactions) {
            long amountKey = Math.round(t.amount * 100); // Use cents to avoid floating point issues
            long complementKey = Math.round(target * 100) - amountKey;

            List<Transaction> complements = amountMap.get(complementKey);
            if (complements != null) {
                for (Transaction comp : complements) {
                    result.add(new TransactionPair(comp, t));
                }
            }

            amountMap.computeIfAbsent(amountKey, k -> new ArrayList<>()).add(t);
        }

        return result;
    }

    /**
     * Two-Sum with time window: Find pairs that sum to target within a given time window (minutes).
     * O(n) with hash-based filtering.
     */
    public List<TransactionPair> findTwoSumWithTimeWindow(double target, long windowMinutes) {
        List<TransactionPair> result = new ArrayList<>();
        Map<Long, List<Transaction>> amountMap = new HashMap<>();

        // Sort by timestamp for efficient window filtering
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparingLong(t -> t.timestamp));

        for (Transaction t : sorted) {
            long amountKey = Math.round(t.amount * 100);
            long complementKey = Math.round(target * 100) - amountKey;

            List<Transaction> complements = amountMap.get(complementKey);
            if (complements != null) {
                for (Transaction comp : complements) {
                    if (Math.abs(t.timestamp - comp.timestamp) <= windowMinutes) {
                        result.add(new TransactionPair(comp, t));
                    }
                }
            }

            amountMap.computeIfAbsent(amountKey, k -> new ArrayList<>()).add(t);
        }

        return result;
    }

    /**
     * K-Sum: Find K transactions that sum to the target.
     * Uses recursive reduction to 2-sum with hash table.
     */
    public List<List<Transaction>> findKSum(int k, double target) {
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparingDouble(t -> t.amount));
        List<List<Transaction>> result = new ArrayList<>();
        findKSumHelper(sorted, k, target, 0, new ArrayList<>(), result);
        return result;
    }

    private void findKSumHelper(List<Transaction> txns, int k, double target,
                                int startIdx, List<Transaction> current,
                                List<List<Transaction>> result) {
        if (k == 2) {
            // Base case: two-sum with two pointers on sorted array
            int left = startIdx;
            int right = txns.size() - 1;
            while (left < right) {
                double sum = txns.get(left).amount + txns.get(right).amount;
                if (Math.abs(sum - target) < 0.01) {
                    List<Transaction> found = new ArrayList<>(current);
                    found.add(txns.get(left));
                    found.add(txns.get(right));
                    result.add(found);
                    left++;
                    right--;
                } else if (sum < target) {
                    left++;
                } else {
                    right--;
                }
            }
            return;
        }

        for (int i = startIdx; i < txns.size() - k + 1; i++) {
            // Prune: skip duplicates at the same recursion level
            if (i > startIdx && Math.abs(txns.get(i).amount - txns.get(i - 1).amount) < 0.01) {
                continue;
            }
            current.add(txns.get(i));
            findKSumHelper(txns, k - 1, target - txns.get(i).amount, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    /**
     * Detect duplicate payments: same amount, same merchant, different accounts.
     * O(n) using a composite-key HashMap.
     */
    public List<DuplicateGroup> detectDuplicates() {
        // Key: "amount|merchant" -> list of accounts
        Map<String, Set<String>> groupMap = new HashMap<>();

        for (Transaction t : transactions) {
            long amountCents = Math.round(t.amount * 100);
            String key = amountCents + "|" + t.merchant.toLowerCase();
            groupMap.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(t.account);
        }

        // Only return groups with multiple different accounts
        return groupMap.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    double amount = Long.parseLong(parts[0]) / 100.0;
                    String merchant = parts[1];
                    return new DuplicateGroup(amount, merchant, new ArrayList<>(e.getValue()));
                })
                .collect(Collectors.toList());
    }

    public int getTransactionCount() {
        return transactions.size();
    }

    // ======================== DEMO ========================
    public static void main(String[] args) {
        Problem9_TwoSumFinancial system = new Problem9_TwoSumFinancial();

        System.out.println("=== Financial Transaction Analysis ===\n");

        // Add sample transactions
        system.addTransaction(1, 500.00, "Store A", "acc1", 600);  // 10:00
        system.addTransaction(2, 300.00, "Store B", "acc2", 615);  // 10:15
        system.addTransaction(3, 200.00, "Store C", "acc3", 630);  // 10:30
        system.addTransaction(4, 700.00, "Store D", "acc4", 645);  // 10:45
        system.addTransaction(5, 800.00, "Store E", "acc5", 660);  // 11:00
        system.addTransaction(6, 500.00, "Store A", "acc6", 680);  // 11:20 — suspicious duplicate
        system.addTransaction(7, 100.00, "Store F", "acc7", 720);  // 12:00
        system.addTransaction(8, 400.00, "Store G", "acc8", 780);  // 13:00
        system.addTransaction(9, 300.00, "Store B", "acc9", 615);  // Same as #2 — duplicate

        System.out.printf("Loaded %d transactions%n%n", system.getTransactionCount());

        // Classic Two-Sum
        System.out.println("=== findTwoSum(target=500) ===");
        List<TransactionPair> pairs = system.findTwoSum(500);
        for (TransactionPair pair : pairs) {
            System.out.printf("  %s%n", pair);
        }

        System.out.println("\n=== findTwoSum(target=1000) ===");
        pairs = system.findTwoSum(1000);
        for (TransactionPair pair : pairs) {
            System.out.printf("  %s%n", pair);
        }

        // Two-Sum with time window (within 60 minutes)
        System.out.println("\n=== findTwoSumWithTimeWindow(target=500, window=60min) ===");
        pairs = system.findTwoSumWithTimeWindow(500, 60);
        for (TransactionPair pair : pairs) {
            System.out.printf("  %s%n", pair);
        }

        // K-Sum
        System.out.println("\n=== findKSum(k=3, target=1000) ===");
        List<List<Transaction>> kResults = system.findKSum(3, 1000);
        for (List<Transaction> group : kResults) {
            String ids = group.stream().map(t -> "id:" + t.id).collect(Collectors.joining(", "));
            double sum = group.stream().mapToDouble(t -> t.amount).sum();
            System.out.printf("  [%s] = %.2f%n", ids, sum);
        }

        // Duplicate detection
        System.out.println("\n=== detectDuplicates() ===");
        List<DuplicateGroup> duplicates = system.detectDuplicates();
        for (DuplicateGroup dup : duplicates) {
            System.out.printf("  %s%n", dup);
        }

        // Benchmark: Two-Sum on large dataset
        System.out.println("\n=== Benchmark: Two-Sum on 100,000 transactions ===");
        Problem9_TwoSumFinancial bench = new Problem9_TwoSumFinancial();
        Random random = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            bench.addTransaction(i, random.nextInt(10000) / 100.0, "M" + (i % 100),
                    "acc" + i, random.nextInt(1440));
        }

        long start = System.nanoTime();
        List<TransactionPair> benchResult = bench.findTwoSum(100.0);
        long elapsed = System.nanoTime() - start;
        System.out.printf("Found %d pairs in %.2f ms%n", benchResult.size(), elapsed / 1_000_000.0);
    }
}
