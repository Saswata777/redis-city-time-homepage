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
 * Full request lifecycle for GET /home?city=&time=
 *
 * Request flow:
 *  1. Normalise city to lowercase
 *  2. Compute 30-min time bucket  (e.g. 21:47 → 21:30)
 *  3. LOCAL CACHE CHECK (hot cities only — bangalore, delhi)
 *     └─ HIT  → return immediately, zero Redis calls
 *     └─ MISS → continue
 *  4. REDIS CACHE CHECK
 *     └─ HIT  → deserialise, populate local cache, return
 *     └─ MISS → continue
 *  5. ACQUIRE DISTRIBUTED LOCK  (prevents cache stampede)
 *  6. DOUBLE-CHECK Redis
 *  7. FETCH raw stores from RedisStoreService
 *  8. FILTER by open/close time
 *  9. SORT by priority descending
 * 10. WRITE to Redis cache  (TTL: 5 min)
 * 11. WRITE to local cache  (hot cities only, TTL: 30 sec)
 * 12. RELEASE lock
 *
 * ALL Redis operations are inside one try/catch so any Redis failure
 * (including the very first cache-check GET) triggers the graceful fallback.
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

        String bucket  = TimeBucketUtil.getBucket(time);
        String key     = RedisKeys.homeCache(city, bucket);
        String lockKey = "lock:" + key;

        // Step 3: Local cache check (hot cities only)
        if (localCacheService.isHotCity(city)) {
            List<Store> local = localCacheService.get(city, bucket);
            if (local != null) {
                System.out.println("LOCAL CACHE HIT [" + city + ":" + bucket + "]");
                return local;
            }
        }

        // Steps 4–12 are ALL inside one try/catch so any Redis failure
        // (even the very first GET) falls through to the graceful fallback.
        try {

            // Step 4: Redis cache check
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                System.out.println("REDIS CACHE HIT [" + city + ":" + bucket + "]");
                List<Store> result = JsonUtil.fromJsonList(cached, Store.class);
                if (localCacheService.isHotCity(city)) {
                    localCacheService.put(city, bucket, result);
                }
                return result;
            }

            System.out.println("CACHE MISS [" + city + ":" + bucket + "]");

            // Step 5: Acquire distributed lock
            Boolean isLocked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));

            if (isLocked != null && isLocked) {

                try {
                    // Step 6: Double-check after acquiring lock
                    cached = redisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        List<Store> result = JsonUtil.fromJsonList(cached, Store.class);
                        if (localCacheService.isHotCity(city)) {
                            localCacheService.put(city, bucket, result);
                        }
                        return result;
                    }

                    // Steps 7–9: Fetch, filter, sort
                    List<Store> stores = redisStoreService.getStoresByCity(city);
                    List<Store> result = stores.stream()
                            .filter(s -> TimeUtil.isStoreOpen(s.getOpenTime(), s.getCloseTime(), time))
                            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                            .toList();

                    // Step 10: Write to Redis cache
                    redisTemplate.opsForValue()
                            .set(key, JsonUtil.toJson(result), Duration.ofMinutes(5));

                    // Step 11: Write to local cache
                    if (localCacheService.isHotCity(city)) {
                        localCacheService.put(city, bucket, result);
                    }

                    return result;

                } finally {
                    // Step 12: Always release lock
                    redisTemplate.delete(lockKey);
                }

            } else {
                // Another thread holds the lock — wait briefly and retry from cache
                Thread.sleep(50);
                String retryCache = redisTemplate.opsForValue().get(key);
                if (retryCache != null) {
                    List<Store> result = JsonUtil.fromJsonList(retryCache, Store.class);
                    if (localCacheService.isHotCity(city)) {
                        localCacheService.put(city, bucket, result);
                    }
                    return result;
                }
                return redisStoreService.getStoresByCity(city);
            }

        } catch (Exception e) {
            // Redis is down or errored — degrade gracefully, never crash
            System.out.println("Redis failure, fallback triggered: " + e.getMessage());
            List<Store> stores = redisStoreService.getStoresByCity(city);
            return stores.stream()
                    .filter(s -> TimeUtil.isStoreOpen(s.getOpenTime(), s.getCloseTime(), time))
                    .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                    .toList();
        }
    }
}