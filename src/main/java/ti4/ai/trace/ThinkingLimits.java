package ti4.ai.trace;

public record ThinkingLimits(long budgetNanos, int reentryLimit, int depthLimit, int callCap) {

    public static ThinkingLimits unlimited() {
        return new ThinkingLimits(0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    boolean hasBudget() {
        return budgetNanos > 0;
    }
}
