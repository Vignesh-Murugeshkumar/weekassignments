import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Problem 10: Multi-Level Cache System with Hash Tables
 *
 * Concepts: Multiple hash tables (L1, L2, L3), LRU eviction via LinkedHashMap,
 *           access-count-based promotion, cache invalidation, hit ratio tracking.
 */
public class Problem10_MultiLevelCache {

    /**
     * Represents video data stored in cache.
     */
    static class VideoData {
        final String videoId;
        final String title;
        final int sizeKB;

        VideoData(String videoId, String title, int sizeKB) {
            this.videoId = videoId;
            this.title = title;
            this.sizeKB = sizeKB;
        }
    }

    /**
     * Result of a cache lookup.
     */
    static class CacheResult {
        final VideoData data;
        final String level; // L1, L2, L3
        final double lookupTimeMs;
        final String action; // HIT, MISS+promoted, etc.

        CacheResult(VideoData data, String level, double lookupTimeMs, String action) {
            this.data = data;
            this.level = level;
            this.lookupTimeMs = lookupTimeMs;
            this.action = action;
        }

        @Override
        public String toString() {
            if (data == null) {
                return String.format("[NOT FOUND] (%.2fms)", lookupTimeMs);
            }
            return String.format("[%s %s] \"%s\" (%.2fms)", level, action, data.title, lookupTimeMs);
        }
    }

    /**
     * L1 Cache: In-memory LRU cache using LinkedHashMap (access order).
     */
    private final LinkedHashMap<String, VideoData> l1Cache;
    private final int l1Capacity;

    /**
     * L2 Cache: Simulated SSD-backed cache (also in-memory for demo, but with artificial latency).
     */
    private final LinkedHashMap<String, VideoData> l2Cache;
    private final int l2Capacity;

    /**
     * L3: Database (all videos). Simulated with a HashMap + artificial latency.
     */
    private final Map<String, VideoData> database = new HashMap<>();

    /**
     * Access count per video (for promotion decisions).
     */
    private final Map<String, AtomicLong> accessCounts = new HashMap<>();

    /**
     * Promotion threshold: if a video in L2 is accessed this many times, promote to L1.
     */
    private final int promotionThreshold;

    // Metrics per level
    private long l1Hits = 0, l1Misses = 0;
    private long l2Hits = 0, l2Misses = 0;
    private long l3Hits = 0, l3Misses = 0;
    private double l1TotalTime = 0, l2TotalTime = 0, l3TotalTime = 0;

    public Problem10_MultiLevelCache(int l1Capacity, int l2Capacity, int promotionThreshold) {
        this.l1Capacity = l1Capacity;
        this.l2Capacity = l2Capacity;
        this.promotionThreshold = promotionThreshold;

        // LRU LinkedHashMap: access-order=true, auto-evict eldest
        this.l1Cache = new LinkedHashMap<>(l1Capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, VideoData> eldest) {
                if (size() > l1Capacity) {
                    // Demote to L2 on eviction
                    demoteToL2(eldest.getKey(), eldest.getValue());
                    return true;
                }
                return false;
            }
        };

        this.l2Cache = new LinkedHashMap<>(l2Capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, VideoData> eldest) {
                return size() > l2Capacity;
            }
        };
    }

    /**
     * Add a video to the database (L3).
     */
    public void addToDatabase(String videoId, String title, int sizeKB) {
        database.put(videoId, new VideoData(videoId, title, sizeKB));
    }

    /**
     * Get a video, checking L1 -> L2 -> L3 in order.
     */
    public CacheResult getVideo(String videoId) {
        double totalTime = 0;

        // ---- L1: In-memory lookup (~0.5ms simulated) ----
        double l1Time = simulateLatency(0.5);
        totalTime += l1Time;

        VideoData data = l1Cache.get(videoId);
        if (data != null) {
            l1Hits++;
            l1TotalTime += l1Time;
            incrementAccess(videoId);
            return new CacheResult(data, "L1", totalTime, "HIT");
        }
        l1Misses++;

        // ---- L2: SSD lookup (~5ms simulated) ----
        double l2Time = simulateLatency(5.0);
        totalTime += l2Time;

        data = l2Cache.get(videoId);
        if (data != null) {
            l2Hits++;
            l2TotalTime += l2Time;
            long count = incrementAccess(videoId);

            // Promote to L1 if accessed enough
            if (count >= promotionThreshold) {
                l2Cache.remove(videoId);
                l1Cache.put(videoId, data);
                return new CacheResult(data, "L2", totalTime, "HIT -> Promoted to L1");
            }
            return new CacheResult(data, "L2", totalTime, "HIT");
        }
        l2Misses++;

        // ---- L3: Database lookup (~150ms simulated) ----
        double l3Time = simulateLatency(150.0);
        totalTime += l3Time;

        data = database.get(videoId);
        if (data != null) {
            l3Hits++;
            l3TotalTime += l3Time;
            incrementAccess(videoId);

            // Add to L2 (will eventually promote to L1 on repeated access)
            l2Cache.put(videoId, data);
            return new CacheResult(data, "L3", totalTime, "HIT -> Added to L2");
        }
        l3Misses++;

        return new CacheResult(null, "NONE", totalTime, "NOT FOUND");
    }

    /**
     * Invalidate a video across all cache levels (e.g., content was updated).
     */
    public void invalidate(String videoId) {
        l1Cache.remove(videoId);
        l2Cache.remove(videoId);
        accessCounts.remove(videoId);
    }

    /**
     * Invalidate all caches.
     */
    public void invalidateAll() {
        l1Cache.clear();
        l2Cache.clear();
        accessCounts.clear();
    }

    private void demoteToL2(String videoId, VideoData data) {
        l2Cache.put(videoId, data);
    }

    private long incrementAccess(String videoId) {
        return accessCounts.computeIfAbsent(videoId, k -> new AtomicLong(0)).incrementAndGet();
    }

    private double simulateLatency(double targetMs) {
        // Simulate latency with busy-wait for accuracy (real system would have actual I/O)
        long endNano = System.nanoTime() + (long) (targetMs * 1_000);
        while (System.nanoTime() < endNano) {
            // busy wait (for sub-ms precision)
        }
        return targetMs;
    }

    /**
     * Get comprehensive cache statistics.
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();

        long totalL1 = l1Hits + l1Misses;
        long totalL2 = l2Hits + l2Misses;
        long totalL3 = l3Hits + l3Misses;
        long totalRequests = l1Hits + l2Hits + l3Hits + l3Misses;
        long totalHits = l1Hits + l2Hits + l3Hits;

        sb.append("=== Cache Statistics ===\n");
        sb.append(String.format("  L1 (Memory):   %,5d/%,d entries | Hits: %,d | Miss: %,d | Hit Rate: %.1f%% | Avg: %.1fms%n",
                l1Cache.size(), l1Capacity, l1Hits, l1Misses,
                totalL1 > 0 ? l1Hits * 100.0 / totalL1 : 0,
                l1Hits > 0 ? l1TotalTime / l1Hits : 0));
        sb.append(String.format("  L2 (SSD):      %,5d/%,d entries | Hits: %,d | Miss: %,d | Hit Rate: %.1f%% | Avg: %.1fms%n",
                l2Cache.size(), l2Capacity, l2Hits, l2Misses,
                totalL2 > 0 ? l2Hits * 100.0 / totalL2 : 0,
                l2Hits > 0 ? l2TotalTime / l2Hits : 0));
        sb.append(String.format("  L3 (Database): %,5d entries     | Hits: %,d | Miss: %,d | Hit Rate: %.1f%% | Avg: %.1fms%n",
                database.size(), l3Hits, l3Misses,
                totalL3 > 0 ? l3Hits * 100.0 / totalL3 : 0,
                l3Hits > 0 ? l3TotalTime / l3Hits : 0));
        sb.append(String.format("  Overall: Total Requests: %,d, Hit Rate: %.1f%%",
                totalRequests, totalRequests > 0 ? totalHits * 100.0 / totalRequests : 0));

        return sb.toString();
    }

    // ======================== DEMO ========================
    public static void main(String[] args) {
        // L1: 5 videos, L2: 20 videos, promote after 3 accesses
        Problem10_MultiLevelCache cache = new Problem10_MultiLevelCache(5, 20, 3);

        System.out.println("=== Multi-Level Cache System ===");
        System.out.println("L1: 5 slots (memory) | L2: 20 slots (SSD) | Promotion threshold: 3 accesses\n");

        // Populate database (L3)
        for (int i = 1; i <= 50; i++) {
            cache.addToDatabase("video_" + i, "Video Title " + i, 1024 * (1 + i % 5));
        }
        System.out.println("Database loaded: 50 videos\n");

        // First access: L3 -> L2
        System.out.println("=== First Access (Cold Cache) ===");
        System.out.println("getVideo(\"video_1\")  -> " + cache.getVideo("video_1"));
        System.out.println("getVideo(\"video_2\")  -> " + cache.getVideo("video_2"));
        System.out.println("getVideo(\"video_3\")  -> " + cache.getVideo("video_3"));

        // Second access: L2 HIT
        System.out.println("\n=== Second Access (L2 Hit) ===");
        System.out.println("getVideo(\"video_1\")  -> " + cache.getVideo("video_1"));
        System.out.println("getVideo(\"video_1\")  -> " + cache.getVideo("video_1"));

        // Third access: should promote to L1
        System.out.println("\n=== Third+ Access (Promotion to L1) ===");
        System.out.println("getVideo(\"video_1\")  -> " + cache.getVideo("video_1"));

        // Now video_1 is in L1
        System.out.println("\n=== L1 Hit ===");
        System.out.println("getVideo(\"video_1\")  -> " + cache.getVideo("video_1"));

        // Non-existent video
        System.out.println("\n=== Cache Miss (Not in DB) ===");
        System.out.println("getVideo(\"video_999\") -> " + cache.getVideo("video_999"));

        // Cache invalidation
        System.out.println("\n=== Cache Invalidation ===");
        cache.invalidate("video_1");
        System.out.println("Invalidated video_1");
        System.out.println("getVideo(\"video_1\")  -> " + cache.getVideo("video_1"));

        // Simulate realistic workload
        System.out.println("\n=== Simulated Workload: 200 requests ===");
        Random random = new Random(42);
        cache.invalidateAll();
        // Reset stats by creating new cache
        Problem10_MultiLevelCache workloadCache = new Problem10_MultiLevelCache(5, 20, 3);
        for (int i = 1; i <= 50; i++) {
            workloadCache.addToDatabase("video_" + i, "Video Title " + i, 1024);
        }

        // Zipf-like distribution: popular videos accessed much more
        for (int i = 0; i < 200; i++) {
            // Skewed: lower video IDs are more popular
            int videoNum = 1 + (int) (Math.pow(random.nextDouble(), 2) * 50);
            videoNum = Math.min(videoNum, 50);
            workloadCache.getVideo("video_" + videoNum);
        }

        System.out.println(workloadCache.getStatistics());
    }
}
