package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedGameStateTeamMateTest {

    // Community-mode team mates are not seated players, so warmup from the database must restore them from the
    // encoded game column exactly as ManagedGame built them from the game file.
    @Test
    void teamMateMappingSurvivesTheDatabaseColumnRoundTrip() {
        Map<String, String> teamMates = Map.of("200", "100", "300", "100", "400", "101");

        String encoded = state(teamMates).encodedTeamMateToPrimaryId();

        assertThat(ManagedGameState.decodeTeamMateToPrimaryId(encoded)).isEqualTo(teamMates);
    }

    @Test
    void gameWithoutTeamMatesStoresNoColumnValue() {
        assertThat(state(Map.of()).encodedTeamMateToPrimaryId()).isNull();
        assertThat(ManagedGameState.decodeTeamMateToPrimaryId(null)).isEmpty();
    }

    private static ManagedGameState state(Map<String, String> teamMates) {
        return new ManagedGameState(
                "team-mates",
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                null,
                0,
                0,
                1,
                null,
                null,
                null,
                null,
                java.util.List.of(),
                teamMates);
    }
}
