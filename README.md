# City & Time Aware Home Page System (Redis Learning Project)

## Problem Statement
Design and implement a *Redis-centric backend system* that serves a *Home Page response* based on a user's *city* and *current time*.

The system should return a list of stores available in the given city at the given time, while efficiently handling caching, eviction, uneven traffic across cities, and Redis failure scenarios.

---

## Input
The system receives:
- city (string): Name of the city (e.g., bangalore, delhi, mumbai)
- time (HH:MM): Local time of the city

Example inputs:
- city = bangalore, time = 21:30
- city = delhi, time = 09:15
- city = mumbai, time = 02:00

---

## Output
A list of stores available in the given city at the given time, sorted by priority.

---

## Store Data
Each store has the following attributes:
- store_id
- name
- city
- open_time
- close_time
- priority

---

## Store Timing Rules
- Same-day stores (e.g., 09:00–21:00)
- Overnight stores (e.g., 22:00–04:00)
- 24×7 stores

---

## Cities Supported
The system must support multiple cities, including but not limited to:
- Bangalore
- Delhi
- Mumbai
- Chennai
- Hyderabad

Traffic is *highly skewed*:
- Bangalore and Delhi receive the majority of requests
- Smaller cities receive very few requests

---

## Core Use Cases
1. A user opens the home page in Bangalore at 8:30 PM and sees only stores open at that time.
2. The same user refreshes at 10:00 PM and sees updated stores due to closures.
3. A user in Mumbai at 2:00 AM sees overnight or 24×7 stores.
4. Bangalore receives thousands of requests per minute without overwhelming Redis.
5. Low-traffic cities may lose cached data due to eviction and must rebuild safely.
6. A store closes early and the home page reflects the change without long-lived stale data.

---

## Redis Usage Requirements
Redis is the primary in-memory system and must be used to:
- Map cities to stores
- Store store timing and metadata
- Cache computed home page results
- Handle high read traffic efficiently

---

## Redis Data Modeling (Indicative)
- city:stores:{city} → set of store_ids
- store:timing:{store_id} → open_time, close_time
- store:meta:{store_id} → name, priority
- home:{city}:{time_bucket} → cached home page result

---

## Caching Strategy
- Home page responses are cached using city and time buckets
- Time must be bucketed (e.g., 30-minute intervals) to avoid unbounded key growth
- Cached data must have a TTL

---

## Redis Constraints
- Redis memory is limited
- Eviction policy is enabled (e.g., LRU or LFU)
- Cached keys may be evicted at any time

---

## Failure & Edge Cases
The system must handle:
- Redis key eviction
- Redis restarts (all cache lost)
- Concurrent requests for the same city and time
- Overnight store timing logic
- Cache stampedes
- Hot city keys under heavy load

---

## Non-Functional Requirements
- Cached response latency should be low (< 50 ms)
- The system must degrade gracefully under Redis failures
- The system must not crash due to missing cache data

---

## Out of Scope
- User authentication
- Orders and payments
- Geo-location or distance-based logic
- Recommendation or ML systems

---

## Learning Objectives
This project is designed to build hands-on understanding of:
- Redis data structures
- Cache key design and TTLs
- Eviction-aware system behavior
- Hot-key mitigation
- Time-based backend logic
- Real-world Redis trade-offs

---

## Success Criteria
A successful solution:
- Correctly filters stores by city and time
- Scales better for high-traffic cities
- Recovers safely from eviction and restart
- Clearly documents Redis design decisions

---

---

# Implementation

## How to Run

**Prerequisites:** Docker and Docker Compose installed.

```bash
# Clone the repository
git clone https://github.com/Saswata777/redis-city-time-homepage.git
cd redis-city-time-homepage

# Start the app and Redis together
docker-compose up --build
```

The app starts on `http://localhost:8080`. Redis starts on port `6379`. Store data is seeded automatically on first startup.

### API

```
GET /home?city={city}&time={HH:MM}
```

**Examples:**

```bash
# Bangalore evening — returns same-day + 24x7 stores sorted by priority
curl "http://localhost:8080/home?city=bangalore&time=21:30"

# Mumbai 2 AM — returns only 24x7 stores (overnight stores checked correctly)
curl "http://localhost:8080/home?city=mumbai&time=02:00"

# Delhi early morning — overnight stores (Night Pharmacy 21:00-09:00) appear
curl "http://localhost:8080/home?city=delhi&time=08:00"

# Missing parameter — returns clean 400 JSON error
curl "http://localhost:8080/home?city=bangalore"

# Invalid time format — returns clean 400 JSON error
curl "http://localhost:8080/home?city=bangalore&time=99:99"
```

**Sample response:**
```json
[
  { "id": "BLR001", "name": "Swiggy Instamart", "city": "bangalore", "openTime": "00:00", "closeTime": "00:00", "priority": 10 },
  { "id": "BLR002", "name": "Zomato",           "city": "bangalore", "openTime": "08:00", "closeTime": "23:00", "priority": 9  }
]
```

### Running Tests

Tests run automatically during Docker build. To run them separately, use the Maven wrapper:

```bash
# Generate the wrapper once (requires Docker)
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.6-eclipse-temurin-21 mvn wrapper:wrapper

# Run all tests
.\mvnw.cmd test
```

---

## Project Structure

```
src/main/java/org/example/
├── controller/
│   ├── HomeController.java          # GET /home endpoint
│   └── GlobalExceptionHandler.java  # Clean 400/500 JSON error responses
├── services/
│   ├── HomeService.java             # Core request flow: cache → lock → fetch → filter → sort
│   ├── RedisStoreService.java       # Fetches stores from Redis using pipelining
│   └── LocalCacheService.java       # In-process JVM cache for hot cities
├── repository/
│   └── StoreRepository.java         # Single source of truth for store catalogue
├── init/
│   └── DataSeeder.java              # Seeds Redis with store data on startup
├── constants/
│   └── RedisKeys.java               # Centralised Redis key generation
├── model/
│   └── Store.java                   # Store data model
└── util/
    ├── TimeUtil.java                 # Open/close time logic (same-day, overnight, 24x7)
    ├── TimeBucketUtil.java           # Snaps time to 30-minute buckets
    └── JsonUtil.java                 # JSON serialisation for Redis cache values

src/test/java/org/example/
├── services/
│   └── HomeServiceTest.java         # Unit tests with mocked Redis
└── util/
    ├── TimeUtilTest.java            # 11 tests covering all timing types + edge cases
    └── TimeBucketUtilTest.java      # 9 tests including boundary value verification
```

---

## Redis Design Decisions

This section explains every significant Redis design choice made in this project, including the reasoning and trade-offs considered.

---

### 1. Redis Key Design

Keys follow the pattern `<entity>:<type>:<identifier>`, centralised in `RedisKeys.java`.

| Key | Type | Purpose |
|-----|------|---------|
| `city:stores:{city}` | Set | All store IDs for a city |
| `store:meta:{id}` | Hash | Store name and priority |
| `store:timing:{id}` | Hash | Store open and close times |
| `home:{city}:{bucket}` | String | Cached home page JSON result |
| `lock:home:{city}:{bucket}` | String | Distributed lock for stampede protection |

**Why separate `store:meta` and `store:timing`?**
Store metadata (name, priority) and timing (hours) change independently. A store that changes its opening hours should only require updating the `store:timing` key. A store that rebrands only needs `store:meta` updated. Keeping them separate avoids rewriting data that hasn't changed.

**Why a Set for `city:stores`?**
Redis Sets guarantee uniqueness automatically — no risk of duplicate store IDs even if seeding runs multiple times. `SMEMBERS` returns all members in O(N) which is exactly what we need.

**Why centralise keys in `RedisKeys.java`?**
Hardcoding key strings in multiple places is how typos and key collisions happen. A single class means changing the key format is a one-line edit, and `redis-cli` debugging is easy because key names are predictable.

---

### 2. Time Bucketing — Why 30-Minute Intervals

Without bucketing, every unique minute generates a unique cache key:
```
home:bangalore:21:17
home:bangalore:21:18
home:bangalore:21:19  ...and so on
```

With bucketing, all requests in a 30-minute window share one key:
```
21:00–21:29 → home:bangalore:21:00
21:30–21:59 → home:bangalore:21:30
```

**Why 30 minutes specifically?**

- **Too small (e.g. 10 min):** More keys, lower hit rate, more Redis writes.
- **Too large (e.g. 2 hours):** Data stays stale too long. A store that closes at 22:00 might still appear in results until 23:59.
- **30 minutes** is the sweet spot: stores rarely change status more than once every 30 minutes, and the data feels fresh to users.

This reduces the total number of possible cache keys from 1440 per city (one per minute) to 48 per city (one per 30-minute bucket).

---

### 3. Cache TTL — Why 5 Minutes

The cached home page result (`home:{city}:{bucket}`) has a **5-minute TTL**.

**Reasoning:** The time bucket is already 30 minutes wide, so the same result would be served for up to 30 minutes anyway. A 5-minute TTL ensures Redis doesn't hold stale data indefinitely if a store's status changes mid-bucket. It also means Redis memory is freed regularly for less popular city+time combinations.

**Why not match the TTL to the bucket size (30 min)?**
If we set TTL to 30 minutes, a store that closes at 21:00 might still appear in the 21:00–21:29 bucket results for users hitting the API at 21:28. A 5-minute TTL limits that window — the worst case is a 5-minute lag, not a 30-minute one.

---

### 4. Eviction Policy — Why LFU over LRU

**LRU (Least Recently Used):** Evicts the key that was accessed least recently. Good when all keys have similar access patterns.

**LFU (Least Frequently Used):** Evicts the key that has been accessed the fewest total times. Good when access patterns are skewed.

This project's traffic is explicitly skewed — Bangalore and Delhi get thousands of requests per minute, while Chennai and Hyderabad get very few. With LRU, a Chennai key accessed once at midnight could survive in Redis all day simply because it was touched recently. With LFU, Chennai's low-frequency keys are the first to be evicted under memory pressure, while Bangalore's high-frequency keys naturally survive.

**LFU is the correct choice for skewed traffic.** It acts as a natural traffic-aware eviction mechanism with no configuration needed beyond setting the policy.

---

### 5. Two-Layer Caching — Why Local Cache + Redis

The system uses two cache layers:

**Layer 1 — Redis (shared, 5-min TTL)**
- Shared across all app instances in a multi-node deployment
- Survives app restarts
- ~1ms latency per request (network hop to Redis)

**Layer 2 — Local JVM cache (per-instance, 30-sec TTL)**
- Only for hot cities: Bangalore and Delhi
- Zero network hops — pure in-memory `ConcurrentHashMap` lookup
- Sub-millisecond latency
- Lost on app restart (acceptable — Redis repopulates it)

**Why not just use Redis for everything?**
Redis is fast, but at thousands of requests per minute hitting the same key, even a 1ms round trip adds up. With 5000 req/min, that's 5 seconds of cumulative Redis wait time per minute. The local cache serves all of those from JVM heap — Redis sees one request per 30 seconds per hot bucket instead.

**Why only hot cities get local caching?**
The local cache consumes JVM heap. Caching Chennai and Hyderabad (low traffic) would waste memory for negligible benefit. The `HOT_CITIES` set in `LocalCacheService` makes this configurable.

**Why 30-second TTL on the local cache?**
Shorter than Redis's 5-minute TTL to keep data reasonably fresh. Users won't notice a 30-second delay in a store appearing or disappearing, but it prevents JVM memory from holding stale data for too long.

---

### 6. Cache Stampede Protection — Distributed Lock

When the Redis cache misses, multiple concurrent requests for the same city+time would all try to rebuild the cache simultaneously — each fetching store data from Redis and writing results back. This is a cache stampede.

**The solution: distributed lock using `SETNX` (set-if-not-exists)**

```
lock:home:{city}:{bucket}  →  TTL: 5 seconds
```

Only one request acquires the lock and rebuilds the cache. All others wait 50ms and then check the cache again — by then it's populated.

**Why `setIfAbsent` with a TTL?**
If the lock-holding request crashes, the lock auto-expires after 5 seconds. Without the TTL, a crashed request would hold the lock forever and block all subsequent requests for that key.

**Double-check pattern:** After acquiring the lock, we check the cache one more time before rebuilding. The request that was second in line may find the cache already populated by the first — no need to recompute.

---

### 7. Redis Pipelining — Why Batch Store Fetches

Fetching stores for a city requires multiple Redis calls:
- 1 × `SMEMBERS city:stores:{city}` → get store IDs
- N × `HGETALL store:meta:{id}` → get each store's metadata
- N × `HGETALL store:timing:{id}` → get each store's timing

Without pipelining, a city with 7 stores costs **15 serial round trips** to Redis. Each round trip has ~1ms network latency, so that's ~15ms just in network overhead before any filtering happens.

With `executePipelined()`, all `HGETALL` commands are sent in **one round trip**. The latency cost drops from O(N) to O(1) regardless of store count.

---

### 8. Eviction-Safe Rebuild

Under memory pressure with LFU eviction, `city:stores:mumbai` (a low-traffic city) may be evicted from Redis. Without a recovery mechanism, all API requests for Mumbai would silently return `[]` forever.

**The fix:** `RedisStoreService` detects an empty result from `SMEMBERS`, logs the eviction, calls `StoreRepository.findByCity()` to get the city's stores from the in-memory catalogue, re-seeds only that city's Redis keys, and retries the lookup.

**Why re-seed only the affected city?**
Re-seeding all 5 cities would write ~80 Redis keys unnecessarily. We use `findByCity()` to target only the evicted city — minimal writes, targeted recovery.

**Why `StoreRepository` as a shared class?**
Both `DataSeeder` (startup seed) and `RedisStoreService` (eviction rebuild) need the same catalogue. A single `@Component` class means adding a store is one edit in one place.

---

### 9. Graceful Redis Failure

All Redis operations in `HomeService` are wrapped in a single `try/catch`. If Redis is completely unavailable, the fallback path:
1. Calls `RedisStoreService.getStoresByCity()` directly
2. Filters and sorts results in memory
3. Returns the result without caching

The user gets a correct (if uncached) response. The app never crashes. Latency increases during Redis downtime but correctness is maintained.

---

## Summary of Trade-offs

| Decision | Choice Made | Alternative Considered | Reason |
|----------|-------------|------------------------|--------|
| Eviction policy | LFU | LRU | Traffic is skewed — LFU protects hot city keys naturally |
| Time bucket size | 30 minutes | 10 min / 1 hour | Balances hit rate vs data freshness |
| Home cache TTL | 5 minutes | 30 minutes | Limits stale data window within a bucket |
| Local cache TTL | 30 seconds | 5 minutes | Keeps data fresh without Redis pressure |
| Hot city cache | JVM HashMap | Redis replica | Zero network hops; hot cities are known upfront |
| Store fetching | Pipelining | Serial HGETALL | Reduces N×2 round trips to 1 |
| Stampede protection | Distributed lock | None / single-flight | Correct under multi-instance deployment |
| Eviction recovery | Inline reseed | App restart | Self-healing; no operator intervention needed |