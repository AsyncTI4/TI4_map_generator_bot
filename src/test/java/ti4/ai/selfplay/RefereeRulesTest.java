package ti4.ai.selfplay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.selfplay.RefereeRules.RefereePress;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionCheckService;
import ti4.service.game.EndGameService;
import ti4.testUtils.BaseTi4Test;

// In an all-AI game nobody else presses the game's own housekeeping buttons, so one of the AI seats does, after a
// delay that leaves room for a human watcher to step in.
class RefereeRulesTest extends BaseTi4Test {

    private static final long SETTLED = NOW - AiSettings.REFEREE_DELAY.toMillis();

    private AiTestGame test;
    private List<Player> seats;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.game.setPlayerCountForMap(2);
        seats = List.of(test.nekro, test.sol);
    }

    @Test
    void doesNothingUnlessEverySeatIsAnAi() {
        AiTestGame mixed = new AiTestGame();
        mixed.game.setPlayerCountForMap(2);
        AiPrompt step = prompt("step", PromptSource.PUBLIC, SETTLED, "startStrategyPhase");

        assertThat(RefereeRules.next(mixed.game, List.of(mixed.nekro), List.of(step), key -> true, NOW))
                .isEmpty();
    }

    @Test
    void doesNothingWithoutSeats() {
        AiPrompt step = prompt("step", PromptSource.PUBLIC, SETTLED, "startStrategyPhase");

        assertThat(RefereeRules.next(test.game, List.of(), List.of(step), key -> true, NOW))
                .isEmpty();
    }

    @Test
    void waitsForTheRefereeDelayBeforePressing() {
        AiPrompt fresh = prompt("fresh", PromptSource.PUBLIC, SETTLED + 1, "startStrategyPhase");
        assertThat(next(fresh)).isEmpty();

        AiPrompt settled = prompt("settled", PromptSource.PUBLIC, SETTLED, "startStrategyPhase");
        assertThat(next(settled).map(press -> press.button().customId())).contains("startStrategyPhase");
    }

    // Dealing secrets before every seat is filled would leave the late seats without any.
    @Test
    void dealsStartingSecretsOnlyOnceEverySeatIsFilled() {
        test.game.setPlayerCountForMap(3);
        AiPrompt deal = prompt("deal", PromptSource.PUBLIC, SETTLED, "deal2SOToAll");
        assertThat(next(deal)).isEmpty();

        test.game.setPlayerCountForMap(2);
        assertThat(next(deal).map(press -> press.button().customId())).contains("deal2SOToAll");
    }

    // The speaker-owned objective reveal button is pressed by whichever AI plays that faction, not by the speaker.
    @Test
    void ownedStepsArePressedByTheSeatOwningThatFaction() {
        test.game.setSpeakerUserID(test.nekro.getUserID());
        test.game.getPublicObjectives2Peekable().add("cpt");
        AiPrompt reveal = prompt("reveal", PromptSource.PUBLIC, SETTLED, "FFCC_sol_reveal_stage_1position_1");

        Optional<RefereePress> press = next(reveal);

        assertThat(press).isPresent();
        assertThat(press.get().seat()).isSameAs(test.sol);
        assertThat(press.get().button().customId()).isEqualTo("FFCC_sol_reveal_stage_1position_1");
    }

    // With the stage II deck empty there is nothing left to reveal; the game ends instead of revealing forever.
    @Test
    void doesNotRevealOnceTheObjectiveTrackIsExhausted() {
        test.game.setSpeakerUserID(test.nekro.getUserID());
        AiPrompt reveal = prompt(
                "reveal",
                PromptSource.PUBLIC,
                SETTLED,
                "reveal_stage_2",
                EndGameService.MOST_POINTS_END_GAME_BUTTON_ID);

        assertThat(RefereeRules.trackExhausted(test.game)).isTrue();
        assertThat(next(reveal)).isEmpty();
        assertThat(RefereeRules.offersGameEnd(List.of(reveal))).isTrue();
    }

    // Once every seat has scored in the last round, the bot posts the reveal button next to its own End Game button.
    // If the AIs do not read that as the end of the game, nobody presses anything and the game stalls in
    // statusScoring with every seat passed. Driving the bot's real prompt keeps the AIs in step with its button ids.
    @Test
    void recognisesTheEndGameButtonTheBotOffersOnceTheObjectivesRunOut() {
        test.game.setRound(9);
        test.game.setPublicObjectives2(new ArrayList<>());
        AiPrompt allScored = allHaveScoredPrompt();

        assertThat(RefereeRules.trackExhausted(test.game)).isTrue();
        assertThat(RefereeRules.offersGameEnd(List.of(allScored))).isTrue();
        assertThat(next(allScored)).isEmpty();
    }

    @Test
    void stepsOwnedByAFactionWithoutAnAiSeatAreLeftAlone() {
        AiPrompt reveal = prompt("reveal", PromptSource.PUBLIC, SETTLED, "FFCC_hacan_reveal_stage_1position_1");

        assertThat(next(reveal)).isEmpty();
    }

    @Test
    void unownedStepsArePressedByTheSpeakerSeat() {
        test.game.setSpeakerUserID(test.sol.getUserID());
        AiPrompt flip = prompt("flip", PromptSource.PUBLIC, SETTLED, "flip_agenda");

        assertThat(next(flip).map(RefereePress::seat)).containsSame(test.sol);
    }

    @Test
    void unownedStepsFallBackToTheFirstSeatWithoutAnAiSpeaker() {
        test.game.setSpeakerUserID("");
        AiPrompt flip = prompt("flip", PromptSource.PUBLIC, SETTLED, "flip_agenda");

        assertThat(RefereeRules.next(test.game, List.of(test.sol, test.nekro), List.of(flip), key -> true, NOW)
                        .map(RefereePress::seat))
                .containsSame(test.sol);
        assertThat(next(flip).map(RefereePress::seat)).containsSame(test.nekro);
    }

    // Ending the game or starting a rematch is the humans' call, even in self-play.
    @Test
    void neverEndsTheGameOrStartsARematch() {
        AiPrompt end =
                prompt("end", PromptSource.PUBLIC, SETTLED, EndGameService.MOST_POINTS_END_GAME_BUTTON_ID, "rematch");

        assertThat(next(end)).isEmpty();
        assertThat(RefereeRules.anyStep().test(end.buttons().getFirst())).isFalse();
        assertThat(RefereeRules.anyStep().test(end.buttons().getLast())).isFalse();
    }

    @Test
    void skipsButtonsAnySeatAlreadyPressed() {
        AiPrompt steps = prompt("steps", PromptSource.PUBLIC, SETTLED, "startOfGameObjReveal", "startStrategyPhase");
        String revealed = AiTurnContext.pressKey(steps, steps.buttons().getFirst());

        Optional<RefereePress> press =
                RefereeRules.next(test.game, seats, List.of(steps), key -> !key.equals(revealed), NOW);

        assertThat(press.map(found -> found.button().customId())).contains("startStrategyPhase");

        String started = AiTurnContext.pressKey(steps, steps.buttons().getLast());
        assertThat(RefereeRules.next(
                        test.game,
                        seats,
                        List.of(steps),
                        key -> !Set.of(revealed, started).contains(key),
                        NOW))
                .isEmpty();
    }

    // Steps are taken in game order: objectives are revealed before the strategy phase starts.
    @Test
    void takesStepsInGameOrder() {
        AiPrompt start = prompt("start", PromptSource.PUBLIC, SETTLED, "startStrategyPhase");
        AiPrompt reveal = prompt("reveal", PromptSource.PUBLIC, SETTLED - 1000, "startOfGameObjReveal");

        Optional<RefereePress> press = RefereeRules.next(test.game, seats, List.of(start, reveal), key -> true, NOW);

        assertThat(press.map(found -> found.button().customId())).contains("startOfGameObjReveal");
    }

    private static AiPrompt resolution(String id, long created, String... labels) {
        return prompt(
                id,
                PromptSource.PUBLIC,
                created,
                java.util.stream.IntStream.range(0, labels.length)
                        .mapToObj(index -> index == 0 ? "agendaResolution_for" : "autoresolve_manual")
                        .toList(),
                List.of(labels));
    }

    // The players need a minute with the shenanigans before the winner is confirmed, and only the plain "Resolve with
    // Current Winner" button is the AI's to press, not the manual resolution next to it.
    @Test
    void confirmsTheAgendaWinnerOnlyAMinuteAfterItWasAnnounced() {
        AiPrompt fresh = resolution("fresh", NOW - 59_000L, "Resolve with Current Winner", "Resolve it Manually");
        assertThat(next(fresh)).isEmpty();

        AiPrompt settled = resolution("settled", NOW - 60_000L, "Resolve with Current Winner", "Resolve it Manually");
        assertThat(next(settled).map(press -> press.button().customId())).contains("agendaResolution_for");
    }

    @Test
    void neverPressesAResolutionOtherThanTheCurrentWinner() {
        AiPrompt other = resolution("other", NOW - 120_000L, "Resolve Sol", "Resolve it Manually");

        assertThat(next(other)).isEmpty();
        assertThat(RefereeRules.anyStep().test(other.buttons().getFirst())).isFalse();
        assertThat(RefereeRules.anyStep()
                        .test(resolution("current", NOW, "Resolve with Current Winner")
                                .buttons()
                                .getFirst()))
                .isTrue();
    }

    private AiPrompt allHaveScoredPrompt() {
        ArgumentCaptor<List<Button>> posted = ArgumentCaptor.captor();
        try (MockedStatic<MessageHelper> messages = Mockito.mockStatic(MessageHelper.class)) {
            ReactionCheckService.respondAllHaveScored(test.game);
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(any(), anyString(), posted.capture()));
        }
        List<PromptButton> buttons = new ArrayList<>();
        for (Button button : posted.getValue()) buttons.add(PromptButton.of(buttons.size(), button));
        return new AiPrompt("main", "all-scored", PromptSource.PUBLIC, "", buttons, SETTLED);
    }

    private Optional<RefereePress> next(AiPrompt... prompts) {
        return RefereeRules.next(test.game, seats, List.of(prompts), key -> true, NOW);
    }
}
