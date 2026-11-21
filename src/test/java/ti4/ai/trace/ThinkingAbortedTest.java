package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkingAbortedTest {

    @Test
    void findsTheRepeatingCycleOfAnOverflowInnermostFirst() {
        List<String> frames = new ArrayList<>(List.of("Tip.a", "Tip.b"));
        for (int i = 0; i < 20; i++) frames.addAll(List.of("C.one", "B.two", "A.three"));

        List<String> cycle = ThinkingAborted.repeatingCycle(frames);

        assertThat(cycle).hasSize(3).containsExactlyInAnyOrder("A.three", "B.two", "C.one");
    }

    @Test
    void reportsNoCycleWhenFramesDoNotRepeat() {
        assertThat(ThinkingAborted.repeatingCycle(List.of("A.a", "B.b", "C.c", "D.d", "E.e", "F.f")))
                .isEmpty();
    }

    @Test
    void anOverflowThroughACycleIsRecordedAsThatCycle() {
        StackOverflowError overflow = new StackOverflowError();
        List<StackTraceElement> trace = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            trace.add(new StackTraceElement("ti4.ai.x.Inner", "deep", null, 1));
            trace.add(new StackTraceElement("ti4.ai.y.Outer", "wide", null, 2));
            trace.add(new StackTraceElement("java.util.Optional", "map", null, 3));
        }
        overflow.setStackTrace(trace.toArray(StackTraceElement[]::new));

        ThinkingAborted aborted = ThinkingAborted.stackOverflow(overflow);

        assertThat(aborted.keys()).containsExactly("cycle");
        assertThat(aborted.spans()).containsExactlyInAnyOrder("Inner.deep", "Outer.wide");
    }
}
