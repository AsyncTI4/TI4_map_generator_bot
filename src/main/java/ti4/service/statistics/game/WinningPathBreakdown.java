package ti4.service.statistics.game;

import java.util.Map;

public record WinningPathBreakdown(
        int stage1s, int stage2s, int secrets, int supports, Map<String, Integer> otherPoints) {

    public static final String SEED = "seed";
    public static final String MUTINY = "mutiny";
    public static final String SHARD = "shard";
    public static final String CUSTODIAN_OR_IMPERIAL = "custodian/imperial";
    public static final String IMPERIAL_RIDER = "imperial rider";
    public static final String CENSURE = "censure";
    public static final String CROWN = "crown";
    public static final String LATVINIA = "latvinia";
    public static final String STYX = "styx";
    public static final String UNRECOGNIZED = "other (probably _Classified Document Leaks_)";

    public int custodians() {
        return pointsFrom(CUSTODIAN_OR_IMPERIAL);
    }

    public int others() {
        return otherPoints.entrySet().stream()
                .filter(entry -> !CUSTODIAN_OR_IMPERIAL.equals(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public int pointsFrom(String pointSource) {
        return otherPoints.getOrDefault(pointSource, 0);
    }

    public boolean scored(String pointSource) {
        return pointsFrom(pointSource) > 0;
    }
}
