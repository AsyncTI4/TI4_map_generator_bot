package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    private String decide(Game on, Object unsaved, String key) {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            return SaveMemo.remember(on, List.of(unsaved), this::compute, "test", key);
        }
    }

    private String compute() {
        return "answer " + computed.incrementAndGet();
    }
}
