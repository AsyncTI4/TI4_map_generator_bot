package ti4.ai.selfplay;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.selfplay.RefereeRules.RefereePress;
import ti4.game.Player;
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
        AiPrompt reveal = prompt("reveal", PromptSource.PUBLIC, SETTLED, "reveal_stage_2", "gameEnd");

        assertThat(RefereeRules.trackExhausted(test.game)).isTrue();
        assertThat(next(reveal)).isEmpty();
        assertThat(RefereeRules.offersGameEnd(List.of(reveal))).isTrue();
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
        AiPrompt end = prompt("end", PromptSource.PUBLIC, SETTLED, "gameEnd", "rematch");

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

    private Optional<RefereePress> next(AiPrompt... prompts) {
        return RefereeRules.next(test.game, seats, List.of(prompts), key -> true, NOW);
    }
}
