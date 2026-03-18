package org.example.constants;

public class RedisKeys {
    public static String cityStores(String city){
        return "city:stores:" + city;
    }

    public static String storeMeta(String id){
        return "store:meta:" + id;
    }

    public static String storeTiming(String id){
        return "store:timing:" + id;
    }

    public static String homeCache(String city, String bucket){
        return "home:" + city + ":" + bucket;
    }
}


/*
 * RedisKeys
 *
 * Purpose:
 * This class is responsible for generating standardized Redis keys
 * used across the application.
 *
 * Why do we need this?
 * Instead of hardcoding Redis keys in multiple places, we centralize
 * key generation logic in one class to ensure:
 *   - Consistency
 *   - Maintainability
 *   - Readability
 *
 * Key Design Strategy:
 * We use a structured naming convention:
 *
 *   <entity>:<type>:<identifier>
 *
 * Examples:
 *   city:stores:kolkata     → List of store IDs in a city
 *   store:meta:101          → Metadata of a store (name, category, etc.)
 *   store:timing:101        → Opening/closing time of a store
 *   home:kolkata:10:00      → Cached homepage response for a city and time bucket
 *
 * Why is this important?
 * - Avoids key collisions
 * - Makes debugging easy (using Redis CLI)
 * - Helps scale the system cleanly
 *
 * Optimization Insight:
 * The "homeCache" key combines:
 *   - city
 *   - time bucket (from TimeBucketUtil)
 *
 * This ensures:
 *   - Fewer cache entries
 *   - Higher cache hit rate
 *   - Better performance
 *
 * System Flow:
 *   User Request → TimeBucketUtil → RedisKeys → Redis → Response
 *
 * This approach is widely used in large-scale systems to maintain
 * clean and efficient cache structures.
 */
