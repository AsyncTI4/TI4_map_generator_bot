package ti4.ai.promissory;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Nekro holds Hacan's Convoys (worth 2: one new partner over two rounds) and has a tactical plan worth a little
// less than 2 plus a stall. With STALL_TIMING the stall no longer counts before the tactical action.
class ConvoysStallTimingTest extends BaseTi4Test {

    private AiTestGame test;
    private AiPrompt turn;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(3);
        test.aiIsActive("action");
        Player hacan = test.addSeat("200000000000000002", "hacan", "red");
        hacan.addOwnedPromissoryNoteByID("convoys");
        test.nekro.setPromissoryNote("convoys", 5);
        destroyer(test.nekro, AiTestGame.HOME);
        destroyer(test.sol, "304");
        turn = prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_passForRound");
    }

    private void destroyer(Player player, String position) {
        Tile tile = test.game.getTileByPosition(position);
        if (tile == null) tile = test.place("46", position);
        test.units(tile, "space", player, UnitType.Destroyer, 1);
        test.game.setLastModifiedDate(test.game.getLastModifiedDate() + 1);
    }

    private AiTurnContext contextWithPlanBetweenValueAndStall() {
        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, turn);
        double value = ConvoysRules.value(test.game, test.nekro);
        double stall = Stall.value(test.game, test.nekro);
        assertThat(value).isEqualTo(2.0);
        assertThat(stall).isPositive();
        TacticalRules.remember(
                context,
                new TacticalPlan(TacticalPlan.Kind.POSITION, AiTestGame.HOME, List.of(), Map.of(), value + stall / 2));
        return context;
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void keepsConvoysForTheEndOfTheRound() {
        AiTurnContext context = contextWithPlanBetweenValueAndStall();

        assertThat(ConvoysRules.insteadOfTacticalAction(context, List.of(turn))).isEmpty();
        assertThat(pressed(ConvoysRules.beforePassing(context, List.of(turn)))).isEqualTo("FFCC_nekro_componentAction");
    }

    @Test
    void theDisabledExperimentPlaysConvoysInsteadOfTheTacticalAction() {
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.STALL_TIMING));
        AiTurnContext context = contextWithPlanBetweenValueAndStall();

        assertThat(pressed(ConvoysRules.insteadOfTacticalAction(context, List.of(turn))))
                .isEqualTo("FFCC_nekro_componentAction");
    }
}
