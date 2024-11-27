package ti4.helpers;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

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
}
