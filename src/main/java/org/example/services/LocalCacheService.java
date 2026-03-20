package org.example.services;

import org.example.model.Store;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * LocalCacheService
 *
 * ─── What problem does this solve? ───────────────────────────────────────────
 *
 * The README states:
 *   "Bangalore and Delhi receive the majority of requests —
 *    thousands of requests per minute."
 *
 * Without this class, every one of those requests hits Redis for the same
 * cache key (e.g. home:bangalore:21:30). Even though Redis is fast (~1ms),
 * that's still thousands of network round trips per minute to a single key.
 * This is the "hot key" problem — one key gets hammered so hard it becomes
 * a bottleneck.
 *
 * ─── How does this solve it? ─────────────────────────────────────────────────
 *
 * We add a tiny in-process (JVM heap) cache in front of Redis specifically
 * for high-traffic cities. The flow becomes:
 *
 *   Request → LocalCacheService (JVM) → [hit] → return instantly (no Redis)
 *                                      → [miss] → Redis → store in local cache
 *
 * For Bangalore at 21:30, the first request per 30 seconds goes to Redis.
 * Every other request in that 30-second window is served from memory —
 * zero network hops, sub-millisecond latency.
 *
 * ─── Why 30 seconds TTL? ─────────────────────────────────────────────────────
 *
 * The Redis cache TTL is 5 minutes (set in HomeService).
 * The local cache TTL is 30 seconds — shorter on purpose.
 *
 * Trade-off:
 *   - Shorter TTL = more up-to-date data, more Redis hits
 *   - Longer TTL  = staler data, fewer Redis hits
 *
 * 30 seconds is a good balance for a home page: users won't notice a
 * 30-second delay in a store appearing/disappearing, but the data won't
 * feel stale either.
 *
 * ─── Why only hot cities? ────────────────────────────────────────────────────
 *
 * Small cities (Chennai, Hyderabad) get very few requests. Caching them
 * locally wastes JVM heap and gains almost nothing. We only pay the memory
 * cost where the benefit is real.
 *
 * ─── Why ConcurrentHashMap? ──────────────────────────────────────────────────
 *
 * Spring Boot handles requests on multiple threads simultaneously. A regular
 * HashMap is not thread-safe — concurrent reads/writes will corrupt it.
 * ConcurrentHashMap gives us thread-safe access with minimal locking overhead.
 *
 * ─── Data structure ──────────────────────────────────────────────────────────
 *
 *   cache key  : "{city}:{timeBucket}"  e.g. "bangalore:21:30"
 *   cache value: CacheEntry { stores, expiresAt }
 *
 * We store the full computed result (already filtered + sorted) so that
 * a cache hit returns immediately without any reprocessing.
 */
@Service
public class LocalCacheService {

    @Value("${cache.local.ttl-ms}")
    private long TTL_MS;

    // Cities that get local caching — high traffic only
    private static final Set<String> HOT_CITIES = Set.of("bangalore", "delhi");

    // Thread-safe in-process store: key → CacheEntry

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /*
     * isHotCity()
     * Returns true if this city should use local caching.
     * Called by HomeService before deciding whether to check local cache.
     */
    public boolean isHotCity(String city) {
        return HOT_CITIES.contains(city);
    }

    /*
     * get()
     * Returns the cached store list for this city+bucket, or null if:
     *   - the key was never cached
     *   - the entry has expired (older than TTL_MS)
     *
     * Expired entries are lazily removed here — no background cleanup thread needed.
     */
    public List<Store> get(String city, String bucket) {
        String key = buildKey(city, bucket);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            return null; // never cached
        }

        if (System.currentTimeMillis() > entry.expiresAt()) {
            cache.remove(key); // lazy eviction
            return null;       // treat as miss
        }

        return entry.stores();
    }

    /*
     * put()
     * Stores the result for city+bucket with a fresh TTL.
     * Only called after a Redis hit or computed result — we never cache empty lists.
     */
    public void put(String city, String bucket, List<Store> stores) {
        if (stores == null || stores.isEmpty()) {
            return; // don't cache empty results — city data may just be missing
        }
        String key = buildKey(city, bucket);
        long expiresAt = System.currentTimeMillis() + TTL_MS;
        cache.put(key, new CacheEntry(stores, expiresAt));
    }

    /*
     * invalidate()
     * Removes a specific city+bucket entry from local cache.
     * Useful if you need to force a refresh (e.g. store data updated).
     */
    public void invalidate(String city, String bucket) {
        cache.remove(buildKey(city, bucket));
    }

    private String buildKey(String city, String bucket) {
        return city + ":" + bucket;
    }

    /*
     * CacheEntry — immutable record holding the result and its expiry timestamp.
     *
     * Using a record (Java 16+) keeps this clean — no boilerplate getters needed.
     * expiresAt is an epoch millisecond timestamp, compared against System.currentTimeMillis().
     */
    private record CacheEntry(List<Store> stores, long expiresAt) {}
}