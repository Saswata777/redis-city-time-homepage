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

@Service
public class HomeService {

    private final RedisStoreService redisStoreService;
    private final StringRedisTemplate redisTemplate;

    public HomeService(RedisStoreService redisStoreService,
                       StringRedisTemplate redisTemplate) {
        this.redisStoreService = redisStoreService;
        this.redisTemplate = redisTemplate;
    }

    public List<Store> getHomePage(String city, String time) {

        city = city.toLowerCase();

        String bucket = TimeBucketUtil.getBucket(time);
        String key = RedisKeys.homeCache(city, bucket);
        String lockKey = "lock:" + key;

        // 1️⃣ Check Cache
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            System.out.println("CACHE HIT");
            return JsonUtil.fromJsonList(cached, Store.class);
        }

        System.out.println("CACHE MISS");

        try {
            // 2️⃣ Try acquiring lock
            Boolean isLocked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));

            if (isLocked != null && isLocked) {

                try {
                    // 3️⃣ DOUBLE CHECK (important)
                    cached = redisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        return JsonUtil.fromJsonList(cached, Store.class);
                    }

                    // 4️⃣ Fetch from Redis
                    List<Store> stores = redisStoreService.getStoresByCity(city);

                    // 5️⃣ Filter + Sort
                    List<Store> result = stores.stream()
                            .filter(s -> TimeUtil.isStoreOpen(
                                    s.getOpenTime(),
                                    s.getCloseTime(),
                                    time))
                            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                            .toList();

                    // 6️⃣ Cache result
                    redisTemplate.opsForValue()
                            .set(key, JsonUtil.toJson(result), Duration.ofMinutes(5));

                    return result;

                } finally {
                    // 7️⃣ Always release lock
                    redisTemplate.delete(lockKey);
                }

            } else {
                // Wait and retry (avoid recursion explosion)
                Thread.sleep(50);

                String retryCache = redisTemplate.opsForValue().get(key);
                if (retryCache != null) {
                    return JsonUtil.fromJsonList(retryCache, Store.class);
                }

                // fallback (last attempt)
                return redisStoreService.getStoresByCity(city);
            }

        } catch (Exception e) {
            System.out.println("Redis failure, fallback triggered");

            // fallback logic
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