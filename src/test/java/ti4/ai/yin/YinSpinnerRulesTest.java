package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// Sling Relay, Freelancers and Chaos Mapping production post Yin Spinner on its own in the actions channel instead of
// on the production buttons; the seat spins there too. The production message itself is left to the production flow.
class YinSpinnerRulesTest extends BaseTi4Test {

    private YinTestBoard board;

    @BeforeEach
    void setUp() {
        board = new YinTestBoard();
        board.yin.addTech(YinSpinnerRules.TECH);
    }

    private AiPrompt spin(String... ids) {
        return AiTestGame.withContent(
                prompt("spin", PromptSource.PUBLIC, NOW, ids),
                board.yin.getRepresentationUnfogged() + " you may use this to Yin Spin.");
    }

    @Test
    void spinsAfterASlingRelayBuild() {
        assertThat(YinSpinnerRules.afterOtherProduction(board.context(NOW, spin(YinSpinnerRules.START)))
                        .map(AiTestGame::pressedId))
                .contains(YinSpinnerRules.START);
    }

    @Test
    void leavesTheProductionMessageToTheProductionFlow() {
        AiPrompt production = spin(YinSpinnerRules.START, "FFCC_yin_deleteButtons_tacticalAction_" + board.position);

        assertThat(YinSpinnerRules.afterOtherProduction(board.context(NOW, production)))
                .isEmpty();
    }

    @Test
    void waitsUntilTheSlingRelayUnitIsPlaced() {
        // The bot posts the spin a moment before the Sling Relay placement buttons, and placing the unit deletes that
        // placement message. "After you produce units": while it is still up nothing has been produced yet.
        AiPrompt placement = prompt(
                "sling",
                PromptSource.PUBLIC,
                NOW + 100,
                "FFCC_yin_" + YinSpinnerRules.BUILD_PREFIX + "_destroyer_" + board.position,
                "FFCC_yin_" + YinSpinnerRules.BUILD_PREFIX + "_cruiser_" + board.position);

        assertThat(YinSpinnerRules.afterOtherProduction(board.context(NOW, spin(YinSpinnerRules.START), placement)))
                .isEmpty();
        assertThat(YinSpinnerRules.afterOtherProduction(board.context(NOW + 5000, spin(YinSpinnerRules.START)))
                        .map(AiTestGame::pressedId))
                .contains(YinSpinnerRules.START);
    }

    @Test
    void anOlderUnrelatedPlacementDoesNotHoldTheSpin() {
        AiPrompt older = prompt(
                "older",
                PromptSource.PUBLIC,
                NOW - 60_000,
                "FFCC_yin_" + YinSpinnerRules.BUILD_PREFIX + "_destroyer_" + board.position);

        assertThat(YinSpinnerRules.afterOtherProduction(board.context(NOW, spin(YinSpinnerRules.START), older))
                        .map(AiTestGame::pressedId))
                .contains(YinSpinnerRules.START);
    }

    @Test
    void needsTheTechnology() {
        board.yin.removeTech(YinSpinnerRules.TECH);

        assertThat(YinSpinnerRules.afterOtherProduction(board.context(NOW, spin(YinSpinnerRules.START))))
                .isEmpty();
    }
}
