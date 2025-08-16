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
        assertTrue(CalendarHelper.isBetween(currentMonth, currentDay, currentMonth, currentDay));
        assertFalse(CalendarHelper.isBetween(currentMonth - 2, currentDay, currentMonth - 1, currentDay));
    }
}
