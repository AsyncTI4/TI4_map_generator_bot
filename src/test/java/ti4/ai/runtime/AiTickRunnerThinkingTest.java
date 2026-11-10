package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.actuation.AiActuator.SeatIdentity;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.FactionBrain;
import ti4.ai.fallback.FallbackLog;
import ti4.ai.fallback.FallbackRecord;
import ti4.ai.profile.AiProfile;
import ti4.ai.runtime.AiTickRunner.SeatTick;
import ti4.ai.trace.Thinking;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.testUtils.BaseTi4Test;

// A decision that never settles is cut short instead of hanging the tick: the seat idles, the table is told why, and
// the same expensive decision is not tried again until the game has changed.
class AiTickRunnerThinkingTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;

    private AiTestGame test;
    private AiLane lane;
    private SeatTick tick;
    private AiTurnContext context;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        lane = new AiLane(test.game.getName());
        AiProfile profile = AiProfile.of(test.nekro);
        tick = new SeatTick(
                lane,
                lane.seat(test.nekro.getUserID()),
                test.game,
                test.nekro,
                new SeatIdentity(test.nekro.getUserID(), "nekro", "Nekro AI", ""),
                profile,
                List.of(),
                false,
                NOW);
        context = new AiTurnContext(test.game, test.nekro, profile, List.of(), Set.of(), test.memory, NOW);
    }

    @Test
    void aDecisionThatRecursesBetweenPlannersIsCutShortAndRecorded() {
        AtomicInteger calls = new AtomicInteger();

        AiDecision decision = AiTickRunner.decide(tick, brain(ctx -> recurse(calls, 0)), context);

        assertThat(decision).isInstanceOf(AiDecision.Idle.class);
        List<FallbackRecord> records = FallbackLog.recent(test.game);
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().kind()).isEqualTo("THINKING_ABORTED");
        assertThat(records.getFirst().reason()).contains("planner.a").contains("re-entered itself 8 deep");
        assertThat(records.getFirst().pendingDecision()).startsWith("...").contains("planner.b > planner.a");
        assertThat(lane.thinking().cutShort()).isEqualTo(1);
        assertThat(calls.get()).isLessThan(30);
    }

    // A seat that keeps hitting the same cycle would log it on every retry (130 lines in aiselfplay737459). The full
    // report is logged once per seat and round; the lane keeps the latest one and the count for the stall report.
    @Test
    void anAbortIsLoggedOncePerRoundWithItsPathKeysAndInputs() {
        test.nekro.setActionCard("overrule", 412);
        FactionBrain stuck = brain(ctx -> recurse(new AtomicInteger(), 0));
        List<String> warnings = new ArrayList<>();
        try (MockedStatic<BotLogger> logger = Mockito.mockStatic(BotLogger.class)) {
            logger.when(() -> BotLogger.warning(anyString())).thenAnswer(call -> warnings.add(call.getArgument(0)));

            AiTickRunner.decide(tick, stuck, context);
            test.nekro.setTg(test.nekro.getTg() + 1);
            AiTickRunner.decide(tick, stuck, context);
            test.game.setRound(test.game.getRound() + 1);
            test.nekro.setTg(test.nekro.getTg() + 1);
            AiTickRunner.decide(tick, stuck, context);
        }

        assertThat(warnings).hasSize(2);
        assertThat(warnings.getFirst())
                .startsWith("AI thinking aborted game=" + test.game.getName() + " round=2 phase=action seat=nekro")
                .contains("path=[planner.a > (planner.b > planner.a) x")
                .contains("calls=[planner.a x")
                .contains("cards [overrule]")
                .contains("explore cultural ");
        assertThat(lane.lastAbort())
                .startsWith("3 aborts, last: AI thinking aborted")
                .contains("round=3");
    }

    // When every AI seat keeps cutting its thinking short, nobody can move the game on and the per-round warning is
    // easy to miss. One error is logged for the storm, and again only after some seat has decided normally.
    @Test
    void anAbortStormAcrossEverySeatIsLoggedOnceAsAnError() {
        AiTestGame table = AiTestGame.withSolAi();
        table.game.setPhaseOfGame("action");
        AiLane stormLane = new AiLane(table.game.getName());
        FactionBrain stuck = brain(ctx -> recurse(new AtomicInteger(), 0));
        FactionBrain fine = brain(ctx -> AiDecision.idle());
        List<String> errors = new ArrayList<>();
        try (MockedStatic<BotLogger> logger = Mockito.mockStatic(BotLogger.class)) {
            logger.when(() -> BotLogger.error(anyString())).thenAnswer(call -> errors.add(call.getArgument(0)));

            for (int i = 0; i < AiSettings.ABORT_STORM_LIMIT; i++) {
                decideFor(stormLane, table, table.nekro, stuck);
                assertThat(errors).isEmpty();
                decideFor(stormLane, table, table.sol, stuck);
            }
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst())
                    .contains("every AI seat in " + table.game.getName())
                    .contains("last: AI thinking aborted");

            decideFor(stormLane, table, table.nekro, stuck);
            decideFor(stormLane, table, table.sol, stuck);
            assertThat(errors).hasSize(1);

            decideFor(stormLane, table, table.nekro, fine);
            decideFor(stormLane, table, table.sol, stuck);
            assertThat(errors).hasSize(1);
            for (int i = 0; i < AiSettings.ABORT_STORM_LIMIT; i++) {
                decideFor(stormLane, table, table.nekro, stuck);
            }
            assertThat(errors).hasSize(2);
        }
    }

    // A seat stuck with no prompt to hand over says in the fallback log that its thinking was cut short just before.
    @Test
    void theStuckRecordNamesARecentAbort() {
        test.game.setActivePlayerID(test.nekro.getUserID());
        test.game.setLastActivePlayerChange(new Date(NOW - 1000));
        lane.noteAbort("AI thinking aborted game=x seat=nekro", NOW);
        lane.requestDelegation(test.nekro.getUserID(), false);

        try (MockedStatic<BotLogger> logger = Mockito.mockStatic(BotLogger.class)) {
            AiTickRunner.handleIdle(tick);
        }

        assertThat(FallbackLog.recent(test.game))
                .extracting(FallbackRecord::outcome)
                .containsExactly("stuck: no prompt it can hand over after thinking was cut short:"
                        + " AI thinking aborted game=x seat=nekro");
    }

    private static void decideFor(AiLane lane, AiTestGame table, Player seat, FactionBrain brain) {
        seat.setTg(seat.getTg() + 1);
        AiProfile profile = AiProfile.of(seat);
        SeatTick seatTick = new SeatTick(
                lane,
                lane.seat(seat.getUserID()),
                table.game,
                seat,
                new SeatIdentity(seat.getUserID(), seat.getFaction(), seat.getUserName(), ""),
                profile,
                List.of(),
                true,
                NOW);
        AiTickRunner.decide(
                seatTick,
                brain,
                new AiTurnContext(table.game, seat, profile, List.of(), Set.of(), table.memoryOf(seat), NOW));
    }

    @Test
    void aRealStackOverflowIsCaughtAtTheTickAndRecordedWithTheCyclingMethods() {
        AiDecision decision = AiTickRunner.decide(tick, brain(ctx -> overflow(0)), context);

        assertThat(decision).isInstanceOf(AiDecision.Idle.class);
        FallbackRecord record = FallbackLog.recent(test.game).getFirst();
        assertThat(record.kind()).isEqualTo("THINKING_ABORTED");
        assertThat(record.reason()).isEqualTo("it ran out of stack");
        assertThat(record.pendingDecision()).contains("AiTickRunnerThinkingTest.overflow");
    }

    @Test
    void theSameDecisionIsNotRetriedWhileTheGameIsUnchangedButIsOnceItChanges() {
        AtomicInteger calls = new AtomicInteger();
        FactionBrain stuck = brain(ctx -> {
            calls.incrementAndGet();
            return recurse(new AtomicInteger(), 0);
        });

        AiTickRunner.decide(tick, stuck, context);
        AiTickRunner.decide(tick, stuck, context);
        AiTickRunner.decide(tick, stuck, context);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(FallbackLog.recent(test.game)).hasSize(1);

        test.nekro.setTg(test.nekro.getTg() + 3);
        AiTickRunner.decide(tick, stuck, context);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aStuckDecisionIsTriedAgainAfterTheRetryDelayEvenIfNothingChanged() {
        AtomicInteger calls = new AtomicInteger();
        FactionBrain stuck = brain(ctx -> {
            calls.incrementAndGet();
            return recurse(new AtomicInteger(), 0);
        });
        AiTickRunner.decide(tick, stuck, context);

        SeatTick later = new SeatTick(
                lane,
                tick.state(),
                tick.game(),
                tick.seat(),
                tick.identity(),
                tick.profile(),
                tick.prompts(),
                false,
                NOW + AiSettings.THINKING_RETRY.toMillis() + 1);
        AiTickRunner.decide(later, stuck, context);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void anOrdinaryDecisionIsReturnedAndTimedWithoutBeingRecordedAsCutShort() {
        AiDecision idle = AiDecision.idle();

        AiDecision decision = AiTickRunner.decide(
                tick,
                brain(ctx -> {
                    try (Thinking.Span span = Thinking.enter("planner.a")) {
                        return idle;
                    }
                }),
                context);

        assertThat(decision).isSameAs(idle);
        assertThat(lane.thinking().decisions()).isEqualTo(1);
        assertThat(lane.thinking().cutShort()).isZero();
        assertThat(FallbackLog.recent(test.game)).isEmpty();
        assertThat(lane.thinking().lines(3, 1)).anyMatch(line -> line.startsWith("planner.a: 1 calls"));
    }

    @Test
    void aBrainBugIsStillLoggedAsAnIdleDecisionAndLeavesNoContextBehind() {
        AiDecision decision = AiTickRunner.decide(
                tick,
                brain(ctx -> {
                    throw new IllegalStateException("boom");
                }),
                context);

        assertThat(decision).isInstanceOf(AiDecision.Idle.class);
        assertThat(lane.thinking().cutShort()).isZero();
        assertThat(FallbackLog.recent(test.game)).isEmpty();
        for (int i = 0; i < 100; i++) Thinking.enter("planner.a").close();
    }

    private static AiDecision recurse(AtomicInteger calls, int depth) {
        calls.incrementAndGet();
        try (Thinking.Span span = Thinking.enter("planner.a")) {
            try (Thinking.Span inner = Thinking.enter("planner.b")) {
                return recurse(calls, depth + 1);
            }
        }
    }

    private static AiDecision overflow(int depth) {
        return overflow(depth + 1);
    }

    private static FactionBrain brain(Function<AiTurnContext, AiDecision> decide) {
        return new FactionBrain() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public Set<String> publicWindowHandlerPrefixes() {
                return Set.of();
            }

            @Override
            public AiDecision decide(AiTurnContext context) {
                return decide.apply(context);
            }
        };
    }
}
