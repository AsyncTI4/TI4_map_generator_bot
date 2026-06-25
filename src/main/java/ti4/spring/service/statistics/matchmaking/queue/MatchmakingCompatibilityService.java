package ti4.spring.service.statistics.matchmaking.queue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

@UtilityClass
class MatchmakingCompatibilityService {

    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;
    private static final double SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 2.0;
    private static final double RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 5.0;
    private static final int NEW_PLAYER_GAME_THRESHOLD = 3;
    static final BigDecimal NEW_PLAYER_MATCHMAKING_RATING = BigDecimal.valueOf(20.0);

    static boolean areIncompatible(PlayerMatchData a, PlayerMatchData b, boolean relaxed) {
        if (a.avoidList().contains(b.userId()) || b.avoidList().contains(a.userId())) {
            return true;
        }

        List<String> aRestrictions = a.restrictions();
        List<String> bRestrictions = b.restrictions();

        if (MatchmakingOptions.wantsTigl(aRestrictions) != MatchmakingOptions.wantsTigl(bRestrictions)) {
            return true;
        }

        if (violatesRoleRestriction(aRestrictions, a.roleNames(), b.roleNames())
                || violatesRoleRestriction(bRestrictions, b.roleNames(), a.roleNames())) {
            return true;
        }

        boolean aIsNew = a.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        boolean bIsNew = b.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        if (bIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(aRestrictions)) {
            return true;
        }
        if (aIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(bRestrictions)) {
            return true;
        }

        if (MatchmakingOptions.wantsSimilarActiveHours(aRestrictions)
                || MatchmakingOptions.wantsSimilarActiveHours(bRestrictions)) {
            long sharedBuckets = a.activeHourBuckets().stream()
                    .filter(b.activeHourBuckets()::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) {
                return true;
            }
        }

        if (MatchmakingOptions.wantsSimilarPlayerSkill(aRestrictions)
                || MatchmakingOptions.wantsSimilarPlayerSkill(bRestrictions)) {
            BigDecimal aRating = aIsNew ? NEW_PLAYER_MATCHMAKING_RATING : a.rating();
            BigDecimal bRating = bIsNew ? NEW_PLAYER_MATCHMAKING_RATING : b.rating();
            double tolerance =
                    relaxed ? RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD : SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
            return aRating.subtract(bRating).abs().compareTo(BigDecimal.valueOf(tolerance)) > 0;
        }

        return false;
    }

    private static boolean violatesRoleRestriction(
            List<String> chooserRestrictions, Set<String> chooserRoleNames, Set<String> otherRoleNames) {
        if (chooserRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyFloaters(chooserRestrictions)
                && !otherRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)) {
            return true;
        }
        return chooserRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyWarriors(chooserRestrictions)
                && !otherRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME);
    }
}
