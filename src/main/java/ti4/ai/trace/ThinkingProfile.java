package ti4.ai.trace;

import java.util.List;

public record ThinkingProfile(long totalNanos, List<SpanTotal> spans) {

    public record SpanTotal(String name, long calls, long totalNanos, long selfNanos, int maxReentry) {}

    public static ThinkingProfile empty() {
        return new ThinkingProfile(0, List.of());
    }
}
