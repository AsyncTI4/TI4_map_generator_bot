package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class HelperTest {

    @Test
    void getDateDifference_acrossMidnight_returnsDayDifference() {
        long start = LocalDate.of(2026, 5, 1)
                .atTime(23, 59)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long end = LocalDate.of(2026, 5, 2)
                .atTime(0, 1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        assertThat(Helper.getDateDifference(start, end)).isEqualTo(1);
    }

    @Test
    void getDateDifference_wholeDayInterval_returnsCorrectDayCount() {
        long start = LocalDate.of(2026, 5, 1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long end = LocalDate.of(2026, 5, 31)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        assertThat(Helper.getDateDifference(start, end)).isEqualTo(30);
    }
}
