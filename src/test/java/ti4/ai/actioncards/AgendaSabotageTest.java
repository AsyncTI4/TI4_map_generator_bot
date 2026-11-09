package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.AgendaClock;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class AgendaSabotageTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";
    private static final String CONFOUNDING = "Confounding Legal Text";
    private static final String DEADLY_PLOT = "Deadly Plot";

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private int awards;

    // Shard of the Throne at the end of voting: Nekro elected itself with 9 votes against Sol's 2. Both sit on 7 of
    // 10 points. Overrule has not been seen, so a lone Sabotage is held back for it unless something big is at stake.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        Player xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setSpeakerUserID(xxcha.getUserID());
        test.game.setPhaseOfGame("agendaEnd");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setCurrentAgendaVote("nekro", "black_9");
        test.game.setCurrentAgendaVote("sol", "blue_2");
        points(sol, 7);
        points(nekro, 7);
        nekro.setActionCard("sabo1", 1);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + ++awards, points));
    }

    private static AiPrompt window(String title) {
        return prompt("window-" + title, PromptSource.PUBLIC, NOW, "no_sabotage", "sabotage_ac_" + title + "_sol");
    }

    private double cancelValue(String title) {
        return SabotagePolicy.cancelValue(test.context(window(title)), title, sol);
    }

    private Optional<PromptButton> cancelWith(String title) {
        return SabotagePolicy.cancelWith(test.context(window(title)), window(title));
    }

    private void experimentOff() {
        AiExperiments.disable(nekro, Set.of(Experiment.AGENDA_SABOTAGE));
    }

    // The audit case: Sol plays Confounding Legal Text to take the Shard point Nekro just won. Cancelling it keeps
    // the point for Nekro and keeps it from the rival, which is worth far more than holding the Sabotage.
    @Test
    void cancelsAConfoundingLegalTextThatStealsItsPoint() {
        assertThat(SabotagePolicy.agendaKept(test.game, nekro, CONFOUNDING, sol))
                .isGreaterThan(SabotagePolicy.marginalKeep(test.game, nekro, CONFOUNDING));
        assertThat(cancelWith(CONFOUNDING)).isPresent();
    }

    @Test
    void withTheExperimentOffTheAgendaStakeIsLeftOut() {
        double on = cancelValue(CONFOUNDING);
        experimentOff();

        assertThat(SabotagePolicy.agendaKept(test.game, nekro, CONFOUNDING, sol))
                .isZero();
        assertThat(cancelValue(CONFOUNDING)).isLessThan(on);
        assertThat(cancelWith(CONFOUNDING)).isEmpty();
    }

    // Deadly Plot would discard the agenda Nekro wins.
    @Test
    void cancelsADeadlyPlotOnAnAgendaItWins() {
        assertThat(cancelWith(DEADLY_PLOT)).isPresent();
    }

    // When Sol is the one elected, Deadly Plot only takes the point away from a rival: the seat is glad to see it.
    @Test
    void doesNotCountADeadlyPlotThatHelpsIt() {
        test.game.setCurrentAgendaVote("nekro", "black_1");
        test.game.setCurrentAgendaVote("sol", "blue_9");

        assertThat(SabotagePolicy.agendaKept(test.game, nekro, DEADLY_PLOT, sol))
                .isNegative();
        assertThat(cancelWith(DEADLY_PLOT)).isEmpty();
    }

    // Outside the agenda phase there is no forecast, so nothing changes.
    @Test
    void noAgendaStakeOutsideTheAgendaPhase() {
        test.game.setPhaseOfGame("action");

        assertThat(SabotagePolicy.agendaKept(test.game, nekro, CONFOUNDING, sol))
                .isZero();
    }

    // The resolution prompt posted at the end of voting, still naming the old winner.
    private static AiPrompt currentWinner(long created) {
        return prompt(
                "current-winner",
                PromptSource.PUBLIC,
                created,
                List.of("agendaResolution_nekro", "autoresolve_manual"),
                List.of("Resolve with Current Winner", "Resolve it Manually"));
    }

    // The message Confounding Legal Text posts itself, newer than the resolution prompt.
    private static AiPrompt cardMessage() {
        return prompt(
                "card-message",
                PromptSource.PUBLIC,
                NOW + 500L,
                List.of("autoresolve_manual"),
                List.of("Resolve Confounding Legal Text"));
    }

    private static AiPrompt plots() {
        return prompt(
                "plots",
                PromptSource.PUBLIC,
                NOW - 1_000L,
                List.of("generic_button_id_2", "generic_button_id_1"),
                List.of("No Bribery", "No Deadly Plot"));
    }

    private void storePlay(CardPlay.Stage stage) {
        PlayMemory.store(
                test.context(),
                PlayMemory.agendaKey(test.context()),
                new CardPlay(stage, NOW - 600_000L, NOW, "confounding", "", false, 0, ""));
    }

    private CardPlay.Stage stage() {
        return test.context()
                .memory()
                .get(PlayMemory.agendaKey(test.context()))
                .flatMap(CardPlay::decode)
                .orElseThrow()
                .stage();
    }

    // After playing Confounding Legal Text and surviving the Sabotage window, the seat resolves the agenda by hand
    // and elects itself. "Resolve it Manually" is pressed on the original resolution prompt, so the stale "Resolve
    // with Current Winner" button is deleted with it, not on the newer message the card posted.
    @Test
    void resolvesAPlayedConfoundingByElectingItself() {
        AgendaClock.seenAt(test.contextAt(NOW - 60_000L));
        storePlay(CardPlay.Stage.TARGET);
        AiPrompt original = currentWinner(NOW - 2_000L);
        AiPrompt card = cardMessage();

        Optional<AiDecision> manual = ActionCardRules.continuePlay(test.context(original, card));
        AiDecision.Press press = (AiDecision.Press) manual.orElseThrow();
        assertThat(press.button().customId()).isEqualTo("autoresolve_manual");
        assertThat(press.prompt().messageId()).isEqualTo("current-winner");

        AiPrompt choose =
                prompt("choose", PromptSource.PUBLIC, NOW + 1_000L, "agendaResolution_sol", "agendaResolution_nekro");
        Set<String> pressed =
                Set.of(AiTurnContext.pressKey(original, original.buttons().get(1)));
        Optional<AiDecision> elect =
                ActionCardRules.continuePlay(test.contextFor(nekro, pressed, NOW + 2_000L, card, choose));
        assertThat(AiTestGame.pressedId(elect.orElseThrow())).isEqualTo("agendaResolution_nekro");
        assertThat(ShenaniganCards.playing(test.context(), CONFOUNDING)).isTrue();
    }

    // Once someone else has resolved the agenda, the original prompt is gone. The card's own message must not be
    // used to resolve the agenda a second time.
    @Test
    void doesNotResolveAgainOnceTheResolutionPromptIsGone() {
        AgendaClock.seenAt(test.contextAt(NOW - 60_000L));
        storePlay(CardPlay.Stage.TARGET);

        assertThat(ActionCardRules.continuePlay(test.context(cardMessage()))).isEmpty();
    }

    // A "choose the winner" prompt the seat did not open itself is left to whoever opened it.
    @Test
    void doesNotElectItselfFromAPromptItDidNotOpen() {
        AgendaClock.seenAt(test.contextAt(NOW - 60_000L));
        storePlay(CardPlay.Stage.TARGET);
        AiPrompt choose =
                prompt("choose", PromptSource.PUBLIC, NOW + 1_000L, "agendaResolution_sol", "agendaResolution_nekro");

        assertThat(ActionCardRules.continuePlay(test.contextAt(NOW + 2_000L, choose)))
                .isEmpty();
    }

    // Sol has not yet answered the Deadly Plot window, so the seat holds the resolution for the whole table until
    // every other player has, however long that takes. Then it resolves.
    @Test
    void waitsForEveryOtherPlayerToAnswerTheShenanigansBeforeResolving() {
        AgendaClock.seenAt(test.contextAt(NOW - 60_000L));
        storePlay(CardPlay.Stage.RESOLVE);
        AiPrompt original = currentWinner(NOW - 2_000L);
        LinkedHashSet<String> reacted = new LinkedHashSet<>(List.of("xxcha"));
        try (MockedStatic<GameMessageManager> messages = Mockito.mockStatic(GameMessageManager.class)) {
            messages.when(() -> GameMessageManager.getOne(anyString(), eq("plots")))
                    .thenAnswer(invocation ->
                            Optional.of(new GameMessage("plots", GameMessageType.AGENDA_DEADLY_PLOT, reacted, 0L)));

            assertThat(ActionCardRules.continuePlay(test.contextAt(NOW + 600_000L, original, plots())))
                    .isEmpty();
            assertThat(stage()).isEqualTo(CardPlay.Stage.RESOLVE);

            reacted.add("sol");
            Optional<AiDecision> manual =
                    ActionCardRules.continuePlay(test.contextAt(NOW + 610_000L, original, plots()));
            assertThat(AiTestGame.pressedId(manual.orElseThrow())).isEqualTo("autoresolve_manual");
        }
    }

    @Test
    void theShenaniganButtonsAreVisibleToTheAi() {
        assertThat(ActionCardRules.targetPrefixes())
                .contains("autoresolve_manual", "agendaResolution_", "resolveWithNoEffect");
        assertThat(List.of(ShenaniganCards.of("confounding"), ShenaniganCards.of("deadly_plot")))
                .allMatch(Optional::isPresent);
        assertThat(AiActionCards.usable("confounding")).isFalse();
    }
}
