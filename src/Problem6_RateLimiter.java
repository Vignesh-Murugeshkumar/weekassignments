import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Problem 6: Distributed Rate Limiter for API Gateway
 *
 * Concepts: Token bucket algorithm, hash table for client tracking,
 *           time-based operations, concurrent access handling.
 */
public class Problem6_RateLimiter {

    /**
     * Token bucket for a single client.
     */
    static class TokenBucket {
        private double tokens;
        private long lastRefillTimeNanos;
        private final int maxTokens;
        private final double refillRatePerSecond; // tokens per second

        TokenBucket(int maxTokens, double refillRatePerSecond) {
            this.maxTokens = maxTokens;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = maxTokens;
            this.lastRefillTimeNanos = System.nanoTime();
        }

        /**
         * Refill tokens based on elapsed time. This is lazy — only refills when checked.
         */
        synchronized void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillTimeNanos) / 1_000_000_000.0;
            double newTokens = elapsedSeconds * refillRatePerSecond;
            tokens = Math.min(maxTokens, tokens + newTokens);
            lastRefillTimeNanos = now;
        }

        /**
         * Try to consume one token. Returns true if allowed, false if rate limited.
         */
        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized int getRemainingTokens() {
            refill();
            return (int) tokens;
        }

        synchronized double getSecondsUntilNextToken() {
            refill();
            if (tokens >= 1.0) return 0;
            return (1.0 - tokens) / refillRatePerSecond;
        }
    }

    /**
     * Result of a rate limit check.
     */
    static class RateLimitResult {
        final boolean allowed;
        final int remainingRequests;
        final int limit;
        final double retryAfterSeconds; // 0 if allowed

        RateLimitResult(boolean allowed, int remaining, int limit, double retryAfterSeconds) {
            this.allowed = allowed;
            this.remainingRequests = remaining;
            this.limit = limit;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        @Override
        public String toString() {
            if (allowed) {
                return String.format("Allowed (%d requests remaining)", remainingRequests);
            }
            return String.format("Denied (0 requests remaining, retry after %.0fs)", retryAfterSeconds);
        }
    }

    /**
     * Status snapshot for a client.
     */
    static class RateLimitStatus {
        final String clientId;
        final int used;
        final int limit;
        final int remaining;
        final double resetSeconds;

        RateLimitStatus(String clientId, int used, int limit, int remaining, double resetSeconds) {
            this.clientId = clientId;
            this.used = used;
            this.limit = limit;
            this.remaining = remaining;
            this.resetSeconds = resetSeconds;
        }

        @Override
        public String toString() {
            return String.format("{clientId=%s, used=%d, limit=%d, remaining=%d, resetIn=%.0fs}",
                    clientId, used, limit, remaining, resetSeconds);
        }
    }

    // Client ID -> Token Bucket
    private final ConcurrentHashMap<String, TokenBucket> clientBuckets = new ConcurrentHashMap<>();

    private final int maxRequestsPerWindow;
    private final double refillRatePerSecond;

    /**
     * @param maxRequestsPerWindow e.g. 1000 requests
     * @param windowSeconds        e.g. 3600 seconds (1 hour)
     */
    public Problem6_RateLimiter(int maxRequestsPerWindow, int windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.refillRatePerSecond = (double) maxRequestsPerWindow / windowSeconds;
    }

    /**
     * Check rate limit for a client. O(1) hash table lookup + O(1) token bucket operation.
     */
    public RateLimitResult checkRateLimit(String clientId) {
        TokenBucket bucket = clientBuckets.computeIfAbsent(clientId,
                k -> new TokenBucket(maxRequestsPerWindow, refillRatePerSecond));

        boolean allowed = bucket.tryConsume();
        int remaining = bucket.getRemainingTokens();

        if (allowed) {
            return new RateLimitResult(true, remaining, maxRequestsPerWindow, 0);
        } else {
            double retryAfter = bucket.getSecondsUntilNextToken();
            return new RateLimitResult(false, 0, maxRequestsPerWindow, retryAfter);
        }
    }

    /**
     * Get the current rate limit status for a client without consuming a token.
     */
    public RateLimitStatus getRateLimitStatus(String clientId) {
        TokenBucket bucket = clientBuckets.get(clientId);
        if (bucket == null) {
            return new RateLimitStatus(clientId, 0, maxRequestsPerWindow, maxRequestsPerWindow, 0);
        }

        int remaining = bucket.getRemainingTokens();
        int used = maxRequestsPerWindow - remaining;
        double resetSeconds = remaining < maxRequestsPerWindow
                ? (maxRequestsPerWindow - remaining) / refillRatePerSecond
                : 0;

        return new RateLimitStatus(clientId, used, maxRequestsPerWindow, remaining, resetSeconds);
    }

    /**
     * Remove a client's rate limit state (e.g., client deregistered).
     */
    public boolean removeClient(String clientId) {
        return clientBuckets.remove(clientId) != null;
    }

    /**
     * Get total number of tracked clients.
     */
    public int getTrackedClientCount() {
        return clientBuckets.size();
    }

    // ======================== DEMO ========================
    public static void main(String[] args) throws InterruptedException {
        // 1000 requests per hour = ~0.278 requests/second refill rate
        Problem6_RateLimiter limiter = new Problem6_RateLimiter(1000, 3600);

        System.out.println("=== API Rate Limiter (Token Bucket) ===");
        System.out.println("Config: 1000 requests/hour per client\n");

        String client = "abc123";

        // Make a few requests
        System.out.println("=== Normal Usage ===");
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = limiter.checkRateLimit(client);
            System.out.printf("checkRateLimit(\"%s\") -> %s%n", client, result);
        }

        System.out.println("\nStatus: " + limiter.getRateLimitStatus(client));

        // Exhaust the rate limit
        System.out.println("\n=== Exhausting Rate Limit ===");
        int allowed = 0;
        int denied = 0;
        for (int i = 0; i < 1000; i++) {
            RateLimitResult result = limiter.checkRateLimit(client);
            if (result.allowed) allowed++;
            else denied++;
        }
        System.out.printf("Sent 1000 requests: %d allowed, %d denied%n", allowed, denied);

        // This should be denied
        RateLimitResult result = limiter.checkRateLimit(client);
        System.out.printf("checkRateLimit(\"%s\") -> %s%n", client, result);
        System.out.println("Status: " + limiter.getRateLimitStatus(client));

        // Wait a bit for tokens to refill
        System.out.println("\n=== Waiting 2 seconds for token refill ===");
        Thread.sleep(2000);
        result = limiter.checkRateLimit(client);
        System.out.printf("checkRateLimit(\"%s\") -> %s%n", client, result);

        // Multiple clients
        System.out.println("\n=== Multiple Clients ===");
        String[] clients = {"client_A", "client_B", "client_C"};
        for (String c : clients) {
            for (int i = 0; i < 100; i++) {
                limiter.checkRateLimit(c);
            }
            System.out.println("Status " + c + ": " + limiter.getRateLimitStatus(c));
        }

        // Benchmark
        System.out.println("\n=== Benchmark: 1M rate limit checks ===");
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            limiter.checkRateLimit("bench_client_" + (i % 10_000));
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("1,000,000 checks in %.2f ms (%.3f µs/check)%n",
                elapsed / 1_000_000.0, elapsed / 1_000.0 / 1_000_000);
        System.out.printf("Tracked clients: %d%n", limiter.getTrackedClientCount());
    }
}
