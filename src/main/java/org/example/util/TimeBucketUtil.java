package org.example.util;

import java.time.LocalTime;

public class TimeBucketUtil {

    public static String getBucket(String time) {
//        Input time 10:17 ---after this function ---> 10:00 [To reduce the number of key in the Cache]
        LocalTime t = LocalTime.parse(time);
        int minute = (t.getMinute() / 30) * 30;
        return String.format("%02d:%02d", t.getHour(), minute);
    }
}

/*
 * TimeBucketUtil
 *
 * Purpose:
 * This utility converts an exact time (HH:mm) into a fixed 30-minute time bucket.
 *
 * Why do we need this?
 * In a real-world system, users can send requests at any minute:
 *   10:17, 10:18, 10:19, ...
 *
 * If we directly use this time in Redis keys:
 *   home:kolkata:10:17
 *   home:kolkata:10:18
 *   home:kolkata:10:19
 *
 * This leads to:
 *   - Too many unique keys (cache explosion)
 *   - Very low cache hit rate
 *   - Increased memory usage in Redis
 *   - Poor performance
 *
 * Optimization Strategy:
 * Instead of storing data per minute, we group requests into 30-minute buckets:
 *
 *   10:00–10:29 → 10:00
 *   10:30–10:59 → 10:30
 *
 * So:
 *   10:17 → 10:00
 *   10:18 → 10:00
 *   10:19 → 10:00
 *
 * Now all these requests use the same Redis key:
 *   home:kolkata:10:00
 *
 * Benefits:
 *   - Reduces number of Redis keys
 *   - Increases cache hit rate
 *   - Improves response time (more cache hits)
 *   - Saves memory
 *   - Scales better under high traffic
 *
 * System Flow:
 *   User Request → TimeBucketUtil → Bucketed Time → Redis Key → Cache Lookup
 *
 * This is a common optimization technique used in large-scale systems
 * to avoid cache fragmentation and improve efficiency.
 */
