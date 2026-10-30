package ti4.ai.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DelegationChoiceTest {

    private static final String SEAT = "7100000123456789";
    private static final String CHANNEL = "1234567890123456789";
    private static final String MESSAGE = "9876543210987654321";

    @Test
    void roundTripsThroughItsCustomId() {
        DelegationChoice choice = DelegationChoice.of(SEAT, CHANNEL, MESSAGE, 12, "FFCC_nekro_scPick_3");

        assertThat(DelegationChoice.parse(choice.customId())).contains(choice);
        assertThat(choice.matches("FFCC_nekro_scPick_3")).isTrue();
        assertThat(choice.matches("FFCC_nekro_scPick_4")).isFalse();
    }

    // Discord rejects custom ids over 100 characters, and they are not truncated for us.
    @Test
    void customIdStaysWithinDiscordsLimitForLongestIds() {
        DelegationChoice choice = DelegationChoice.of(SEAT, CHANNEL + "0", MESSAGE + "0", 24, "x".repeat(100));

        assertThat(choice.customId()).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void rejectsForeignOrMalformedIds() {
        assertThat(DelegationChoice.parse("scPick_3")).isEmpty();
        assertThat(DelegationChoice.parse("aiSeatPick_only_three_parts")).isEmpty();
        assertThat(DelegationChoice.parse(null)).isEmpty();
    }
}
