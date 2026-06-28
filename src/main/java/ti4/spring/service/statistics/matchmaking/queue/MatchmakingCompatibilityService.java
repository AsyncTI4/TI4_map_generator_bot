package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Duration;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

@UtilityClass
class MatchmakingCompatibilityService {

    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;

    private static final double SKILL_DIFFERENCE_STARTING_THRESHOLD = 4;
    private static final double SKILL_DIFFERENCE_WIDENING_PER_WINDOW = 1;
    private static final Duration SIMILAR_SKILL_DECAY_INTERVAL = Duration.ofHours(2);

    private static final int HOURS_TO_AVOID_FLOATERS_WARRIORS = 8;

    static boolean areIncompatible(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        return areIncompatible(a, b, null);
    }

    static boolean areIncompatible(PlayerMatchmakingData a, PlayerMatchmakingData b, String expansion) {
        if (a.avoidList().contains(b.userId()) || b.avoidList().contains(a.userId())) {
            return true;
        }

        List<String> aRestrictions = a.restrictions();
        List<String> bRestrictions = b.restrictions();

        if (MatchmakingOptions.wantsSimilarActiveHours(aRestrictions)
                || MatchmakingOptions.wantsSimilarActiveHours(bRestrictions)) {
            long sharedBuckets = a.activeHourBuckets().stream()
                    .filter(b.activeHourBuckets()::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) return true;
        }

        boolean aWantsTigl = MatchmakingOptions.wantsTigl(aRestrictions);
        boolean bWantsTigl = MatchmakingOptions.wantsTigl(bRestrictions);
        if (aWantsTigl != bWantsTigl) {
            return true;
        }
        if (expansion != null && aWantsTigl && hasDifferentTiglRank(a, b, expansion)) {
            return true;
        }

        // We don't want to block TIGL games on roles/skill
        if (aWantsTigl) return false;

        if (shouldAvoidFloaterOrWarrior(a, b)) {
            return true;
        }

        return hasSimilarSkill(a, b);
    }

    private static boolean hasSimilarSkill(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        Duration maxWait = getMaxQueueWaitTime(a, b);
        double similarSkillWindow = getSimilarSkillWindow(maxWait);

        double aRating = a.rating().getMean();
        double bRating = b.rating().getMean();
        double ratingDifference = Math.abs(aRating - bRating);
        return ratingDifference <= similarSkillWindow;
    }

    private static Duration getMaxQueueWaitTime(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        Duration aWait = a.queueWait();
        Duration bWait = b.queueWait();
        return aWait.compareTo(bWait) >= 0 ? aWait : bWait;
    }

    private static boolean hasDifferentTiglRank(PlayerMatchmakingData a, PlayerMatchmakingData b, String expansion) {
        if (MatchmakingOptions.usesFracturedRank(expansion)) {
            return !a.tiglFracturedRank().equals(b.tiglFracturedRank());
        }
        return !a.tiglRank().equals(b.tiglRank());
    }

    private static double getSimilarSkillWindow(Duration waited) {
        long intervalsElapsed = waited.toMinutes() / SIMILAR_SKILL_DECAY_INTERVAL.toMinutes();
        return SKILL_DIFFERENCE_STARTING_THRESHOLD + intervalsElapsed * SKILL_DIFFERENCE_WIDENING_PER_WINDOW;
    }

    private static boolean shouldAvoidFloaterOrWarrior(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        Duration maxWait = getMaxQueueWaitTime(a, b);
        if (maxWait.toHours() >= HOURS_TO_AVOID_FLOATERS_WARRIORS) return false;

        if (a.roleNames().contains(PlayerMatchDataFactory.FLOATERS_ROLE_NAME)
                && b.roleNames().contains(PlayerMatchDataFactory.WARRIORS_ROLE_NAME)) return true;

        return a.roleNames().contains(PlayerMatchDataFactory.WARRIORS_ROLE_NAME)
                && b.roleNames().contains(PlayerMatchDataFactory.FLOATERS_ROLE_NAME);
    }
}
