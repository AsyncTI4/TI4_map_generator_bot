package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeededObjectivesTest {

    // Three stage one objectives wait in the peekable list; stacking five chosen ones used to grow it to eight, so the
    // status phases kept revealing stage one objectives and never reached stage two.
    @Test
    void stackingKeepsTheNumberOfStageOneObjectivesStillToBeRevealed() {
        List<String> peekable = new ArrayList<>(List.of("a", "b", "c"));
        List<String> deck = new ArrayList<>(List.of("d", "e", "f", "g", "h"));

        SelfPlayGame.stackOnTop(peekable, deck, List.of("e", "a", "g", "h", "x"));

        assertThat(peekable).containsExactly("e", "a", "g");
        assertThat(deck).containsExactly("h", "x", "b", "c", "d", "f");
    }

    @Test
    void stacksOnTheDeckWhenNothingIsPeekable() {
        List<String> peekable = new ArrayList<>();
        List<String> deck = new ArrayList<>(List.of("d", "e", "f"));

        SelfPlayGame.stackOnTop(peekable, deck, List.of("f", "x"));

        assertThat(peekable).isEmpty();
        assertThat(deck).containsExactly("f", "x", "d", "e");
    }
}
