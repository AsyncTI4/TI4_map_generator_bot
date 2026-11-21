package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

// The lift is how many win-rate points a faction gains from holding its breakthrough, read off the human games and
// shrunk so that factions with only a few games without a breakthrough do not swing it.
class ExpeditionStatsTest extends BaseTi4Test {

    @Test
    void factionsThatLiftALotWantTheirBreakthroughMost() {
        for (String faction :
                List.of("mahact", "mentak", "letnev", "yin", "arborec", "l1z1x", "muaat", "xxcha", "deepwrought")) {
            assertThat(ExpeditionStats.breakthroughLiftPoints(faction))
                    .as(faction)
                    .isGreaterThan(1.9);
        }
        assertThat(ExpeditionStats.breakthroughLiftPoints("mahact")).isGreaterThan(4.5);
    }

    @Test
    void factionsThatLoseNothingOrGainLittleHaveASmallOrNegativeLift() {
        for (String faction : List.of("crimson", "yssaril", "naalu", "nekro", "jolnar")) {
            assertThat(ExpeditionStats.breakthroughLiftPoints(faction))
                    .as(faction)
                    .isNegative();
        }
        for (String faction : List.of("saar", "argent")) {
            assertThat(ExpeditionStats.breakthroughLiftPoints(faction))
                    .as(faction)
                    .isBetween(0.0, 1.5);
        }
    }

    // Keleres has a handful of games without a breakthrough: the prior keeps the lift near the global point.
    @Test
    void tinySamplesStayNearTheGlobalLift() {
        assertThat(ExpeditionStats.breakthroughLiftPoints("keleresa")).isBetween(0.5, 1.5);
        assertThat(ExpeditionStats.breakthroughLiftPoints("keleresx")).isBetween(0.5, 1.5);
    }

    @Test
    void anUnknownFactionGetsTheGlobalLift() {
        assertThat(ExpeditionStats.breakthroughLiftPoints("unheard")).isEqualTo(1.0);
        assertThat(ExpeditionStats.kindNudge("unheard", ExpeditionKind.FIVE_INF))
                .isZero();
    }

    // Obsidian and Firmament are the two sides of one faction and share its record.
    @Test
    void firmamentSharesObsidiansRecord() {
        assertThat(ExpeditionStats.breakthroughLiftPoints("firmament"))
                .isEqualTo(ExpeditionStats.breakthroughLiftPoints("obsidian"));
    }

    @Test
    void theKindNudgeNeverExceedsAnEighthOfAPoint() {
        for (String faction : List.of("mahact", "hacan", "bastion", "nekro", "sol")) {
            for (ExpeditionKind kind : ExpeditionKind.values()) {
                assertThat(Math.abs(ExpeditionStats.kindNudge(faction, kind)))
                        .as(faction + " " + kind)
                        .isLessThanOrEqualTo(ExpeditionStats.KIND_NUDGE_CAP);
            }
        }
    }

    // Hacan wins more often after the trade goods expedition than after the resources one.
    @Test
    void theNudgeFollowsTheKindsThatWorkForTheFaction() {
        assertThat(ExpeditionStats.kindNudge("hacan", ExpeditionKind.TRADE_GOODS))
                .isPositive();
        assertThat(ExpeditionStats.kindNudge("hacan", ExpeditionKind.FIVE_RES)).isNegative();
    }
}
