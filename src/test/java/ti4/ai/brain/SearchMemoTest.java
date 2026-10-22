package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearchMemoTest {

    private static int count(AtomicInteger calls, Object... key) {
        return SearchMemo.remember(calls::incrementAndGet, key);
    }

    @Test
    void computesEveryTimeWithoutAScope() {
        AtomicInteger calls = new AtomicInteger();

        count(calls, "a", 1);
        count(calls, "a", 1);

        assertThat(calls).hasValue(2);
    }

    @Test
    void computesOncePerKeyInsideAScope() {
        AtomicInteger calls = new AtomicInteger();

        try (SearchMemo.Open ignored = SearchMemo.open()) {
            assertThat(count(calls, "a", 1)).isEqualTo(count(calls, "a", 1));
            count(calls, "a", 2);
            count(calls, "b", 1);
        }

        assertThat(calls).hasValue(3);
    }

    @Test
    void forgetsEverythingWhenTheScopeCloses() {
        AtomicInteger calls = new AtomicInteger();
        try (SearchMemo.Open ignored = SearchMemo.open()) {
            count(calls, "a");
        }

        try (SearchMemo.Open ignored = SearchMemo.open()) {
            count(calls, "a");
        }
        count(calls, "a");

        assertThat(calls).hasValue(3);
    }

    @Test
    void anInnerScopeStartsEmptyAndRestoresTheOuterOne() {
        AtomicInteger calls = new AtomicInteger();

        try (SearchMemo.Open outer = SearchMemo.open()) {
            count(calls, "a");
            try (SearchMemo.Open inner = SearchMemo.open()) {
                count(calls, "a");
                count(calls, "a");
            }
            count(calls, "a");
        }

        assertThat(calls).hasValue(2);
    }

    @Test
    void aFailedSearchStillClosesItsScope() {
        AtomicInteger calls = new AtomicInteger();

        try (SearchMemo.Open ignored = SearchMemo.open()) {
            throw new IllegalStateException("search failed");
        } catch (IllegalStateException expected) {
            count(calls, "a");
            count(calls, "a");
        }

        assertThat(calls).hasValue(2);
    }

    @Test
    void aValueThatComputesAnotherInsideTheScopeIsStored() {
        try (SearchMemo.Open ignored = SearchMemo.open()) {
            AtomicInteger inner = new AtomicInteger();

            int outer = SearchMemo.remember(() -> count(inner, "inner") + 10, "outer");

            assertThat(outer).isEqualTo(11);
            assertThat(SearchMemo.<Integer>remember(() -> -1, "outer")).isEqualTo(11);
        }
    }
}
