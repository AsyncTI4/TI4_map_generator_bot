package ti4.ai.promissory;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class CeasefireRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile origin;
    private Tile target;

    @BeforeEach
    void setUp() {
        board(new AiTestGame());
    }

    // An empty system at the home position and, next to it, Wellon's system, where the activation happens.
    private void board(AiTestGame game) {
        test = game;
        origin = test.place("46", AiTestGame.HOME);
        target = test.place("19", AiTestGame.neighbourOf(AiTestGame.HOME));
    }

    private void activates(Player active, String summaryTail) {
        test.isActive(active, "action");
        test.game.setActiveSystem(target.getPosition());
        test.game.setStoredValue(
                "currentActionSummary" + active.getFaction(),
                active.getFaction() + " Activated " + target.getPosition() + "." + summaryTail);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static AiPrompt solCeasefireInHand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "resolvePNPlay_blue_cf");
    }

    private void aiHoldsSolsCeasefire() {
        test.sol.addOwnedPromissoryNoteByID("blue_cf");
        test.nekro.setPromissoryNote("blue_cf");
        test.units(target, "space", test.nekro, UnitType.Carrier, 1);
    }

    private void solHoldsTheAisCeasefire() {
        test.nekro.addOwnedPromissoryNoteByID("black_cf");
        test.sol.setPromissoryNote("black_cf");
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
    }

    // Sol activates the system holding the AI's carrier and has a cruiser in range: the AI plays Sol's Ceasefire.
    @Test
    void playsCeasefireWhenTheActivePlayerCouldMoveIn() {
        aiHoldsSolsCeasefire();
        test.units(origin, "space", test.sol, UnitType.Cruiser, 1);
        activates(test.sol, "");

        assertThat(pressed(PromissoryRules.playCeasefire(test.context(solCeasefireInHand()))))
                .isEqualTo("resolvePNPlay_blue_cf");
    }

    // With no ship able to reach the system, the activation is no threat and the note is kept.
    @Test
    void keepsCeasefireWhenNothingCanReachItsUnits() {
        aiHoldsSolsCeasefire();
        activates(test.sol, "");

        assertThat(PromissoryRules.playCeasefire(test.context(solCeasefireInHand())))
                .isEmpty();
    }

    // Once Sol has finished moving, the window has passed.
    @Test
    void keepsCeasefireOnceTheActivePlayerHasMoved() {
        aiHoldsSolsCeasefire();
        test.units(origin, "space", test.sol, UnitType.Cruiser, 1);
        activates(test.sol, " Moved ships there.");

        assertThat(PromissoryRules.playCeasefire(test.context(solCeasefireInHand())))
                .isEmpty();
    }

    // The bot does not enforce Ceasefire. When the AI's own Ceasefire, which was out of its hand when it activated,
    // comes back while another player has units in the system, the AI moves nothing in and just finishes moving.
    // It never needs to know who held the note.
    @Test
    void movesNothingInOnceItsCeasefireIsPlayed() {
        solHoldsTheAisCeasefire();
        test.units(origin, "space", test.nekro, UnitType.Carrier, 1);
        activates(test.nekro, "");
        PromissoryRules.observe(test.context());
        test.sol.removePromissoryNote("black_cf");
        test.nekro.setPromissoryNote("black_cf");
        TacticalRules.remember(
                test.context(),
                new TacticalPlan(
                        Kind.POSITION,
                        target.getPosition(),
                        List.of(new UnitMove(origin.getPosition(), "space", UnitType.Carrier, 1)),
                        Map.of(),
                        5.0));
        AiPrompt moves = prompt(
                "moves",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalMoveFrom_" + origin.getPosition(),
                "FFCC_nekro_concludeMove_" + target.getPosition());

        assertThat(CeasefireRules.blocksMovement(test.context())).isTrue();
        assertThat(pressed(TacticalRules.continueAction(test.context(moves))))
                .isEqualTo("FFCC_nekro_concludeMove_" + target.getPosition());
    }

    // A human who could hold the AI's Ceasefire only gets a text reminder at activation, so the AI waits before
    // moving in.
    @Test
    void givesAHumanHolderTwoMinutesBeforeMoving() {
        solHoldsTheAisCeasefire();
        activates(test.nekro, "");

        assertThat(PromissoryRules.holdForCeasefire(test.context())).containsInstanceOf(AiDecision.Wait.class);
        assertThat(PromissoryRules.holdForCeasefire(test.contextAt(NOW + 121_000L)))
                .isEmpty();
    }

    // Another AI seat decides on its own tick before the mover's next one, so there is nothing to wait for.
    @Test
    void doesNotWaitForAnAiHolder() {
        board(AiTestGame.withSolAi());
        solHoldsTheAisCeasefire();
        activates(test.nekro, "");

        assertThat(PromissoryRules.holdForCeasefire(test.context())).isEmpty();
    }

    // With its own Ceasefire still in hand, nobody can stop its movement, so it does not wait.
    @Test
    void doesNotWaitWhileItHoldsItsOwnCeasefire() {
        test.nekro.addOwnedPromissoryNoteByID("black_cf");
        test.nekro.setPromissoryNote("black_cf");
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        activates(test.nekro, "");

        assertThat(PromissoryRules.holdForCeasefire(test.context())).isEmpty();
    }
}
