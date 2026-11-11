package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.yin.YinTestBoard.PLANET;
import static ti4.ai.yin.YinTestBoard.SPACE;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Yin rules are wired into the brain: the follow-up buttons the bot posts without an owner are visible, and the
// start-of-combat and after-round abilities come before the dice.
class YinBrainTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();

    @Test
    void theUnownedYinFollowUpsArePublicWindows() {
        assertThat(brain.publicWindowHandlerPrefixes())
                .contains(
                        DannelRules.TARGET_PREFIX,
                        DannelRules.COMBAT_PREFIX,
                        BrotherOmarRules.STEP_ONE,
                        BrotherOmarRules.REMOVAL_PREFIX,
                        YinSpinnerRules.START);
    }

    @Test
    void indoctrinatesBeforeRollingTheFirstRound() {
        YinTestBoard board = new YinTestBoard();
        board.sol.addPlanet(PLANET);
        board.units(PLANET, board.yin, UnitType.Infantry, 3);
        board.units(PLANET, board.sol, UnitType.Infantry, 3);
        String start = "FFCC_yin_initialIndoctrination_" + PLANET;
        AiPrompt combat = prompt(
                "combat",
                PromptSource.COMBAT_THREAD,
                NOW - 60_000,
                start,
                "combatRoll_" + board.position + "_" + PLANET);

        assertThat(AiTestGame.pressedId(brain.decide(board.context(NOW, combat))))
                .isEqualTo(start);
    }

    @Test
    void usesDevotionBeforeRollingTheNextRound() {
        YinTestBoard board = new YinTestBoard();
        board.units(SPACE, board.yin, UnitType.Destroyer, 2);
        board.units(SPACE, board.yin, UnitType.Cruiser, 2);
        board.units(SPACE, board.sol, UnitType.Carrier, 2);
        board.units(SPACE, board.sol, UnitType.Cruiser, 2);
        board.rounds(board.yin, SPACE, 1);
        board.rounds(board.sol, SPACE, 1);
        String start = "FFCC_yin_startDevotion_" + board.position;
        AiPrompt combat = prompt(
                "combat",
                PromptSource.COMBAT_THREAD,
                NOW - 60_000,
                start,
                "combatRoll_" + board.position + "_" + SPACE);

        assertThat(AiTestGame.pressedId(brain.decide(board.context(NOW, combat))))
                .isEqualTo(start);
    }
}
