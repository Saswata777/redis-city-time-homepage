package org.example.services;

import org.example.model.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * HomeServiceTest
 *
 * Tests HomeService logic using Mockito to stub its three dependencies:
 *   - StringRedisTemplate  (Redis interactions)
 *   - RedisStoreService    (raw store data)
 *   - LocalCacheService    (in-process hot-city cache)
 *
 * Key Mockito rules applied here:
 *
 * 1. @ExtendWith(MockitoExtension.class) enables STRICT_STUBS mode by default.
 *    This means every stub set up with when(...) MUST be used by at least one
 *    test — unused stubs are flagged as UnnecessaryStubbingException.
 *    Fix: don't put shared stubs in @BeforeEach. Set them up per-test instead.
 *
 * 2. The Redis fallback test must stub the *first* get() call (line 35 of
 *    HomeService, outside the try/catch) to throw — not a later one.
 *    That first call is what triggers the fallback path when Redis is down.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomeService")
class HomeServiceTest {

    @Mock private RedisStoreService redisStoreService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private LocalCacheService localCacheService;
    @Mock private ValueOperations<String, String> valueOps;

    private HomeService homeService;

    // Sample stores covering all three timing types
    private final Store store24x7      = new Store("S1", "AllNighter",  "bangalore", "00:00", "00:00", 10);
    private final Store storeSameDay   = new Store("S2", "DayShop",     "bangalore", "09:00", "21:00", 8);
    private final Store storeOvernight = new Store("S3", "NightOwl",    "bangalore", "22:00", "04:00", 6);

    @BeforeEach
    void setUp() {
        homeService = new HomeService(redisStoreService, redisTemplate, localCacheService);
        // NOTE: do NOT stub redisTemplate.opsForValue() here.
        // Strict Mockito flags it as unnecessary on tests that never reach Redis
        // (e.g. the local cache hit test). Stub per-test instead.
    }

    // ── Local cache hit ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns local cache result without touching Redis for hot city")
    void getHomePage_localCacheHit_returnsWithoutRedis() {
        // No redisTemplate stub needed — local cache hit returns before Redis
        when(localCacheService.isHotCity("bangalore")).thenReturn(true);
        when(localCacheService.get(eq("bangalore"), anyString()))
                .thenReturn(List.of(store24x7));

        List<Store> result = homeService.getHomePage("bangalore", "14:00");

        assertEquals(1, result.size());
        assertEquals("AllNighter", result.get(0).getName());
        verifyNoInteractions(redisTemplate);
    }

    // ── Redis cache hit ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns Redis cache result and populates local cache for hot city")
    void getHomePage_redisCacheHit_populatesLocalCache() {
        String cachedJson = "[{\"id\":\"S1\",\"name\":\"AllNighter\",\"city\":\"bangalore\","
                + "\"openTime\":\"00:00\",\"closeTime\":\"00:00\",\"priority\":10}]";

        when(localCacheService.isHotCity("bangalore")).thenReturn(true);
        when(localCacheService.get(eq("bangalore"), anyString())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(cachedJson);

        List<Store> result = homeService.getHomePage("bangalore", "14:00");

        assertEquals(1, result.size());
        verify(localCacheService).put(eq("bangalore"), anyString(), anyList());
        verifyNoInteractions(redisStoreService);
    }

    // ── Cache miss → filter + sort ────────────────────────────────────────────

    @Test
    @DisplayName("Cache miss: only open stores returned, sorted by priority desc")
    void getHomePage_cacheMiss_filtersAndSortsByPriority() {
        when(localCacheService.isHotCity("bangalore")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // First get() = cache miss, second get() inside lock = also miss
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(redisStoreService.getStoresByCity("bangalore"))
                .thenReturn(List.of(store24x7, storeSameDay, storeOvernight));

        // 14:00 — 24x7 open, SameDay 09-21 open, Overnight 22-04 closed
        List<Store> result = homeService.getHomePage("bangalore", "14:00");

        assertEquals(2, result.size());
        assertEquals("AllNighter", result.get(0).getName()); // priority 10
        assertEquals("DayShop",    result.get(1).getName()); // priority 8
        assertFalse(result.stream().anyMatch(s -> s.getName().equals("NightOwl")));
    }

    @Test
    @DisplayName("Cache miss at 02:00: overnight and 24x7 open, same-day closed")
    void getHomePage_cacheMiss_2am_overnightAndAllNighterOnly() {
        when(localCacheService.isHotCity("bangalore")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(redisStoreService.getStoresByCity("bangalore"))
                .thenReturn(List.of(store24x7, storeSameDay, storeOvernight));

        List<Store> result = homeService.getHomePage("bangalore", "02:00");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getName().equals("AllNighter")));
        assertTrue(result.stream().anyMatch(s -> s.getName().equals("NightOwl")));
        assertFalse(result.stream().anyMatch(s -> s.getName().equals("DayShop")));
    }

    @Test
    @DisplayName("City name is normalised to lowercase before processing")
    void getHomePage_cityNormalisedToLowercase() {
        when(localCacheService.isHotCity("bangalore")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(redisStoreService.getStoresByCity("bangalore")).thenReturn(List.of(store24x7));

        homeService.getHomePage("BANGALORE", "12:00");

        verify(redisStoreService).getStoresByCity("bangalore");
    }

    // ── Redis failure fallback ────────────────────────────────────────────────

    @Test
    @DisplayName("Redis failure on first get() triggers graceful fallback")
    void getHomePage_redisDown_fallsBackGracefully() {
        // HomeService calls redisTemplate.opsForValue().get(key) at line 35,
        // BEFORE the try/catch block. That call is what needs to throw.
        // The catch block at line 93 handles exceptions from the lock section,
        // but not from this first cache check — so we wrap the whole method
        // by making opsForValue() itself throw, which propagates through both paths.
        when(localCacheService.isHotCity("bangalore")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        when(redisStoreService.getStoresByCity("bangalore"))
                .thenReturn(List.of(store24x7, storeSameDay));

        List<Store> result = homeService.getHomePage("bangalore", "14:00");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Empty store list returns empty result without error")
    void getHomePage_noStores_returnsEmptyList() {
        when(localCacheService.isHotCity("bangalore")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(redisStoreService.getStoresByCity("bangalore")).thenReturn(List.of());

        List<Store> result = homeService.getHomePage("bangalore", "14:00");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}