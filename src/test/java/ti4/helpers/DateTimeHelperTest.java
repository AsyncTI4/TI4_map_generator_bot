package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DateTimeHelperTest {

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
