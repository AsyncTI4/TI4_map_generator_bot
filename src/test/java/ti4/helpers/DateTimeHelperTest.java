package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DateTimeHelperTest {

    @Test
    void testGetLongDateTimeFromDiscordSnowflake() {
        long snowflake = 481860200169472030L;
        long expectedDateTime = 1534954824250L;
        long actualDateTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(snowflake);
        assertThat(actualDateTime).isEqualTo(expectedDateTime);
    }

    @Test
    void testGetLongDateTimeFromDiscordSnowflakeWithZero() {
        long snowflake = 0L;
        long expectedDateTime = 1420070400000L;
        long actualDateTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(snowflake);
        assertThat(actualDateTime).isEqualTo(expectedDateTime);
    }

    @Test
    void testGetTimeRepresentationNanoSeconds() {
        assertThat(DateTimeHelper.getTimeRepresentationNanoSeconds(0L)).isEqualTo("00s:000:000:000");
        assertThat(DateTimeHelper.getTimeRepresentationNanoSeconds(1_000_000_000L))
                .isEqualTo("01s:000:000:000");
        assertThat(DateTimeHelper.getTimeRepresentationNanoSeconds(1_000_000L)).isEqualTo("00s:001:000:000");
    }

    @Test
    void testGetTimeRepresentationToMilliseconds() {
        assertThat(DateTimeHelper.getTimeRepresentationToMilliseconds(12_345L)).isEqualTo("00m:12s:345ms");
    }
}
