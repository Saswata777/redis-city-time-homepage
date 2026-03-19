package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * TimeBucketUtilTest
 *
 * Tests that times are correctly snapped to their 30-minute bucket.
 *
 * Why this matters:
 * The bucket is used as part of the Redis cache key (home:{city}:{bucket}).
 * A wrong bucket = wrong cache key = cache misses on every request, or
 * worse, a different city's data being served.
 *
 * Key boundaries to test:
 *   :00 — start of first bucket, must stay :00 (not round down to -1)
 *   :29 — last minute of first bucket, must round down to :00
 *   :30 — exact start of second bucket, must stay :30
 *   :59 — last minute of second bucket, must round down to :30
 */
@DisplayName("TimeBucketUtil")
class TimeBucketUtilTest {

    // ── First half of hour (00–29 → :00) ─────────────────────────────────────

    @Test
    @DisplayName("Exact hour start maps to :00 bucket")
    void getBucket_exactHour_returnsHourBucket() {
        assertEquals("10:00", TimeBucketUtil.getBucket("10:00"));
        assertEquals("00:00", TimeBucketUtil.getBucket("00:00"));
        assertEquals("23:00", TimeBucketUtil.getBucket("23:00"));
    }

    @Test
    @DisplayName("Minutes 01–29 round down to :00 bucket")
    void getBucket_firstHalf_roundsDownToZero() {
        assertEquals("10:00", TimeBucketUtil.getBucket("10:01"));
        assertEquals("10:00", TimeBucketUtil.getBucket("10:17"));
        assertEquals("10:00", TimeBucketUtil.getBucket("10:29"));
    }

    // ── Second half of hour (30–59 → :30) ────────────────────────────────────

    @Test
    @DisplayName("Exact :30 maps to :30 bucket")
    void getBucket_exactHalfHour_returnsHalfBucket() {
        assertEquals("10:30", TimeBucketUtil.getBucket("10:30"));
        assertEquals("00:30", TimeBucketUtil.getBucket("00:30"));
        assertEquals("23:30", TimeBucketUtil.getBucket("23:30"));
    }

    @Test
    @DisplayName("Minutes 31–59 round down to :30 bucket")
    void getBucket_secondHalf_roundsDownToThirty() {
        assertEquals("10:30", TimeBucketUtil.getBucket("10:31"));
        assertEquals("10:30", TimeBucketUtil.getBucket("10:45"));
        assertEquals("10:30", TimeBucketUtil.getBucket("10:59"));
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Midnight 00:00 stays as 00:00")
    void getBucket_midnight_staysMidnight() {
        assertEquals("00:00", TimeBucketUtil.getBucket("00:00"));
    }

    @Test
    @DisplayName("Last minute of day 23:59 maps to 23:30")
    void getBucket_lastMinuteOfDay_mapsToHalfBucket() {
        assertEquals("23:30", TimeBucketUtil.getBucket("23:59"));
    }

    @Test
    @DisplayName("Two-digit hour formatting is preserved")
    void getBucket_singleDigitHour_formattedWithLeadingZero() {
        assertEquals("09:00", TimeBucketUtil.getBucket("09:15"));
        assertEquals("01:30", TimeBucketUtil.getBucket("01:45"));
    }

    // ── Cache key reduction verification ─────────────────────────────────────

    @Test
    @DisplayName("All minutes in 10:00–10:29 produce the same bucket key")
    void getBucket_wholeFirstWindow_allSameBucket() {
        String expected = "10:00";
        for (int m = 0; m <= 29; m++) {
            String time = String.format("10:%02d", m);
            assertEquals(expected, TimeBucketUtil.getBucket(time),
                    "Expected " + time + " → " + expected);
        }
    }

    @Test
    @DisplayName("All minutes in 10:30–10:59 produce the same bucket key")
    void getBucket_wholeSecondWindow_allSameBucket() {
        String expected = "10:30";
        for (int m = 30; m <= 59; m++) {
            String time = String.format("10:%02d", m);
            assertEquals(expected, TimeBucketUtil.getBucket(time),
                    "Expected " + time + " → " + expected);
        }
    }
}