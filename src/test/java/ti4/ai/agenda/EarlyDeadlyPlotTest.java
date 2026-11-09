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
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Deadly Plot (PoK action card): "If you voted for or predicted another outcome, discard the agenda instead. The
// agenda is resolved with no effect and it is not replaced. Then, exhaust all of your planets." Played on the first
// agenda of the phase, the seat cannot vote with its planets on the second one (rules: docs/rules/cards/
// action-cards.md, Deadly Plot I-V; LRR 8.4: planets ready only after both agendas are resolved).
class EarlyDeadlyPlotTest extends BaseTi4Test {

    private static final long LATE = NOW + 30_000L;
    private static final String THIRD_ID = "200000000000000002";
    private static final int DEADLY_PLOT_INDEX = 42;
    private static final String PLAY_DEADLY_PLOT = "ac_play_from_hand_" + DEADLY_PLOT_INDEX;
    private static final String NO_DEADLY_PLOT = "generic_button_id_1";
    private static final String NO_BRIBERY = "generic_button_id_2";

    private AiTestGame test;
    private Player xxcha;

    // The first agenda of the phase has been voted on: Sol 9 votes for its outcome, Nekro 2 votes on itself. Nekro
    // holds Deadly Plot and has no ready planets left, so discarding costs it no second-agenda votes.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setPhaseOfGame("agendaEnd");
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaVote("sol", "blue_9");
        test.game.setCurrentAgendaVote("nekro", "black_2");
        test.nekro.setActionCard("deadly_plot", DEADLY_PLOT_INDEX);
    }

    private void agenda(String alias) {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_" + alias);
    }

    private static AiPrompt plots() {
        return AiTestGame.prompt(
                "plots",
                PromptSource.PUBLIC,
                NOW,
                List.of(NO_BRIBERY, NO_DEADLY_PLOT),
                List.of("No Bribery", "No Deadly Plot"));
    }

    private static AiPrompt hand() {
        return AiTestGame.prompt("hand", PromptSource.AI_THREAD, NOW, PLAY_DEADLY_PLOT);
    }

    // Answers every shenanigans button the seat would press, in order, marking each press as done.
    private List<String> answers() {
        AiPrompt[] prompts = {plots(), hand()};
        ShenanigansRules.react(test.contextFor(test.nekro, Set.of(), NOW, prompts));
        Set<String> done = new HashSet<>();
        List<String> pressed = new ArrayList<>();
        for (int step = 0; step < 6; step++) {
            Optional<AiDecision> decision = ShenanigansRules.react(test.contextFor(test.nekro, done, LATE, prompts));
            if (decision.isEmpty() || !(decision.get() instanceof AiDecision.Press press)) break;
            pressed.add(press.button().customId());
            done.add(AiTurnContext.pressKey(press.prompt(), press.button()));
            if (PLAY_DEADLY_PLOT.equals(press.button().customId())) test.nekro.removeActionCard(DEADLY_PLOT_INDEX);
        }
        return pressed;
    }

    private void assertPlays() {
        assertThat(answers()).contains(PLAY_DEADLY_PLOT, NO_BRIBERY).doesNotContain(NO_DEADLY_PLOT);
    }

    private void assertKeeps() {
        assertThat(answers()).containsExactlyInAnyOrder(NO_DEADLY_PLOT, NO_BRIBERY);
    }

    private void shardToSolOnSevenPoints() {
        agenda("shard_of_the_throne");
        test.points(test.sol, 7);
        test.points(test.nekro, 7);
    }

    // Shard of the Throne gives the elected player 1 VP: Sol, on 7 of 10, would gain a point and Nekro none, so
    // Nekro discards it on the first agenda.
    @Test
    void discardsAFirstAgendaThatGivesOnlyARivalAPoint() {
        shardToSolOnSevenPoints();

        assertThat(AgendaPoints.gainers(test.game, "sol")).containsExactly(test.sol);
        assertPlays();
    }

    // Minister of Sciences is worth 3 resources a round to the holder for the rest of the game, more than a VP's
    // worth to Sol, who leads the table alone. No points are at stake, but the ability is powerful enough.
    @Test
    void discardsAFirstAgendaThatGivesTheLeaderAPowerfulAbility() {
        agenda("minister_sciences");
        test.points(test.sol, 4);
        test.points(test.nekro, 2);
        test.points(xxcha, 2);

        assertThat(AgendaPoints.gainers(test.game, "sol")).isEmpty();
        assertThat(AgendaForecast.of(test.game, test.nekro).utility(test.sol, "sol"))
                .isGreaterThanOrEqualTo(ScoringStakes.vpWorth(test.game, test.sol));
        assertPlays();
    }

    // The same powerful ability to a rival trailing the table is not worth spending the card on the first agenda.
    @Test
    void keepsItWhenThePowerfulAbilityGoesToARivalNotDoingWell() {
        agenda("minister_sciences");
        test.points(test.sol, 2);
        test.points(test.nekro, 5);
        test.points(xxcha, 5);

        assertKeeps();
    }

    // Imperial Arbiter is an ordinary ability (a strategy card swap), below a VP's worth even to the leader.
    @Test
    void keepsItOnAnOrdinaryFirstAgendaForTheLeader() {
        agenda("arbiter");
        test.points(test.sol, 4);
        test.points(test.nekro, 2);
        test.points(xxcha, 2);

        assertKeeps();
    }

    // With Mecatol Rex (6 influence) still ready, discarding the first agenda costs Nekro its 6 votes on the second
    // one, 4.8 TG at 0.8 TG per influence, which eats Shard's swing. On the final agenda there is nothing to lose.
    @Test
    void paysForTheVotesItLosesOnTheSecondAgenda() {
        shardToSolOnSevenPoints();
        test.place("18", "000");
        test.nekro.addPlanet("mr");

        assertKeeps();

        test.game.setStoredValue("agendaCount", "2");

        assertPlays();
    }

    @Test
    void withTheExperimentOffItWaitsForTheFinalAgenda() {
        shardToSolOnSevenPoints();
        AiExperiments.disable(test.nekro, Set.of(Experiment.AGENDA_SABOTAGE));

        assertKeeps();
    }
}
