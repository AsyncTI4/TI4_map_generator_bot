package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FowAutoDeclineServiceTest {

    private static final long HOUR = 60 * 60 * 1000;
    private static final long NOW = 1_000_000_000L;

    // random01 maps linearly onto [base - spread, base + spread].
    @Test
    void delayCoversBasePlusOrMinusSpread() {
        assertEquals(NOW + HOUR, FowAutoDeclineService.dueTime(NOW, 2, 1, 0.0));
        assertEquals(NOW + 2 * HOUR, FowAutoDeclineService.dueTime(NOW, 2, 1, 0.5));
        assertEquals(NOW + 3 * HOUR, FowAutoDeclineService.dueTime(NOW, 2, 1, 1.0));
    }

    // With the defaults (1h +/- 1h) the low end reaches zero; a larger spread must never schedule in the past.
    @Test
    void delayNeverNegative() {
        assertEquals(NOW, FowAutoDeclineService.dueTime(NOW, 1, 1, 0.0));
        assertEquals(NOW, FowAutoDeclineService.dueTime(NOW, 1, 5, 0.0));
    }

    @Test
    void zeroSpreadIsExactlyTheBase() {
        assertEquals(NOW + 4 * HOUR, FowAutoDeclineService.dueTime(NOW, 4, 0, 0.123));
    }

    // GM input comes from a free-text modal field.
    @Test
    void invalidHoursFallBack() {
        assertEquals(1.5, FowAutoDeclineService.parseHours("1.5", 9));
        assertEquals(9, FowAutoDeclineService.parseHours("abc", 9));
        assertEquals(9, FowAutoDeclineService.parseHours("", 9));
        assertEquals(9, FowAutoDeclineService.parseHours("-2", 9));
    }

    // A GM can type NaN or Infinity into the modal; Double.parseDouble accepts both and NaN would cast to a 0 delay.
    @Test
    void nonFiniteAndHugeHoursFallBack() {
        assertEquals(9, FowAutoDeclineService.parseHours("NaN", 9));
        assertEquals(9, FowAutoDeclineService.parseHours("Infinity", 9));
        assertEquals(9, FowAutoDeclineService.parseHours("1000", 9));
        assertEquals(168, FowAutoDeclineService.parseHours("168", 9));
    }
}
