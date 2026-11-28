package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SavedGameReplayTest {

    @Test
    void realClockStampsDoNotCountAsDifferences() {
        String recorded = "last_modified_date 1795081283018\ncreation_date 2026.11.19\ntotal_turn_time 5081147014\n"
                + "check_reacts_info startTimeOfRound1Strategy,1795081256064:round,1";
        String replayed = "last_modified_date 1795099999999\ncreation_date 2026.11.20\ntotal_turn_time 5081740761\n"
                + "check_reacts_info startTimeOfRound1Strategy,1795099990000:round,1";

        assertThat(SavedGameReplay.firstDifference(recorded, replayed)).isEmpty();
    }

    @Test
    void virtualClockStampsStillCount() {
        // 1790009640000 is on the virtual clock, which a replay reproduces, so a change there is a real difference.
        assertThat(SavedGameReplay.firstDifference("offer 1790009640000", "offer 1790009650000"))
                .isPresent();
    }

    @Test
    void aDifferenceIsReportedWithTheStretchAroundIt() {
        String head = "x".repeat(500);
        SavedGameReplay.Line line = SavedGameReplay.firstDifference(
                        "line one\n" + head + "fighter:tail", "line one\n" + head + "carrier:tail")
                .orElseThrow();

        assertThat(line.number()).isEqualTo(2);
        assertThat(line.expected()).startsWith("...").endsWith("fighter:tail");
        assertThat(line.actual()).startsWith("...").endsWith("carrier:tail");
        assertThat(line.expected().length()).isLessThan(head.length());
    }

    @Test
    void aMissingLineIsADifference() {
        assertThat(SavedGameReplay.firstDifference("a\nb", "a"))
                .hasValueSatisfying(line -> assertThat(line.actual()).isEqualTo("(end of save)"));
    }

    @Test
    void savesThatDifferOnlyByRealClockStampsCompareEqual() {
        assertThat(SavedGameReplay.comparableText("last_modified_date 1795081283018\nround 2"))
                .isEqualTo(SavedGameReplay.comparableText("last_modified_date 1795099999999\nround 2"));
    }
}
