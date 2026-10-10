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
}
