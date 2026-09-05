package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Duration;
import java.util.Collections;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

@UtilityClass
class MatchmakingCompatibilityService {

    private static final int ACTIVE_HOUR_DATA_MARGIN_OVER_SHARED_REQUIREMENT = 2;

    private static final double SKILL_DIFFERENCE_STARTING_THRESHOLD = 3;
    private static final double SKILL_DIFFERENCE_WIDENING_PER_WINDOW = 1;
    private static final Duration SIMILAR_SKILL_DECAY_INTERVAL = Duration.ofHours(2);

    private static final int HOURS_TO_AVOID_FLOATERS_WARRIORS = 8;

    static int requiredActiveHourData(int requiredSharedHours) {
        return requiredSharedHours + ACTIVE_HOUR_DATA_MARGIN_OVER_SHARED_REQUIREMENT;
    }

    static boolean hasEnoughActiveHourDataToMatch(PlayerMatchmakingData data, int requiredSharedHours) {
        return data.activeHours().size() >= requiredActiveHourData(requiredSharedHours);
    }

    static boolean shareEnoughActiveHours(PlayerMatchmakingData a, PlayerMatchmakingData b, int requiredSharedHours) {
        long sharedHours =
                a.activeHours().stream().filter(b.activeHours()::contains).count();
        return sharedHours >= requiredSharedHours;
    }

    static boolean areIncompatible(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        if (a.avoidList().contains(b.userId()) || b.avoidList().contains(a.userId())) {
            return true;
        }

        int requiredSharedHours = strictestRequiredSharedActiveHours(a, b);
        if (requiredSharedHours > 0 && !shareEnoughActiveHours(a, b, requiredSharedHours)) {
            return true;
        }

        // TIGL parties only match other TIGL parties.
        if (a.tigl() != b.tigl()) {
            return true;
        }
        if (a.tigl()) {
            // the grouper enforces a rank common to the whole game.
            // We also don't want to block TIGL games on roles/skill, so we return here.
            return Collections.disjoint(a.tiglRanks(), b.tiglRanks());
        }

        if (shouldAvoidFloaterOrWarrior(a, b)) {
            return true;
        }

        return isSkillGapTooLarge(a, b);
    }

    private static int strictestRequiredSharedActiveHours(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        return Math.max(
                MatchmakingOptions.requiredSharedActiveHours(a.restrictions()),
                MatchmakingOptions.requiredSharedActiveHours(b.restrictions()));
    }

    private static boolean isSkillGapTooLarge(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        Duration maxWait = getMaxQueueWaitTime(a, b);
        double similarSkillWindow = getSimilarSkillWindow(maxWait);

        double aRating = a.rating().getMean();
        double bRating = b.rating().getMean();
        double ratingDifference = Math.abs(aRating - bRating);
        return ratingDifference > similarSkillWindow;
    }

    private static Duration getMaxQueueWaitTime(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        Duration aWait = a.queueWait();
        Duration bWait = b.queueWait();
        return aWait.compareTo(bWait) >= 0 ? aWait : bWait;
    }

    private static double getSimilarSkillWindow(Duration waited) {
        long intervalsElapsed = waited.toMinutes() / SIMILAR_SKILL_DECAY_INTERVAL.toMinutes();
        return SKILL_DIFFERENCE_STARTING_THRESHOLD + intervalsElapsed * SKILL_DIFFERENCE_WIDENING_PER_WINDOW;
    }

    private static boolean shouldAvoidFloaterOrWarrior(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        Duration maxWait = getMaxQueueWaitTime(a, b);
        if (maxWait.toHours() >= HOURS_TO_AVOID_FLOATERS_WARRIORS) return false;

        if (a.roleNames().contains(MatchmakingOptions.FLOATERS_ROLE_NAME)
                && b.roleNames().contains(MatchmakingOptions.WARRIORS_ROLE_NAME)) return true;

        return a.roleNames().contains(MatchmakingOptions.WARRIORS_ROLE_NAME)
                && b.roleNames().contains(MatchmakingOptions.FLOATERS_ROLE_NAME);
    }
}
