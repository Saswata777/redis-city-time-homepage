package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * TimeUtilTest
 *
 * Tests the three store timing types the README requires:
 *   1. Same-day   : open < close   e.g. 09:00 – 21:00
 *   2. Overnight  : open > close   e.g. 22:00 – 04:00  (wraps past midnight)
 *   3. 24x7       : open == close  e.g. 00:00 – 00:00
 *
 * Why test TimeUtil so thoroughly?
 * The overnight logic is the trickiest part of the system. A naive
 * implementation (just check open <= current <= close) silently breaks
 * for overnight stores. These tests document exactly what correct
 * behaviour looks like and catch any regression.
 *
 * Test naming convention: methodName_scenario_expectedResult
 */
@DisplayName("TimeUtil")
class TimeUtilTest {

    // ── 24x7 stores ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("24x7 store is open at any time")
    void isStoreOpen_24x7_alwaysOpen() {
        assertTrue(TimeUtil.isStoreOpen("00:00", "00:00", "00:00"));
        assertTrue(TimeUtil.isStoreOpen("00:00", "00:00", "12:00"));
        assertTrue(TimeUtil.isStoreOpen("00:00", "00:00", "23:59"));
    }

    // ── Same-day stores ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Same-day store is open during its window")
    void isStoreOpen_sameDay_openDuringWindow() {
        assertTrue(TimeUtil.isStoreOpen("09:00", "21:00", "09:00")); // exact open
        assertTrue(TimeUtil.isStoreOpen("09:00", "21:00", "14:00")); // middle
        assertTrue(TimeUtil.isStoreOpen("09:00", "21:00", "21:00")); // exact close
    }

    @Test
    @DisplayName("Same-day store is closed before opening and after closing")
    void isStoreOpen_sameDay_closedOutsideWindow() {
        assertFalse(TimeUtil.isStoreOpen("09:00", "21:00", "08:59")); // one minute before
        assertFalse(TimeUtil.isStoreOpen("09:00", "21:00", "21:01")); // one minute after
        assertFalse(TimeUtil.isStoreOpen("09:00", "21:00", "02:00")); // middle of night
    }

    @Test
    @DisplayName("Morning-only store (Aavin Milk 05:00-09:00) is closed at noon")
    void isStoreOpen_morningOnly_closedAtNoon() {
        assertTrue(TimeUtil.isStoreOpen("05:00", "09:00", "06:30"));
        assertFalse(TimeUtil.isStoreOpen("05:00", "09:00", "12:00"));
        assertFalse(TimeUtil.isStoreOpen("05:00", "09:00", "04:59"));
    }

    // ── Overnight stores ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Overnight store is open after its opening time (same day)")
    void isStoreOpen_overnight_openAfterOpenTime() {
        assertTrue(TimeUtil.isStoreOpen("22:00", "04:00", "22:00")); // exact open
        assertTrue(TimeUtil.isStoreOpen("22:00", "04:00", "23:30")); // late night
        assertTrue(TimeUtil.isStoreOpen("22:00", "04:00", "23:59")); // just before midnight
    }

    @Test
    @DisplayName("Overnight store is open after midnight until close time")
    void isStoreOpen_overnight_openAfterMidnight() {
        assertTrue(TimeUtil.isStoreOpen("22:00", "04:00", "00:00")); // midnight
        assertTrue(TimeUtil.isStoreOpen("22:00", "04:00", "02:00")); // 2 AM
        assertTrue(TimeUtil.isStoreOpen("22:00", "04:00", "04:00")); // exact close
    }

    @Test
    @DisplayName("Overnight store is closed in the daytime gap")
    void isStoreOpen_overnight_closedDuringDay() {
        assertFalse(TimeUtil.isStoreOpen("22:00", "04:00", "04:01")); // one minute after close
        assertFalse(TimeUtil.isStoreOpen("22:00", "04:00", "10:00")); // morning
        assertFalse(TimeUtil.isStoreOpen("22:00", "04:00", "21:59")); // one minute before open
    }

    @Test
    @DisplayName("Night Pharmacy (21:00-09:00) covers late night and early morning")
    void isStoreOpen_nightPharmacy_correctWindow() {
        assertTrue(TimeUtil.isStoreOpen("21:00", "09:00", "21:00"));  // exact open
        assertTrue(TimeUtil.isStoreOpen("21:00", "09:00", "00:30"));  // after midnight
        assertTrue(TimeUtil.isStoreOpen("21:00", "09:00", "09:00"));  // exact close
        assertFalse(TimeUtil.isStoreOpen("21:00", "09:00", "09:01")); // just after close
        assertFalse(TimeUtil.isStoreOpen("21:00", "09:00", "12:00")); // noon — closed
        assertFalse(TimeUtil.isStoreOpen("21:00", "09:00", "20:59")); // one minute before open
    }

    @Test
    @DisplayName("Overnight store closing at 02:00 (Burger King 10:00-02:00)")
    void isStoreOpen_lateNightCloseAt2am() {
        assertTrue(TimeUtil.isStoreOpen("10:00", "02:00", "10:00"));
        assertTrue(TimeUtil.isStoreOpen("10:00", "02:00", "23:00"));
        assertTrue(TimeUtil.isStoreOpen("10:00", "02:00", "01:59"));
        assertTrue(TimeUtil.isStoreOpen("10:00", "02:00", "02:00")); // exact close
        assertFalse(TimeUtil.isStoreOpen("10:00", "02:00", "02:01"));
        assertFalse(TimeUtil.isStoreOpen("10:00", "02:00", "09:59"));
    }

    // ── Midnight edge cases ───────────────────────────────────────────────────

    @Test
    @DisplayName("Store closing exactly at midnight (00:00) treated as same-day")
    void isStoreOpen_closingAtMidnight_sameDayBehaviour() {
        // open=09:00 close=00:00 — open < close is false (00:00 < 09:00),
        // so this is treated as overnight: open after 09:00 OR before/at 00:00
        // 00:00 == closeTime so it is exactly on the boundary
        assertTrue(TimeUtil.isStoreOpen("09:00", "00:00", "12:00"));
        assertTrue(TimeUtil.isStoreOpen("09:00", "00:00", "23:59"));
        assertFalse(TimeUtil.isStoreOpen("09:00", "00:00", "08:59"));
    }
}