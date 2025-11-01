package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import org.junit.jupiter.api.Test;

class CalendarHelperTest {

    @Test
    void testIsBetween() {
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        assertTrue(CalendarHelper.isBetween(currentMonth - 1, currentDay, currentMonth + 1, currentDay));
        assertFalse(CalendarHelper.isBetween(currentMonth, currentDay, currentMonth, currentDay));
        assertFalse(CalendarHelper.isBetween(currentMonth - 2, currentDay, currentMonth - 1, currentDay));
    }

    @Test
    void testHalloween() {
        assertTrue(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.OCTOBER, 2, Calendar.NOVEMBER, 7));
        assertTrue(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.NOVEMBER, 6, Calendar.NOVEMBER, 7));
        assertTrue(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.OCTOBER, 6, Calendar.NOVEMBER, 7));
        assertTrue(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.OCTOBER, 24, Calendar.NOVEMBER, 7));
        assertTrue(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.OCTOBER, 31, Calendar.NOVEMBER, 7));
        assertTrue(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.NOVEMBER, 3, Calendar.NOVEMBER, 7));

        assertFalse(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.SEPTEMBER, 30, Calendar.NOVEMBER, 7));
        assertFalse(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.NOVEMBER, 8, Calendar.NOVEMBER, 7));
        assertFalse(CalendarHelper.isBetween(Calendar.OCTOBER, 1, Calendar.APRIL, 8, Calendar.NOVEMBER, 7));
    }

    @Test
    void testIsNearNewYearsDay() {
        assertTrue(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.DECEMBER, 28, Calendar.JANUARY, 2));
        assertTrue(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.JANUARY, 1, Calendar.JANUARY, 2));
        assertTrue(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.DECEMBER, 31, Calendar.JANUARY, 2));
        assertTrue(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.JANUARY, 1, Calendar.JANUARY, 2));

        assertFalse(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.DECEMBER, 26, Calendar.JANUARY, 2));
        assertFalse(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.JANUARY, 3, Calendar.JANUARY, 2));
        assertFalse(CalendarHelper.isBetween(Calendar.DECEMBER, 27, Calendar.JULY, 3, Calendar.JANUARY, 2));
    }
}
