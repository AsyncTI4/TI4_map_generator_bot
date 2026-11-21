package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// A "both" spend lists what the exhausted planets add up to as resources and as influence. Centauri is 1 resource and
// 3 influence. The hero counts its combined 4 as both; the breakthrough lets it count as 3 of either kind.
class XxchaSpendMessageTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.place("34", "201");
        xxcha.addPlanet("centauri");
        xxcha.exhaustPlanet("centauri");
        xxcha.addSpentThing("centauri");
    }

    private String both() {
        return Helper.buildSpentThingsMessage(xxcha, test.game, "both");
    }

    @Test
    void ordinaryPlanetsAddTheirOwnKindOfValue() {
        assertThat(both()).contains("total spend of 1 resource or 3 influence.");
    }

    @Test
    void theHeroCountsTheCombinedValueAsBothKinds() {
        xxcha.addLeader("xxchahero");
        xxcha.getLeader("xxchahero").orElseThrow().setLocked(false);

        assertThat(both()).contains("total spend of 4 resources and 4 influence.");
    }

    @Test
    void theBreakthroughCountsThePlanetAtItsLargerValueForOneKindOnly() {
        xxcha.addBreakthrough("xxchabt");
        xxcha.setBreakthroughUnlocked("xxchabt", true);

        assertThat(both())
                .contains("total spend of 3 resources or 3 influence.")
                .contains("each planet counts as resources or as influence, not both");
    }
}
