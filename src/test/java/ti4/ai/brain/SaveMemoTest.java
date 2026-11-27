package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;

class SaveMemoTest {

    private final Game game = new Game();
    private final AtomicInteger computed = new AtomicInteger();

    @BeforeEach
    void name() {
        SaveMemo.clear();
        game.setName("savememo");
        game.setLastModifiedDate(100);
    }

    @AfterEach
    void clear() {
        SaveMemo.clear();
    }

    // Each tick reloads the game, so the answer must outlive the decision and the Game instance: two decisions on the
    // same save, even on a freshly loaded copy of it, work the answer out once.
    @Test
    void laterDecisionsOnTheSameSaveReuseTheAnswer() {
        assertThat(decide(game, "state", "nekro")).isEqualTo("answer 1");
        Game reloaded = new Game();
        reloaded.setName("savememo");
        reloaded.setLastModifiedDate(100);

        assertThat(decide(reloaded, "state", "nekro")).isEqualTo("answer 1");
        assertThat(computed).hasValue(1);
    }

    @Test
    void aNewSaveChangedUnsavedStateOrAnotherKeyWorksItOutAgain() {
        decide(game, "state", "nekro");
        decide(game, "state", "sol");
        decide(game, "ledger changed", "sol");
        game.setLastModifiedDate(101);
        decide(game, "ledger changed", "sol");

        assertThat(computed).hasValue(4);
    }

    // Outside a decision, or inside a nested scope such as an imagined rival turn, nothing is shared.
    @Test
    void onlyTheOutermostDecisionScopeShares() {
        SaveMemo.remember(game, "state", this::compute, "k");
        SaveMemo.remember(game, "state", this::compute, "k");
        try (DecisionMemo.Open decision = DecisionMemo.open();
                DecisionMemo.Open imagined = DecisionMemo.open()) {
            SaveMemo.remember(game, "state", this::compute, "k");
            SaveMemo.remember(game, "state", this::compute, "k");
        }

        assertThat(computed).hasValue(4);
    }

    // The self-play harness probes the AI on its own reloaded copy of the game; what it works out there must not reach
    // the next tick, which plays on another instance.
    @Test
    void anIsolatedScopeSharesNothing() {
        try (DecisionMemo.Open probes = DecisionMemo.openIsolated()) {
            SaveMemo.remember(game, "state", this::compute, "k");
            SaveMemo.remember(game, "state", this::compute, "k");
        }
        // An imagined rival turn opened inside the probes is not the outermost decision either.
        try (DecisionMemo.Open probes = DecisionMemo.openIsolated();
                DecisionMemo.Open imagined = DecisionMemo.open()) {
            SaveMemo.remember(game, "state", this::compute, "k");
        }
        assertThat(computed).hasValue(3);
        assertThat(SaveMemo.gamesKept()).isZero();

        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(game, "state", this::compute, "k");
        }
        assertThat(computed).hasValue(4);
    }

    @Test
    void forgettingAGameDropsItsAnswers() {
        decide(game, "state", "nekro");
        SaveMemo.forget("savememo");
        decide(game, "state", "nekro");

        assertThat(computed).hasValue(2);
        assertThat(SaveMemo.gamesKept()).isEqualTo(1);
    }

    // A guard such as an estimate in progress degrades what is worked out under it. An answer one of those guards cut
    // short, when the guard was already held before the answer started, is only handed to later callers that hold
    // that same guard: worked out again there, it would be cut short the same way. Plain callers get a whole answer.
    @Test
    void anAnswerCutShortByAHeldGuardIsServedOnlyWhileThatGuardIsHeld() {
        assertThat(decideHolding(ESTIMATE, () -> cutBy(ESTIMATE))).isEqualTo("answer 1");
        assertThat(decide(game, "state", "k")).isEqualTo("answer 2");
        assertThat(decideHolding(ESTIMATE, () -> cutBy(ESTIMATE))).isEqualTo("answer 1");
        assertThat(decide(game, "state", "k")).isEqualTo("answer 2");
        assertThat(Reentry.idle()).isTrue();
    }

    // Holding a guard the answer never looks at changes nothing, so such an answer is still kept. Turning every held
    // guard into a reason to skip the memo dropped every reuse in a seeded self-play game.
    @Test
    void anAnswerWorkedOutWhileAnUnrelatedGuardIsHeldIsKept() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            holding(ESTIMATE, () -> SaveMemo.remember(game, List.of("state"), this::compute, "test", "k"));
        }

        assertThat(decide(game, "state", "k")).isEqualTo("answer 1");
    }

    // A guard the answer opens itself and then runs into is part of how that answer is always worked out, so it is
    // kept.
    @Test
    void anAnswerThatRunsIntoItsOwnGuardIsKept() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(game, List.of("state"), () -> holding(ESTIMATE, () -> cutBy(ESTIMATE)), "test", "k");
        }

        assertThat(decide(game, "state", "k")).isEqualTo("answer 1");
    }

    // A kept answer that looked at a guard is not handed to a caller holding that guard: worked out there, it would be
    // cut short. The kept answer stays for plain callers.
    @Test
    void aKeptAnswerIsNotServedWhileAGuardItLookedAtIsHeld() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(game, List.of("state"), () -> lookedAt(ESTIMATE), "test", "k");
        }
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            assertThat(holding(
                            ESTIMATE,
                            () -> SaveMemo.remember(game, List.of("state"), () -> cutBy(ESTIMATE), "test", "k")))
                    .isEqualTo("answer 2");
        }

        assertThat(decide(game, "state", "k")).isEqualTo("answer 1");
    }

    // An answer built on a kept answer inherits the guards that one looked at, so it too stays away from a caller
    // holding them.
    @Test
    void anAnswerBuiltOnAKeptAnswerInheritsTheGuardsItLookedAt() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(game, List.of("state"), () -> lookedAt(ESTIMATE), "test", "inner");
        }
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(
                    game,
                    List.of("state"),
                    () -> SaveMemo.remember(game, List.of("state"), this::compute, "test", "inner"),
                    "test",
                    "outer");
        }
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            assertThat(holding(
                            ESTIMATE, () -> SaveMemo.remember(game, List.of("state"), this::compute, "test", "outer")))
                    .isEqualTo("answer 2");
        }
    }

    // The cycle the review found: the outer ranking opens the plan-inputs guard, and a ranking for the same key nested
    // inside it runs into that guard. The nested answer is degraded and is kept only for callers holding that guard;
    // the outer one is whole and serves plain callers.
    @Test
    void anAnswerCutByTheGuardOfTheAnswerAroundItIsKeptOnlyForCallersInsideThatGuard() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(
                    game,
                    List.of("state"),
                    () -> holding(
                            ESTIMATE,
                            () -> SaveMemo.remember(game, List.of("state"), () -> cutBy(ESTIMATE), "test", "inner")),
                    "test",
                    "outer");
        }

        assertThat(decide(game, "state", "outer")).isEqualTo("answer 1");
        assertThat(decide(game, "state", "inner")).isEqualTo("answer 2");
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            assertThat(holding(
                            ESTIMATE,
                            () -> SaveMemo.remember(game, List.of("state"), () -> cutBy(ESTIMATE), "test", "inner")))
                    .isEqualTo("answer 1");
        }
    }

    // The ranking sits behind a per-decision memo too. A hit there still reports the guards the answer looked at, so an
    // answer built on it later in the decision inherits them and stays away from callers holding them.
    @Test
    void aPerDecisionHitStillReportsTheGuardsItsAnswerLookedAt() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            withinDecision("inner", () -> lookedAt(ESTIMATE));
            SaveMemo.remember(game, List.of("state"), () -> withinDecision("inner", this::compute), "test", "outer");
        }
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            assertThat(holding(
                            ESTIMATE, () -> SaveMemo.remember(game, List.of("state"), this::compute, "test", "outer")))
                    .isEqualTo("answer 2");
        }
    }

    // A decision cut short (a thinking limit) leaves no degraded answer behind and no guard or trace open.
    @Test
    void anAbortedAnswerLeavesNothingDegradedBehind() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            SaveMemo.remember(
                    game,
                    List.of("state"),
                    () -> holding(ESTIMATE, () -> {
                        SaveMemo.remember(game, List.of("state"), () -> cutBy(ESTIMATE), "test", "inner");
                        throw new IllegalStateException("cut short");
                    }),
                    "test",
                    "outer");
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("cut short");
        }

        assertThat(decide(game, "state", "inner")).isEqualTo("answer 2");
        assertThat(decide(game, "state", "outer")).isEqualTo("answer 3");
        assertThat(Reentry.idle()).isTrue();
    }

    // The per-decision memo ignores guards: within the decision a ranking cut short under a guard is handed to later
    // callers that do not hold it, as before. An answer built on such a hit took a path a fresh answer would not, so it
    // is not kept for later decisions.
    @Test
    void anAnswerBuiltOnAPerDecisionHitFromAnotherGuardStateIsNotKept() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            holding(ESTIMATE, () -> withinDecision("inner", () -> cutBy(ESTIMATE)));
            assertThat(SaveMemo.remember(
                            game, List.of("state"), () -> withinDecision("inner", this::compute), "test", "outer"))
                    .isEqualTo("answer 1");
        }

        assertThat(decide(game, "state", "outer")).isEqualTo("answer 2");
        assertThat(Reentry.idle()).isTrue();
    }

    private static final String ESTIMATE = "test.estimate";

    private <T> T holding(String guard, Supplier<T> computation) {
        Reentry.enter(guard);
        try {
            return computation.get();
        } finally {
            Reentry.exit(guard);
        }
    }

    private String decideHolding(String guard, Supplier<String> computation) {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            return holding(guard, () -> SaveMemo.remember(game, List.of("state"), computation, "test", "k"));
        }
    }

    private String withinDecision(String key, Supplier<String> computation) {
        return DecisionMemo.remember(() -> SaveMemo.answer(game, List.of("state"), computation, "test", key), key)
                .reported();
    }

    private String cutBy(String guard) {
        Reentry.cutsShort(guard, true);
        return compute();
    }

    private String lookedAt(String guard) {
        Reentry.cutsShort(guard, false);
        return compute();
    }

    private String decide(Game on, Object unsaved, String key) {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            return SaveMemo.remember(on, List.of(unsaved), this::compute, "test", key);
        }
    }

    private String compute() {
        return "answer " + computed.incrementAndGet();
    }
}
