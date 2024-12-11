package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;

import org.junit.jupiter.api.Test;

public class CalendarHelperTest {
    @Test
    void testIsBetween() {
        assertTrue(CalendarHelper.isBetween(Calendar.MONTH - 1, Calendar.DAY_OF_MONTH, Calendar.MONTH + 1, Calendar.DAY_OF_MONTH));
        assertTrue(CalendarHelper.isBetween(Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.MONTH, Calendar.DAY_OF_MONTH));
        assertFalse(CalendarHelper.isBetween(Calendar.MONTH - 2, Calendar.DAY_OF_MONTH, Calendar.MONTH - 1, Calendar.DAY_OF_MONTH));
    }
}
