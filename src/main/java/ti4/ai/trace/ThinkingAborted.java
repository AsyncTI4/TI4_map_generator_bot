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

    ThinkingAborted(String reason, List<String> spans) {
        super(reason, null, false, false);
        this.reason = reason;
        this.spans = List.copyOf(spans);
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

    public String trail() {
        return String.join(SEPARATOR, spans);
    }

    public String innermost(int count) {
        if (spans.size() <= count) return trail();
        return "..." + SEPARATOR + String.join(SEPARATOR, spans.subList(spans.size() - count, spans.size()));
    }
}
