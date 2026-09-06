package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gesundkrank.jskills.GameInfo;
import org.junit.jupiter.api.Test;

class MatchmakingGameInfoTest {

    private static final double TOLERANCE = 1.0e-9;

    @Test
    void matchesJskillsDefaultsApartFromTheDynamicsFactor() {
        GameInfo matchmaking = MatchmakingGameInfo.create();
        GameInfo jskillsDefault = GameInfo.getDefaultGameInfo();

        assertThat(matchmaking.getInitialMean()).isEqualTo(jskillsDefault.getInitialMean(), within(TOLERANCE));
        assertThat(matchmaking.getInitialStandardDeviation())
                .isEqualTo(jskillsDefault.getInitialStandardDeviation(), within(TOLERANCE));
        assertThat(matchmaking.getBeta()).isEqualTo(jskillsDefault.getBeta(), within(TOLERANCE));
        assertThat(matchmaking.getDrawProbability()).isEqualTo(jskillsDefault.getDrawProbability(), within(TOLERANCE));
    }

    @Test
    void usesTheJskillsDefaultDynamicsFactor() {
        assertThat(MatchmakingGameInfo.create().getDynamicsFactor())
                .isEqualTo(GameInfo.getDefaultGameInfo().getDynamicsFactor(), within(TOLERANCE));
    }

    @Test
    void handsOutAFreshInstanceEachCall() {
        assertThat(MatchmakingGameInfo.create()).isNotSameAs(MatchmakingGameInfo.create());
    }
}
