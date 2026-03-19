package org.example.init;

import org.example.constants.RedisKeys;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
@Component
public class DataSeeder implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;

    public DataSeeder(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) {

        // Skip seeding if data already exists in Redis
        // (handles app restart when Redis is still running)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.cityStores("bangalore")))) {
            System.out.println("[DataSeeder] Redis already has data. Skipping seed.");
            return;
        }

        System.out.println("[DataSeeder] Seeding store data into Redis...");

        List<StoreData> stores = buildStoreData();

        for (StoreData store : stores) {

            // 1. Add store ID to the city's set
            redisTemplate.opsForSet().add(
                    RedisKeys.cityStores(store.city),
                    store.id
            );

            // 2. Store metadata (name + priority) as a Hash
            redisTemplate.opsForHash().putAll(
                    RedisKeys.storeMeta(store.id),
                    Map.of(
                            "name", store.name,
                            "priority", String.valueOf(store.priority)
                    )
            );

            // 3. Store timing (open + close) as a Hash
            redisTemplate.opsForHash().putAll(
                    RedisKeys.storeTiming(store.id),
                    Map.of(
                            "open", store.openTime,
                            "close", store.closeTime
                    )
            );
        }

        System.out.println("[DataSeeder] Seeded " + stores.size() + " stores across 5 cities.");
    }

    /*
     * buildStoreData()
     *
     * Returns the full store catalogue.
     * Covers all 5 cities from the README, and all three timing types:
     *   - same-day (open < close)
     *   - overnight (open > close, e.g. a late-night food joint)
     *   - 24x7 (open == close == "00:00")
     *
     * Priority: higher number = shown first in the home page response.
     * (HomeService sorts by priority descending.)
     */
    private List<StoreData> buildStoreData() {
        return List.of(

                // ── BANGALORE (high traffic city) ──────────────────────────
                new StoreData("BLR001", "Swiggy Instamart",       "bangalore", "00:00", "00:00", 10),  // 24x7
                new StoreData("BLR002", "Zomato",                 "bangalore", "08:00", "23:00", 9),   // same-day
                new StoreData("BLR003", "BigBasket",              "bangalore", "07:00", "22:00", 8),   // same-day
                new StoreData("BLR004", "Blinkit",                "bangalore", "06:00", "00:00", 7),   // same-day (late close)
                new StoreData("BLR005", "MTR Restaurant",         "bangalore", "07:30", "22:30", 6),   // same-day
                new StoreData("BLR006", "Burger King",            "bangalore", "10:00", "02:00", 5),   // overnight
                new StoreData("BLR007", "24x7 Medical Store",     "bangalore", "00:00", "00:00", 4),   // 24x7

                // ── DELHI (high traffic city) ───────────────────────────────
                new StoreData("DEL001", "Zepto",                  "delhi",     "00:00", "00:00", 10),  // 24x7
                new StoreData("DEL002", "Zomato",                 "delhi",     "09:00", "23:30", 9),   // same-day
                new StoreData("DEL003", "DMart",                  "delhi",     "08:00", "22:00", 8),   // same-day
                new StoreData("DEL004", "Haldiram's",             "delhi",     "08:00", "23:00", 7),   // same-day
                new StoreData("DEL005", "Mcdonald's",             "delhi",     "09:00", "03:00", 6),   // overnight
                new StoreData("DEL006", "Night Pharmacy",         "delhi",     "21:00", "09:00", 5),   // overnight

                // ── MUMBAI ──────────────────────────────────────────────────
                new StoreData("MUM001", "Blinkit",                "mumbai",    "00:00", "00:00", 10),  // 24x7
                new StoreData("MUM002", "Swiggy",                 "mumbai",    "08:00", "01:00", 9),   // overnight
                new StoreData("MUM003", "Reliance Smart",         "mumbai",    "09:00", "21:00", 8),   // same-day
                new StoreData("MUM004", "Vada Pav Corner",        "mumbai",    "07:00", "23:00", 6),   // same-day
                new StoreData("MUM005", "Cafe Coffee Day",        "mumbai",    "07:00", "22:00", 5),   // same-day

                // ── CHENNAI ─────────────────────────────────────────────────
                new StoreData("CHN001", "Aavin Milk",             "chennai",   "05:00", "09:00", 9),   // same-day (morning only)
                new StoreData("CHN002", "Swiggy",                 "chennai",   "09:00", "23:00", 8),   // same-day
                new StoreData("CHN003", "Saravana Stores",        "chennai",   "10:00", "21:00", 7),   // same-day
                new StoreData("CHN004", "Apollo Pharmacy",        "chennai",   "00:00", "00:00", 10),  // 24x7
                new StoreData("CHN005", "Murugan Idli Shop",      "chennai",   "06:00", "22:00", 6),   // same-day

                // ── HYDERABAD ────────────────────────────────────────────────
                new StoreData("HYD001", "Zepto",                  "hyderabad", "00:00", "00:00", 10),  // 24x7
                new StoreData("HYD002", "Paradise Biryani",       "hyderabad", "11:00", "23:30", 9),   // same-day
                new StoreData("HYD003", "Big Bazaar",             "hyderabad", "09:00", "21:30", 8),   // same-day
                new StoreData("HYD004", "Zomato",                 "hyderabad", "09:00", "00:00", 7),   // same-day (midnight close)
                new StoreData("HYD005", "Night Dhaba",            "hyderabad", "20:00", "06:00", 5)    // overnight
        );
    }

    /*
     * StoreData
     *
     * A simple internal record to hold one store's full data before
     * writing to Redis. Using a record keeps buildStoreData() readable.
     *
     * Not the same as the public Store model — this is seeder-internal only.
     */
    private record StoreData(
            String id,
            String name,
            String city,
            String openTime,
            String closeTime,
            int priority
    ) {}
}
