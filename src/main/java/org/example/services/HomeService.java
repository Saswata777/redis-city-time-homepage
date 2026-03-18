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

        // Step 1: Create Time Bucket
        String bucket = TimeBucketUtil.getBucket(time);

        // Step 2: Generate Redis Key
        String key = RedisKeys.homeCache(city.toLowerCase(), bucket);

        // Step 3: Check Cache
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            System.out.println(" CACHE HIT");
            return JsonUtil.fromJsonList(cached, Store.class);
        }

        System.out.println(" CACHE MISS");

        // Step 4: Fetch All Stores
        List<Store> stores = redisStoreService.getStoresByCity(city);

        //  Step 5: Filter + Sort (your existing logic)
        List<Store> result = stores.stream()
                .filter(s -> TimeUtil.isStoreOpen(
                        s.getOpenTime(),
                        s.getCloseTime(),
                        time))
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .toList();

        //  Step 6: Store in Redis (with TTL)
        redisTemplate.opsForValue()
                .set(key, JsonUtil.toJson(result), Duration.ofMinutes(5));

        return result;
    }
}