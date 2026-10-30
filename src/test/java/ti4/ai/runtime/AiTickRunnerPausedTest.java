package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actuation.AiActuator.SeatIdentity;
import ti4.ai.fallback.FallbackLog;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.profile.AiProfile;
import ti4.ai.runtime.AiTickRunner.SeatResult;
import ti4.ai.runtime.AiTickRunner.SeatTick;
import ti4.testUtils.BaseTi4Test;

// A paused AI makes no decisions, and that includes the stall detection that hands its choice to the table.
class AiTickRunnerPausedTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final long LONG_AGO = NOW - 3_600_000L;

    private AiTestGame test;
    private AiLane lane;
    private List<AiPrompt> prompts;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.game.setActivePlayerID(test.nekro.getUserID());
        test.game.setLastActivePlayerChange(new Date(LONG_AGO));
        lane = new AiLane(test.game.getName());
        AiPrompt turn = new AiPrompt(
                "channel",
                "message",
                PromptSource.PUBLIC,
                "",
                List.of(PromptButton.of(0, Button.secondary("FFCC_nekro_tacticalAction", "Tactical"))),
                LONG_AGO);
        prompts = List.of(turn);
    }

    @Test
    void aPausedSeatHandsNothingToTheTableHoweverLongTheGameHasWaited() {
        pause();
        lane.seat(test.nekro.getUserID()).stallAge("its turn", LONG_AGO);

        SeatResult result = AiTickRunner.handleIdle(tick(NOW));

        assertThat(result.acted()).isFalse();
        assertThat(FallbackLog.recent(test.game)).isEmpty();
    }

    @Test
    void timeSpentPausedDoesNotCountTowardsTheStallOnResume() {
        pause();
        AiSeatState state = lane.seat(test.nekro.getUserID());
        state.stallAge("its turn", LONG_AGO);

        AiTickRunner.handleIdle(tick(NOW));
        resume();

        assertThat(state.stallAge("its turn", NOW + 1_000L)).isZero();
    }

    @Test
    void resumingClearsAStallTimerThatNoTickTouchedWhilePaused() {
        AiSeatState state = lane.seat(test.nekro.getUserID());
        state.stallAge("its turn", LONG_AGO);

        state.clearStall();

        assertThat(state.stallAge("its turn", NOW)).isZero();
    }

    @Test
    void aPlayerWhoAsksExplicitlyStillGetsTheChoiceHandedOver() {
        pause();
        lane.seat(test.nekro.getUserID()).requestDelegation(true);

        AiTickRunner.handleIdle(tick(NOW));

        assertThat(FallbackLog.recent(test.game)).hasSize(1);
        assertThat(FallbackLog.recent(test.game).getFirst().kind()).isEqualTo("REQUESTED");
    }

    @Test
    void anUnpausedSeatThatHasWaitedPastTheThresholdFallsBack() {
        lane.seat(test.nekro.getUserID()).stallAge("its turn", LONG_AGO);

        AiTickRunner.handleIdle(tick(NOW));

        assertThat(FallbackLog.recent(test.game)).hasSize(1);
    }

    private void pause() {
        AiProfile.of(test.nekro).withPause(AiProfile.PauseState.MANUAL).writeTo(test.nekro);
    }

    private void resume() {
        AiProfile.of(test.nekro).withPause(AiProfile.PauseState.RUNNING).writeTo(test.nekro);
        lane.seat(test.nekro.getUserID()).clearStall();
    }

    private SeatTick tick(long now) {
        return new SeatTick(
                lane,
                lane.seat(test.nekro.getUserID()),
                test.game,
                test.nekro,
                new SeatIdentity(test.nekro.getUserID(), "nekro", "Nekro AI", ""),
                AiProfile.of(test.nekro),
                prompts,
                false,
                now);
    }
}
