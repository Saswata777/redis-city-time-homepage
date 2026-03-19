package org.example.services;

import org.example.constants.RedisKeys;
import org.example.model.Store;
import org.example.repository.StoreRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/*
 * RedisStoreService — Phase 6
 *
 * Two improvements over Phase 5:
 *
 * 1. PIPELINING
 *    N stores used to require N×2 serial Redis calls.
 *    executePipelined() batches all HGETALL commands into one round trip.
 *    Results come back as a List<Object> indexed the same way we issued the calls.
 *
 * 2. EVICTION-SAFE REBUILD
 *    If city:stores:{city} is empty (evicted under LFU pressure),
 *    we re-seed just that city from StoreRepository, then retry once.
 *    The first request after eviction pays a small extra write cost;
 *    all subsequent requests work normally.
 */
@Service
public class RedisStoreService {

    private final StringRedisTemplate redisTemplate;
    private final StoreRepository storeRepository;

    public RedisStoreService(StringRedisTemplate redisTemplate,
                             StoreRepository storeRepository) {
        this.redisTemplate = redisTemplate;
        this.storeRepository = storeRepository;
    }

    public List<Store> getStoresByCity(String city) {

        Set<String> storeIds = redisTemplate.opsForSet()
                .members(RedisKeys.cityStores(city));

        // Eviction detected — rebuild this city's data then retry once
        if (storeIds == null || storeIds.isEmpty()) {
            System.out.println("[RedisStoreService] city:stores:" + city
                    + " missing — rebuilding from catalogue...");
            reseedCity(city);

            storeIds = redisTemplate.opsForSet().members(RedisKeys.cityStores(city));

            if (storeIds == null || storeIds.isEmpty()) {
                System.out.println("[RedisStoreService] Rebuild failed for " + city + ", returning empty.");
                return List.of();
            }
        }

        return fetchStoresViaPipeline(city, new ArrayList<>(storeIds));
    }

    /*
     * fetchStoresViaPipeline()
     *
     * Pipeline layout for N stores (ordered by storeIds index i):
     *   results[0]     → HGETALL store:meta:storeIds[0]
     *   results[1]     → HGETALL store:meta:storeIds[1]
     *   ...
     *   results[N-1]   → HGETALL store:meta:storeIds[N-1]
     *   results[N]     → HGETALL store:timing:storeIds[0]
     *   results[N+1]   → HGETALL store:timing:storeIds[1]
     *   ...
     *   results[2N-1]  → HGETALL store:timing:storeIds[N-1]
     *
     * So for store at index i:
     *   meta   = results[i]
     *   timing = results[i + N]
     */
    @SuppressWarnings("unchecked")
    private List<Store> fetchStoresViaPipeline(String city, List<String> storeIds) {

        List<Object> results = redisTemplate.executePipelined(
                new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) throws DataAccessException {
                        for (String id : storeIds) {
                            operations.opsForHash().entries(RedisKeys.storeMeta(id));
                        }
                        for (String id : storeIds) {
                            operations.opsForHash().entries(RedisKeys.storeTiming(id));
                        }
                        return null; // ignored — results come from executePipelined return value
                    }
                }
        );

        int n = storeIds.size();
        List<Store> stores = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Map<Object, Object> meta   = (Map<Object, Object>) results.get(i);
            Map<Object, Object> timing = (Map<Object, Object>) results.get(i + n);

            if (meta == null || meta.isEmpty() || timing == null || timing.isEmpty()) {
                System.out.println("[RedisStoreService] Skipping store "
                        + storeIds.get(i) + " — evicted mid-pipeline");
                continue;
            }

            try {
                stores.add(new Store(
                        storeIds.get(i),
                        (String) meta.get("name"),
                        city,
                        (String) timing.get("open"),
                        (String) timing.get("close"),
                        Integer.parseInt((String) meta.get("priority"))
                ));
            } catch (Exception e) {
                System.out.println("[RedisStoreService] Failed to parse store "
                        + storeIds.get(i) + ": " + e.getMessage());
            }
        }

        return stores;
    }

    /*
     * reseedCity()
     * Re-writes all Redis keys for a single city from the StoreRepository catalogue.
     * Called only when eviction is detected — targeted write, not full re-seed.
     */
    private void reseedCity(String city) {
        List<StoreRepository.StoreRecord> cityStores = storeRepository.findByCity(city);

        if (cityStores.isEmpty()) {
            System.out.println("[RedisStoreService] No catalogue data for: " + city);
            return;
        }

        for (StoreRepository.StoreRecord store : cityStores) {
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

        System.out.println("[RedisStoreService] Rebuilt " + cityStores.size()
                + " stores for: " + city);
    }
}

/*
 * RedisStoreService
 *
 * Purpose:
 * This service interacts with Redis to fetch store-related data
 * and convert it into application-level objects.
 *
 * Data Modeling in Redis:
 * - city:stores:<city> → Set of store IDs
 * - store:meta:<id>    → Hash (name, priority, etc.)
 * - store:timing:<id>  → Hash (open, close time)
 *
 * Flow:
 * 1. Fetch all store IDs for a given city
 * 2. For each store ID:
 *    - Fetch metadata
 *    - Fetch timing information
 * 3. Combine the data and construct Store objects
 * 4. Return the list of stores
 *
 * Why this design?
 * - Separates data logically (city → store → details)
 * - Avoids duplication
 * - Makes data easy to update independently
 *
 * Benefits:
 * - Fast lookups using Redis
 * - Scalable structure
 * - Flexible for adding new fields
 *
 * Note:
 * - Each store requires multiple Redis calls (meta + timing)
 * - Can be optimized later using pipelining or combined storage
 */