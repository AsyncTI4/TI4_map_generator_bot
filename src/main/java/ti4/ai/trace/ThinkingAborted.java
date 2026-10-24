package ti4.ai.trace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ThinkingAborted extends RuntimeException {

    private static final String SEPARATOR = " > ";
    private static final String AI_PACKAGE = "ti4.ai.";
    private static final int OVERFLOW_FRAMES = 8;

    private final String reason;
    private final List<String> spans;
    private final List<String> keys;

    ThinkingAborted(String reason, List<String> spans) {
        this(reason, spans, List.of());
    }

    ThinkingAborted(String reason, List<String> spans, List<String> keys) {
        super(reason, null, false, false);
        this.reason = reason;
        this.spans = List.copyOf(spans);
        this.keys = List.copyOf(keys);
    }

    public static ThinkingAborted stackOverflow(StackOverflowError overflow) {
        Set<String> frames = new LinkedHashSet<>();
        for (StackTraceElement element : overflow.getStackTrace()) {
            if (frames.size() == OVERFLOW_FRAMES) break;
            if (element.getClassName().startsWith(AI_PACKAGE)) frames.add(frameOf(element));
        }
        return new ThinkingAborted("it ran out of stack", new ArrayList<>(frames).reversed());
    }

    private static String frameOf(StackTraceElement element) {
        String className = element.getClassName();
        return className.substring(className.lastIndexOf('.') + 1) + "." + element.getMethodName();
    }

    public String reason() {
        return reason;
    }

    public List<String> spans() {
        return spans;
    }

    public List<String> keys() {
        return keys;
    }

    public String compactTrail() {
        int bestPeriod = 0;
        int bestRepeats = 1;
        for (int period = 1; period <= spans.size() / 2; period++) {
            int repeats = repeatsAtEnd(period);
            if (repeats > 1 && repeats * period > bestRepeats * bestPeriod) {
                bestPeriod = period;
                bestRepeats = repeats;
            }
        }
        if (bestPeriod == 0) return trail();
        int cycleStart = spans.size() - bestRepeats * bestPeriod;
        List<String> parts = new ArrayList<>(spans.subList(0, cycleStart));
        parts.add("(" + String.join(SEPARATOR, spans.subList(spans.size() - bestPeriod, spans.size())) + ") x"
                + bestRepeats);
        return String.join(SEPARATOR, parts);
    }

    private int repeatsAtEnd(int period) {
        List<String> unit = spans.subList(spans.size() - period, spans.size());
        int repeats = 1;
        while (spans.size() - (repeats + 1) * period >= 0
                && spans.subList(spans.size() - (repeats + 1) * period, spans.size() - repeats * period)
                        .equals(unit)) {
            repeats++;
        }
        return repeats;
    }

    public String trail() {
        return String.join(SEPARATOR, spans);
    }

    public String innermost(int count) {
        if (spans.size() <= count) return trail();
        return "..." + SEPARATOR + String.join(SEPARATOR, spans.subList(spans.size() - count, spans.size()));
    }
}
