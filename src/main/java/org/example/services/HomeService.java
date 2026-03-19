package org.example.services;

import org.example.constants.RedisKeys;
import org.example.model.Store;
import org.example.util.JsonUtil;
import org.example.util.TimeBucketUtil;
import org.example.util.TimeUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/*
 * HomeService
 *
 * Handles the full request lifecycle for GET /home?city=&time=
 *
 * ─── Request flow (updated for Phase 5) ──────────────────────────────────────
 *
 *  1. Normalise city to lowercase
 *  2. Compute 30-min time bucket  (e.g. 21:47 → 21:30)
 *  3. LOCAL CACHE CHECK (hot cities only — bangalore, delhi)
 *     └─ HIT  → return immediately, zero Redis calls
 *     └─ MISS → continue to step 4
 *  4. REDIS CACHE CHECK
 *     └─ HIT  → deserialise, populate local cache, return
 *     └─ MISS → continue to step 5
 *  5. ACQUIRE DISTRIBUTED LOCK  (prevents cache stampede)
 *  6. DOUBLE-CHECK Redis  (another thread may have populated it while we waited)
 *  7. FETCH raw store list from Redis  (city:stores set + per-store hashes)
 *  8. FILTER by open/close time  (handles same-day, overnight, 24x7)
 *  9. SORT by priority descending
 * 10. WRITE result to Redis cache  (TTL: 5 min)
 * 11. WRITE result to local cache  (TTL: 30 sec, hot cities only)
 * 12. RELEASE lock
 *
 * On any Redis exception → graceful fallback: compute result without caching.
 *
 * ─── Why two levels of cache? ────────────────────────────────────────────────
 *
 * Redis cache (5 min TTL):
 *   Shared across all app instances. Good for multi-instance deployments.
 *   Survives app restarts. Slightly slower (~1ms network hop per request).
 *
 * Local cache (30 sec TTL):
 *   Per-JVM instance. Zero network hops — pure memory read.
 *   Only for hot cities where thousands of req/min hit the same key.
 *   The short TTL keeps data reasonably fresh without Redis pressure.
 *
 * Together: hot city requests get <0.1ms responses; cold cities use Redis normally.
 */
@Service
public class HomeService {

    private final RedisStoreService redisStoreService;
    private final StringRedisTemplate redisTemplate;
    private final LocalCacheService localCacheService;

    public HomeService(RedisStoreService redisStoreService,
                       StringRedisTemplate redisTemplate,
                       LocalCacheService localCacheService) {
        this.redisStoreService = redisStoreService;
        this.redisTemplate = redisTemplate;
        this.localCacheService = localCacheService;
    }

    public List<Store> getHomePage(String city, String time) {

        city = city.toLowerCase();

        String bucket = TimeBucketUtil.getBucket(time);
        String key    = RedisKeys.homeCache(city, bucket);
        String lockKey = "lock:" + key;

        // ── Step 3: Local cache check (hot cities only) ───────────────────────
        if (localCacheService.isHotCity(city)) {
            List<Store> local = localCacheService.get(city, bucket);
            if (local != null) {
                System.out.println("LOCAL CACHE HIT [" + city + ":" + bucket + "]");
                return local;
            }
        }

        // ── Step 4: Redis cache check ─────────────────────────────────────────
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            System.out.println("REDIS CACHE HIT [" + city + ":" + bucket + "]");
            List<Store> result = JsonUtil.fromJsonList(cached, Store.class);

            // Populate local cache on Redis hit so next requests skip Redis too
            if (localCacheService.isHotCity(city)) {
                localCacheService.put(city, bucket, result);
            }

            return result;
        }

        System.out.println("CACHE MISS [" + city + ":" + bucket + "]");

        try {
            // ── Step 5: Acquire distributed lock ─────────────────────────────
            Boolean isLocked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));

            if (isLocked != null && isLocked) {

                try {
                    // ── Step 6: Double-check Redis after acquiring lock ────────
                    // Another thread may have populated the cache while we waited
                    cached = redisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        List<Store> result = JsonUtil.fromJsonList(cached, Store.class);
                        if (localCacheService.isHotCity(city)) {
                            localCacheService.put(city, bucket, result);
                        }
                        return result;
                    }

                    // ── Step 7: Fetch raw store list from Redis ───────────────
                    List<Store> stores = redisStoreService.getStoresByCity(city);

                    // ── Steps 8 + 9: Filter by time, sort by priority ─────────
                    List<Store> result = stores.stream()
                            .filter(s -> TimeUtil.isStoreOpen(
                                    s.getOpenTime(),
                                    s.getCloseTime(),
                                    time))
                            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                            .toList();

                    // ── Step 10: Write to Redis cache (5 min TTL) ─────────────
                    redisTemplate.opsForValue()
                            .set(key, JsonUtil.toJson(result), Duration.ofMinutes(5));

                    // ── Step 11: Write to local cache (hot cities only) ───────
                    if (localCacheService.isHotCity(city)) {
                        localCacheService.put(city, bucket, result);
                    }

                    return result;

                } finally {
                    // ── Step 12: Always release lock ──────────────────────────
                    redisTemplate.delete(lockKey);
                }

            } else {
                // Lock held by another thread — wait briefly and retry from cache
                Thread.sleep(50);

                String retryCache = redisTemplate.opsForValue().get(key);
                if (retryCache != null) {
                    List<Store> result = JsonUtil.fromJsonList(retryCache, Store.class);
                    if (localCacheService.isHotCity(city)) {
                        localCacheService.put(city, bucket, result);
                    }
                    return result;
                }

                // Last resort — return unfiltered store list without caching
                return redisStoreService.getStoresByCity(city);
            }

        } catch (Exception e) {
            // Redis is down or threw — degrade gracefully, never crash
            System.out.println("Redis failure, fallback triggered: " + e.getMessage());

            List<Store> stores = redisStoreService.getStoresByCity(city);

            return stores.stream()
                    .filter(s -> TimeUtil.isStoreOpen(
                            s.getOpenTime(),
                            s.getCloseTime(),
                            time))
                    .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                    .toList();
        }
    }
}