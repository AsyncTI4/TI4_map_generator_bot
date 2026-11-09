package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ShenanigansPlayTest extends BaseTi4Test {

    private static final long LATE = NOW + 30_000L;
    private static final String THIRD_ID = "200000000000000002";
    private static final int CONFOUNDING_INDEX = 41;
    private static final int DEADLY_PLOT_INDEX = 42;
    private static final String PLAY_CONFOUNDING = "ac_play_from_hand_" + CONFOUNDING_INDEX;
    private static final String PLAY_DEADLY_PLOT = "ac_play_from_hand_" + DEADLY_PLOT_INDEX;

    private AiTestGame test;
    private Player xxcha;
    private int awards;

    // Shard of the Throne at the end of voting: Sol elected itself with 9 votes, Nekro voted 2 for itself. Both sit
    // on 7 of 10 points, so the point Shard gives is worth a lot either way.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setPhaseOfGame("agendaEnd");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setCurrentAgendaVote("sol", "blue_9");
        test.game.setCurrentAgendaVote("nekro", "black_2");
        points(test.sol, 7);
        points(test.nekro, 7);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + ++awards, points));
    }

    private static AiPrompt legalText() {
        return AiTestGame.prompt(
                "legal",
                PromptSource.PUBLIC,
                NOW,
                List.of("generic_button_id_3", "genericReact4"),
                List.of("Refuse Confounding Legal Text", "Refuse Confusing Legal Text"));
    }

    private static AiPrompt plots() {
        return AiTestGame.prompt(
                "plots",
                PromptSource.PUBLIC,
                NOW,
                List.of("generic_button_id_2", "generic_button_id_1"),
                List.of("No Bribery", "No Deadly Plot"));
    }

    private static AiPrompt hand() {
        return AiTestGame.prompt("hand", PromptSource.AI_THREAD, NOW, PLAY_CONFOUNDING, PLAY_DEADLY_PLOT);
    }

    // Answers every shenanigans button the seat would press, in order, marking each press as done.
    private List<String> answers(AiPrompt... prompts) {
        ShenanigansRules.react(test.contextFor(test.nekro, Set.of(), NOW, prompts));
        Set<String> done = new HashSet<>();
        List<String> pressed = new ArrayList<>();
        for (int step = 0; step < 6; step++) {
            Optional<AiDecision> decision = ShenanigansRules.react(test.contextFor(test.nekro, done, LATE, prompts));
            if (decision.isEmpty() || !(decision.get() instanceof AiDecision.Press press)) break;
            pressed.add(press.button().customId());
            done.add(AiTurnContext.pressKey(press.prompt(), press.button()));
            if (press.button().customId().startsWith("ac_play_from_hand_")) {
                test.nekro.removeActionCard(
                        Integer.parseInt(press.button().customId().substring("ac_play_from_hand_".length())));
            }
        }
        return pressed;
    }

    // The audit case: the seat holds Confounding Legal Text while a rival on 7 points is about to take Shard of the
    // Throne. It plays the card instead of confirming that it plays none, and never refuses it afterwards.
    @Test
    void playsAHeldConfoundingLegalTextToTakeShardFromTheLeader() {
        test.nekro.setActionCard("confounding", CONFOUNDING_INDEX);

        List<String> pressed = answers(legalText(), hand());

        assertThat(pressed).contains(PLAY_CONFOUNDING, "genericReact4").doesNotContain("generic_button_id_3");
    }

    @Test
    void withTheExperimentOffItStillRefusesWhileHoldingIt() {
        test.nekro.setActionCard("confounding", CONFOUNDING_INDEX);
        AiExperiments.disable(test.nekro, Set.of(Experiment.AGENDA_SABOTAGE));

        List<String> pressed = answers(legalText(), hand());

        assertThat(pressed).containsExactlyInAnyOrder("generic_button_id_3", "genericReact4");
    }

    // Nothing to gain when the seat is already the one elected.
    @Test
    void refusesConfoundingWhenItIsAlreadyElected() {
        test.nekro.setActionCard("confounding", CONFOUNDING_INDEX);
        test.game.setCurrentAgendaVote("nekro", "black_12");

        assertThat(answers(legalText(), hand())).containsExactlyInAnyOrder("generic_button_id_3", "genericReact4");
    }

    // Deadly Plot exhausts all of the player's planets, so on the first agenda it costs the seat its votes on the
    // second one; here Mecatol Rex (6 influence) is still ready, which outweighs Shard's swing. On the last agenda of
    // the phase the planets ready anyway. EarlyDeadlyPlotTest covers the first-agenda cases.
    @Test
    void keepsDeadlyPlotForTheFinalAgenda() {
        test.nekro.setActionCard("deadly_plot", DEADLY_PLOT_INDEX);
        test.place("18", "000");
        test.nekro.addPlanet("mr");

        assertThat(answers(plots(), hand())).containsExactlyInAnyOrder("generic_button_id_1", "generic_button_id_2");

        test.game.setStoredValue("agendaCount", "2");

        assertThat(answers(plots(), hand()))
                .contains(PLAY_DEADLY_PLOT, "generic_button_id_2")
                .doesNotContain("generic_button_id_1");
    }

    // Deadly Plot needs a vote or prediction on another outcome than the winner.
    @Test
    void cannotDeadlyPlotAnOutcomeItVotedFor() {
        test.nekro.setActionCard("deadly_plot", DEADLY_PLOT_INDEX);
        test.game.setStoredValue("agendaCount", "2");
        test.game.removeOutcomeAgendaVote("nekro");
        test.game.setCurrentAgendaVote("sol", "blue_9;black_2");

        assertThat(answers(plots(), hand())).containsExactlyInAnyOrder("generic_button_id_1", "generic_button_id_2");
    }
}
