package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessageHelperTest {

    private static final String OVERRULE = "- **Overrule:**\n  - 100.0 Impact Score\n";
    private static final String LIE_IN_WAIT = "- **Lie in Wait:**\n  - 55.7 Impact Score\n";

    @Test
    void shouldKeepABlockWholeRatherThanFillTheMessage() {
        // Both cards would fit inside 60 characters if the second were allowed to break, which is
        // the thing to avoid - Discord reads bullets that arrive without the line naming the card
        // as a list of their own.
        List<String> messages = MessageHelper.packBlocksIntoMessages(List.of(OVERRULE, LIE_IN_WAIT), 60);

        assertThat(messages).containsExactly(OVERRULE, LIE_IN_WAIT);
    }

    @Test
    void shouldPackEveryBlockThatFitsIntoOneMessage() {
        List<String> messages = MessageHelper.packBlocksIntoMessages(List.of(OVERRULE, LIE_IN_WAIT), 2000);

        assertThat(messages).containsExactly(OVERRULE + LIE_IN_WAIT);
    }

    @Test
    void shouldFillEachMessageBeforeStartingTheNext() {
        List<String> messages = MessageHelper.packBlocksIntoMessages(List.of("a\n", "b\n", "c\n", "d\n"), 4);

        assertThat(messages).containsExactly("a\nb\n", "c\nd\n");
    }

    @Test
    void shouldSplitABlockThatCannotBeKeptWhole() {
        // Nothing can keep a block longer than a whole message together, so it breaks - but it must
        // not drag the block after it into the mess, and no message may come back empty.
        List<String> messages = MessageHelper.packBlocksIntoMessages(List.of("aaaa\nbbbb\ncccc\n", "d\n"), 10);

        assertThat(messages).containsExactly("aaaa\nbbbb\n", "cccc\n", "d\n");
    }

    @Test
    void shouldStartAFreshMessageForAnOversizedBlock() {
        // The oversized block cannot join the message being filled, or the split would run past
        // the limit once the earlier block is counted.
        List<String> messages = MessageHelper.packBlocksIntoMessages(List.of("a\n", "bbbb\ncccc\n"), 10);

        assertThat(messages).containsExactly("a\n", "bbbb\ncccc\n");
    }

    @Test
    void shouldIgnoreBlocksWithNothingInThem() {
        List<String> messages = MessageHelper.packBlocksIntoMessages(List.of("a\n", "", "b\n"), 2000);

        assertThat(messages).containsExactly("a\nb\n");
    }

    @Test
    void shouldReturnNoMessagesForNoBlocks() {
        assertThat(MessageHelper.packBlocksIntoMessages(List.of(), 2000)).isEmpty();
        assertThat(MessageHelper.packBlocksIntoMessages(null, 2000)).isEmpty();
    }

    @Test
    void shouldSplitFlatTextOnTheLastNewline() {
        String message = "first line\nsecond line\nthird line\n";

        List<String> parts = MessageHelper.splitLargeText(message, 22);

        assertThat(parts).containsExactly("first line\n", "second line\n", "third line\n");
    }

    @Test
    void shouldReturnTheWholeMessageWhenItFits() {
        assertThat(MessageHelper.splitLargeText(OVERRULE, 2000)).containsExactly(OVERRULE);
    }
}
