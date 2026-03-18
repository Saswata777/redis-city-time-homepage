package org.example.services;
import java.util.*;


import org.example.model.Store;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisStoreService {
    private final StringRedisTemplate redisTemplate;

    public RedisStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<Store> getStoresByCity(String city) {
        Set<String> storeIds = redisTemplate.opsForSet()
                .members("city:stores:" + city);

        List<Store> stores = new ArrayList<>();

        assert storeIds != null;
        for(String id : storeIds) {

            Map<Object, Object> meta =
                    redisTemplate.opsForHash().entries("store:meta:" + id);

            Map<Object, Object> timing =
                    redisTemplate.opsForHash().entries("store:timing:" + id);

            Store store = new Store(
                    id,
                    (String) meta.get("name"),
                    city,
                    (String) timing.get("open"),
                    (String) timing.get("close"),
                    Integer.parseInt((String) meta.get("priority"))
            );

            stores.add(store);
        }

        return stores;
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