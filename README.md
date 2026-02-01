# City & Time Aware Home Page System (Redis Learning Project)

## Problem Statement
Design and implement a *Redis-centric backend system* that serves a *Home Page response* based on a user’s *city* and *current time*.

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
