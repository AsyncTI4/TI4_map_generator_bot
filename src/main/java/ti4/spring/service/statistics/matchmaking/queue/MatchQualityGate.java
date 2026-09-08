package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.settings.GlobalSettings;

/**
 * Gates match formation on normalized match quality (see {@link MatchQualityCalculator}). The threshold
 * relaxes as the group's longest-waiting member waits, so no one is starved: at the default constants the
 * gate is fully open after 20 hours, before the 24-hour near-match trigger. Shadow mode by default:
 * quality is logged by MatchDescriber but never blocks formation until the global setting is enabled.
 */
@UtilityClass
class MatchQualityGate {

    private static final double NORMALIZED_QUALITY_STARTING_THRESHOLD = 0.5;
    private static final double NORMALIZED_QUALITY_RELAXATION_PER_WINDOW = 0.05;
    private static final Duration QUALITY_THRESHOLD_DECAY_INTERVAL = Duration.ofHours(2);

    static boolean isEnforced() {
        return GlobalSettings.ImplementedSettings.MATCHMAKING_QUALITY_GATE_ENFORCED.getAsBoolean(false);
    }

    static double currentThreshold(Duration maxWait) {
        long intervalsElapsed = maxWait.toMinutes() / QUALITY_THRESHOLD_DECAY_INTERVAL.toMinutes();
        return Math.max(
                0, NORMALIZED_QUALITY_STARTING_THRESHOLD - intervalsElapsed * NORMALIZED_QUALITY_RELAXATION_PER_WINDOW);
    }

    static boolean wouldBlock(double normalizedQuality, Duration maxWait) {
        return normalizedQuality < currentThreshold(maxWait);
    }

    static Duration maxQueueWait(
            List<MatchmakingQueueMember> members, Map<MatchmakingQueueMember, PlayerMatchmakingData> matchmakingData) {
        return members.stream()
                .map(member -> matchmakingData.get(member).queueWait())
                .max(Comparator.naturalOrder())
                .orElse(Duration.ZERO);
    }
}
