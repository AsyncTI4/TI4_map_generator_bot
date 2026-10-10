package ti4.ai.nekro;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Thunder's Edge Warfare is a tactical action that places no command token. With Fleet Logistics the seat attacks
// with Warfare first, so the same ships stay free for a follow-up tactical action in the same turn.
class FleetLogisticsTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
        test.game.setStrategyCardSet("te");
        test.nekro.addSC(6);
        test.aiIsActive("action");
    }

    @Test
    void attacksWithWarfareFirstWhenItHasFleetLogistics() {
        test.nekro.addTech("fl");

        assertThat(pressedId(brain.decide(test.context(turn())))).isEqualTo("FFCC_nekro_strategicAction_6");
    }

    @Test
    void attacksWithATacticalActionWithoutFleetLogistics() {
        assertThat(pressedId(brain.decide(test.context(turn())))).isEqualTo("FFCC_nekro_tacticalAction");
    }

    private static AiPrompt turn() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_strategicAction_6");
    }
}
