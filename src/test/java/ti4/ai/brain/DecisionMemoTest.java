package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DecisionMemoTest {

    private final AtomicInteger computed = new AtomicInteger();

    @Test
    void aDecisionRemembersWhatItWorkedOutAndForgetsItWhenItCloses() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            assertThat(remembered()).isEqualTo(1);
            assertThat(remembered()).isEqualTo(1);
        }
        assertThat(remembered()).isEqualTo(2);
        assertThat(remembered()).isEqualTo(3);
    }

    // The self-play harness probes the AI between ticks, on its own reloaded copy of the game. Its scope remembers like
    // a
    // decision does, but it is not a decision: nothing it works out may reach the memos shared across decisions.
    @Test
    void anIsolatedScopeRemembersButIsNeverTheOutermostDecision() {
        assertThat(DecisionMemo.outermost()).isFalse();
        assertThat(DecisionMemo.isolated()).isFalse();
        try (DecisionMemo.Open probes = DecisionMemo.openIsolated()) {
            assertThat(remembered()).isEqualTo(1);
            assertThat(remembered()).isEqualTo(1);
            assertThat(DecisionMemo.outermost()).isFalse();
            assertThat(DecisionMemo.isolated()).isTrue();
        }
        assertThat(DecisionMemo.isolated()).isFalse();
    }

    // An imagined turn inside a probe opens a scope of its own; it stays isolated.
    @Test
    void aScopeOpenedInsideAnIsolatedOneIsIsolatedToo() {
        try (DecisionMemo.Open probes = DecisionMemo.openIsolated()) {
            try (DecisionMemo.Open imagined = DecisionMemo.open()) {
                assertThat(DecisionMemo.isolated()).isTrue();
                assertThat(DecisionMemo.outermost()).isFalse();
            }
            assertThat(DecisionMemo.isolated()).isTrue();
        }
    }

    @Test
    void anOrdinaryDecisionIsTheOutermostAndNotIsolated() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            assertThat(DecisionMemo.outermost()).isTrue();
            assertThat(DecisionMemo.isolated()).isFalse();
            try (DecisionMemo.Open imagined = DecisionMemo.open()) {
                assertThat(DecisionMemo.outermost()).isFalse();
                assertThat(DecisionMemo.isolated()).isFalse();
            }
        }
    }

    private int remembered() {
        return DecisionMemo.remember(computed::incrementAndGet, "decisionmemo", "k");
    }
}
