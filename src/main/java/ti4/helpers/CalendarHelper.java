package ti4.helpers;

import java.util.Calendar;

/**
 * The CalendarHelper class provides methods to check if the current date is near specific holidays.
 * 
 * Note: The isBetween method is a private helper method used to determine if the current date falls within a specified range.
 */
public class CalendarHelper {

    /**
     * @param startMonth 0 indexed month #
     * @param startDay 1 indexed day # of month
     * @param endMonth 0 indexed month #
     * @param endDay 1 indexed day # of month
     * @return
     */
    private static boolean isBetween(int startMonth, int startDay, int endMonth, int endDay) {
        return Calendar.MONTH == startMonth && Calendar.DAY_OF_MONTH >= startDay
            && Calendar.MONTH == endMonth && Calendar.DAY_OF_MONTH <= endDay;
    }

    public static boolean isNearNewYearsDay() {
        return isBetween(Calendar.DECEMBER, 27, Calendar.JANUARY, 2);
    }

    public static boolean isNearValentinesDay() {
        return isBetween(Calendar.FEBRUARY, 12, Calendar.FEBRUARY, 15);
    }

    public static boolean isNearHalloween() {
        return isBetween(Calendar.OCTOBER, 1, Calendar.NOVEMBER, 7);
    }

    public static boolean isNearChristmas() {
        return isBetween(Calendar.DECEMBER, 1, Calendar.DECEMBER, 26);
    }
}
