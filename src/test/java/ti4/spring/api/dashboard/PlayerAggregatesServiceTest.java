package ti4.spring.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerAggregatesServiceTest {

    @Test
    void buildAggressionProfileReturnsZeroScoresWhenLessThanTwoGamesWithRoundStats() {
        Map<String, PlayerAggregatesService.AggressionInput> inputs = new LinkedHashMap<>();
        inputs.put("g1", new PlayerAggregatesService.AggressionInput(3, 1, 2));

        PlayerDashboardResponse.AggressionProfile profile =
                PlayerAggregatesService.buildAggressionProfileFromGameInputs(inputs, 4, 1);

        assertThat(profile.byGame()).containsExactly(Map.entry("g1", 0.0));
        assertThat(profile.summary().avgScore()).isEqualTo(0.0);
        assertThat(profile.summary().medianScore()).isEqualTo(0.0);
        assertThat(profile.summary().maxScore()).isEqualTo(0.0);
        assertThat(profile.summary().minScore()).isEqualTo(0.0);
        assertThat(profile.summary().mostAggressiveGameId()).isNull();
        assertThat(profile.coverage().completedGamesConsidered()).isEqualTo(4);
        assertThat(profile.coverage().gamesWithRoundStats()).isEqualTo(1);
    }

    @Test
    void buildAggressionProfileComputesScoresFromRoundStatsInputsOnly() {
        Map<String, PlayerAggregatesService.AggressionInput> inputs = new LinkedHashMap<>();
        inputs.put("g1", new PlayerAggregatesService.AggressionInput(10, 5, 8));
        inputs.put("g2", new PlayerAggregatesService.AggressionInput(2, 1, 1));

        PlayerDashboardResponse.AggressionProfile profile =
                PlayerAggregatesService.buildAggressionProfileFromGameInputs(inputs, 3, 2);

        assertThat(profile.byGame()).containsOnlyKeys("g1", "g2");
        assertThat(profile.byGame().get("g1")).isEqualTo(1.0);
        assertThat(profile.byGame().get("g2")).isEqualTo(-1.0);
        assertThat(profile.summary().avgScore()).isEqualTo(0.0);
        assertThat(profile.summary().medianScore()).isEqualTo(0.0);
        assertThat(profile.summary().maxScore()).isEqualTo(1.0);
        assertThat(profile.summary().minScore()).isEqualTo(-1.0);
        assertThat(profile.summary().mostAggressiveGameId()).isEqualTo("g1");
        assertThat(profile.coverage().completedGamesConsidered()).isEqualTo(3);
        assertThat(profile.coverage().gamesWithRoundStats()).isEqualTo(2);
    }

    @Test
    void hashCompletedGameIdsIsDeterministicAndSensitiveToMembership() {
        String hashA = PlayerAggregatesService.hashCompletedGameIds(List.of("a", "b", "c"));
        String hashB = PlayerAggregatesService.hashCompletedGameIds(List.of("a", "b", "c"));
        String hashDifferent = PlayerAggregatesService.hashCompletedGameIds(List.of("a", "b"));

        assertThat(hashA).isEqualTo(hashB);
        assertThat(hashA).isNotEqualTo(hashDifferent);
        assertThat(hashA).hasSize(64);
    }
}
