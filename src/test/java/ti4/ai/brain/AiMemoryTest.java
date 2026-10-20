package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AiMemoryTest {

    private static final int MANY = 5000;

    private final AiMemory memory = new AiMemory();

    @Test
    void storesAndForgetsValues() {
        memory.put("plan", "attack 302");

        assertThat(memory.get("plan")).contains("attack 302");
        assertThat(memory.has("plan")).isTrue();

        memory.remove("plan");

        assertThat(memory.get("plan")).isEmpty();
        assertThat(memory.has("plan")).isFalse();
    }

    // Memory lives as long as the bot runs, so it is bounded: once full, the oldest entries go first.
    @Test
    void keepsOnlyTheNewestEntriesOnceFull() {
        for (int i = 0; i < MANY; i++) memory.put("key" + i, String.valueOf(i));

        int kept = (int)
                IntStream.range(0, MANY).filter(i -> memory.has("key" + i)).count();

        assertThat(kept).isPositive().isLessThan(MANY);
        assertThat(IntStream.range(MANY - kept, MANY)).allMatch(i -> memory.has("key" + i));
        assertThat(IntStream.range(0, MANY - kept)).noneMatch(i -> memory.has("key" + i));
    }

    @Test
    void aKeyPutAfterOverflowIsKept() {
        for (int i = 0; i < MANY; i++) memory.put("key" + i, String.valueOf(i));

        memory.put("fresh", "value");

        assertThat(memory.get("fresh")).contains("value");
        assertThat(memory.has("key" + (MANY - 1))).isTrue();
    }

    // Overwriting a key keeps a single entry for it rather than growing the memory.
    @Test
    void overwritingAKeyKeepsOneEntry() {
        memory.put("plan", "first");
        memory.put("plan", "second");

        assertThat(memory.get("plan")).contains("second");
    }

    @Test
    void listsTheKeysThatStartWithAPrefix() {
        memory.put("acPlay|msg:1", "a");
        memory.put("acPlay|turn:2", "b");
        memory.put("other|msg:3", "c");

        assertThat(memory.keysStartingWith("acPlay|msg:")).containsExactly("acPlay|msg:1");
        assertThat(memory.keysStartingWith("acPlay|")).containsExactly("acPlay|msg:1", "acPlay|turn:2");
        assertThat(memory.keysStartingWith("none|")).isEmpty();
    }

    // The memory drops the entry that was inserted first, whether or not it was used since, so a value that must
    // outlast newer ones is written again with refresh.
    @Test
    void aRefreshedKeyStaysAmongTheYoungestEntries() {
        memory.put("play", "first");
        for (int i = 0; i < 400; i++) memory.put("key" + i, String.valueOf(i));
        memory.refresh("play", "second");
        for (int i = 400; i < 800; i++) memory.put("key" + i, String.valueOf(i));

        assertThat(memory.get("play")).contains("second");

        memory.put("plain", "first");
        memory.put("plain", "again");
        for (int i = 0; i < MANY; i++) memory.put("filler" + i, String.valueOf(i));

        assertThat(memory.has("plain")).isFalse();
    }
}
