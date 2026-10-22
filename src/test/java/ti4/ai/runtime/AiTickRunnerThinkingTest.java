package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
