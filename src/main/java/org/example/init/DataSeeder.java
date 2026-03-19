/*
 * DataSeeder
 *
 * Purpose:
 * Loads all store data into Redis on application startup.
 *
 * Why do we need this?
 * Redis is an in-memory store — it starts empty on every fresh run
 * or restart. Without seeding, city:stores:*, store:meta:*, and
 * store:timing:* keys don't exist, so every API call returns [].
 *
 * Why CommandLineRunner?
 * Spring Boot calls run() automatically after the application context
 * is fully ready — beans are wired, Redis connection is live. It's the
 * cleanest hook for one-time startup work.
 *
 * Why check before seeding?
 * If the app restarts but Redis still has data (e.g. Redis didn't restart),
 * we skip re-seeding to avoid unnecessary Redis writes. This makes restarts
 * fast and idempotent.
 *
 * Data Design:
 * Each store is broken into two Redis Hashes:
 *   store:meta:{id}   → name, priority
 *   store:timing:{id} → open, close
 *
 * And one Redis Set per city:
 *   city:stores:{city} → { id1, id2, id3, ... }
 *
 * This matches the data model expected by RedisStoreService exactly.
 *
 * Store timing coverage (per README requirements):
 *   - Same-day stores  : open_time < close_time  e.g. 09:00 – 21:00
 *   - Overnight stores : open_time > close_time  e.g. 22:00 – 04:00
 *   - 24x7 stores      : open_time == close_time  e.g. 00:00 – 00:00
 */
package org.example.init;

import org.example.constants.RedisKeys;
import org.example.repository.StoreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/*
 * DataSeeder
 *
 * Seeds all store data into Redis on application startup.
 *
 * Phase 6 change: catalogue moved to StoreRepository (shared with RedisStoreService).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;
    private final StoreRepository storeRepository;

    public DataSeeder(StringRedisTemplate redisTemplate,
                      StoreRepository storeRepository) {
        this.redisTemplate = redisTemplate;
        this.storeRepository = storeRepository;
    }

    @Override
    public void run(String... args) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.cityStores("bangalore")))) {
                System.out.println("[DataSeeder] Redis already has data. Skipping seed.");
                return;
            }

            System.out.println("[DataSeeder] Seeding store data into Redis...");

            List<StoreRepository.StoreRecord> stores = storeRepository.findAll();

            for (StoreRepository.StoreRecord store : stores) {
                redisTemplate.opsForSet().add(
                        RedisKeys.cityStores(store.city()), store.id());

                redisTemplate.opsForHash().putAll(
                        RedisKeys.storeMeta(store.id()),
                        Map.of("name", store.name(),
                                "priority", String.valueOf(store.priority())));

                redisTemplate.opsForHash().putAll(
                        RedisKeys.storeTiming(store.id()),
                        Map.of("open", store.openTime(),
                                "close", store.closeTime()));
            }

            System.out.println("[DataSeeder] Seeded " + stores.size() + " stores across 5 cities.");

        } catch (Exception e) {
            System.err.println("[DataSeeder] WARNING: Could not seed Redis — " + e.getMessage());
            System.err.println("[DataSeeder] App will start but responses may be empty.");
        }
    }
}