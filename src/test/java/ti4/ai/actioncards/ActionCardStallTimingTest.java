package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

// Mining Initiative mid-round, with a tactical plan worth a little less than the card plus its stall credit. With
// STALL_TIMING the stall credit is dropped from the instead-of-tactical comparison, so the tactical action goes first
// and the card waits for the beforePassing slot.
class ActionCardStallTimingTest extends BaseTi4Test {

    private static final String MINING = "mining_initiative";
    private static final int HAND_INDEX = 412;
    private static final String PLAY = "ac_play_from_hand_" + HAND_INDEX;

    private AiTestGame test;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        test.nekro.setActionCard(MINING, HAND_INDEX);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of("nekro")), 0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiTurnContext contextWithPlanBetweenCardAndStall() {
        AiTurnContext context = test.context(prompt("hand", PromptSource.AI_THREAD, NOW, PLAY));
        double card = ActionCardValue.bestNewComponentPlay(test.game, test.nekro)
                .orElseThrow()
                .value();
        double stall = Stall.cardActionValue(test.game, test.nekro);
        assertThat(stall).isPositive();
        TacticalRules.remember(
                context,
                new TacticalPlan(TacticalPlan.Kind.POSITION, AiTestGame.HOME, List.of(), Map.of(), card - stall / 2));
        return context;
    }

    // ActionCardRules takes Stall.cardActionValue back out of the card value, so the value it is handed must hold
    // exactly that stall credit on top of the card's own play value. This pins the coupling with economyPlan.
    @Test
    void theEconomyCardValueHoldsExactlyTheCardStallCredit() {
        ActionCardValue.Candidate best =
                ActionCardValue.bestNewComponentPlay(test.game, test.nekro).orElseThrow();

        assertThat(best.alias()).isEqualTo(MINING);
        assertThat(best.urgent()).isFalse();
        assertThat(best.value())
                .isEqualTo(ActionCardValue.playValue(test.game, test.nekro, MINING)
                        + Stall.cardActionValue(test.game, test.nekro));
    }

    @Test
    void takesTheTacticalActionBeforeANonUrgentCard() {
        AiTurnContext context = contextWithPlanBetweenCardAndStall();

        assertThat(ActionCardRules.insteadOfTacticalAction(context)).isEmpty();
        assertThat(pressed(ActionCardRules.playBeforePassing(context))).isEqualTo(PLAY);
    }

    @Test
    void theDisabledExperimentStillPlaysTheCardFirst() {
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.STALL_TIMING));
        AiTurnContext context = contextWithPlanBetweenCardAndStall();

        assertThat(pressed(ActionCardRules.insteadOfTacticalAction(context))).isEqualTo(PLAY);
    }
}
