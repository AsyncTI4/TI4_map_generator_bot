package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gesundkrank.jskills.GameInfo;
import org.junit.jupiter.api.Test;

class MatchmakingGameInfoTest {

    private static final double TOLERANCE = 1.0e-9;
    private static final double EXPECTED_DRAW_PROBABILITY = 0.5;
    private static final double EXPECTED_DYNAMICS_FACTOR_DIVISOR = 50.0;

    @Test
    void keepsTheJskillsDefaultsForTheDistributionParameters() {
        GameInfo matchmaking = MatchmakingGameInfo.create();
        GameInfo jskillsDefault = GameInfo.getDefaultGameInfo();

        assertThat(matchmaking.getInitialMean()).isEqualTo(jskillsDefault.getInitialMean(), within(TOLERANCE));
        assertThat(matchmaking.getInitialStandardDeviation())
                .isEqualTo(jskillsDefault.getInitialStandardDeviation(), within(TOLERANCE));
        assertThat(matchmaking.getBeta()).isEqualTo(jskillsDefault.getBeta(), within(TOLERANCE));
    }

    @Test
    void tunesTheDrawProbabilityAboveTheJskillsDefault() {
        GameInfo matchmaking = MatchmakingGameInfo.create();

        assertThat(matchmaking.getDrawProbability()).isEqualTo(EXPECTED_DRAW_PROBABILITY, within(TOLERANCE));
        assertThat(matchmaking.getDrawProbability())
                .isGreaterThan(GameInfo.getDefaultGameInfo().getDrawProbability());
    }

    @Test
    void tunesTheDynamicsFactorAboveTheJskillsDefault() {
        GameInfo matchmaking = MatchmakingGameInfo.create();

        assertThat(matchmaking.getDynamicsFactor())
                .isEqualTo(
                        matchmaking.getInitialStandardDeviation() / EXPECTED_DYNAMICS_FACTOR_DIVISOR,
                        within(TOLERANCE));
        assertThat(matchmaking.getDynamicsFactor())
                .isGreaterThan(GameInfo.getDefaultGameInfo().getDynamicsFactor());
    }

    @Test
    void handsOutAFreshInstanceEachCall() {
        assertThat(MatchmakingGameInfo.create()).isNotSameAs(MatchmakingGameInfo.create());
    }
}
