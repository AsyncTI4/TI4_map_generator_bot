package ti4.spring.service.statistics.matchmaking.queue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

@UtilityClass
class MatchmakingCompatibilityService {

    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;
    private static final double SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 2.0;
    private static final double RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 4.0;
    private static final int NEW_PLAYER_GAME_THRESHOLD = 3;
    static final BigDecimal NEW_PLAYER_MATCHMAKING_RATING = BigDecimal.valueOf(20.0);

    static Optional<String> incompatibilityReason(PlayerMatchData a, PlayerMatchData b, boolean relaxed) {
        if (a.avoidList().contains(b.userId())) {
            return Optional.of("<@" + a.userId() + "> has <@" + b.userId() + "> on their avoid list."
                    + " Remove them from your avoid list (Additional Settings) to queue with that player.");
        }
        if (b.avoidList().contains(a.userId())) {
            return Optional.of("<@" + b.userId() + "> has <@" + a.userId() + "> on their avoid list."
                    + " Remove them from your avoid list (Additional Settings) to queue with that player.");
        }

        List<String> aRestrictions = a.restrictions();
        List<String> bRestrictions = b.restrictions();

        if (MatchmakingOptions.wantsTigl(aRestrictions) != MatchmakingOptions.wantsTigl(bRestrictions)) {
            return Optional.of("<@" + a.userId() + "> and <@" + b.userId() + "> disagree on the \""
                    + MatchmakingOptions.TIGL_OPTION + "\" restriction."
                    + removeOptionHint(MatchmakingOptions.TIGL_OPTION));
        }

        Optional<String> roleReason = roleViolationReason(aRestrictions, a.roleNames(), b.roleNames(), b.userId());
        if (roleReason.isPresent()) return roleReason;
        roleReason = roleViolationReason(bRestrictions, b.roleNames(), a.roleNames(), a.userId());
        if (roleReason.isPresent()) return roleReason;

        boolean aIsNew = a.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        boolean bIsNew = b.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        if (bIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(aRestrictions)) {
            return Optional.of("<@" + b.userId() + "> is a new async player."
                    + removeOptionHint(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION));
        }
        if (aIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(bRestrictions)) {
            return Optional.of("<@" + a.userId() + "> is a new async player."
                    + removeOptionHint(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION));
        }

        if (MatchmakingOptions.wantsSimilarActiveHours(aRestrictions)
                || MatchmakingOptions.wantsSimilarActiveHours(bRestrictions)) {
            long sharedBuckets = a.activeHourBuckets().stream()
                    .filter(b.activeHourBuckets()::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) {
                return Optional.of(
                        "<@" + a.userId() + "> and <@" + b.userId() + "> don't have similar enough active hours."
                                + removeOptionHint(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION));
            }
        }

        if (MatchmakingOptions.wantsSimilarPlayerSkill(aRestrictions)
                || MatchmakingOptions.wantsSimilarPlayerSkill(bRestrictions)) {
            BigDecimal aRating = aIsNew ? NEW_PLAYER_MATCHMAKING_RATING : a.rating();
            BigDecimal bRating = bIsNew ? NEW_PLAYER_MATCHMAKING_RATING : b.rating();
            double tolerance =
                    relaxed ? RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD : SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
            if (aRating.subtract(bRating).abs().compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                return Optional.of(
                        "<@" + a.userId() + "> and <@" + b.userId() + "> don't have similar enough skill ratings."
                                + removeOptionHint(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION));
            }
        }

        return Optional.empty();
    }

    private static String removeOptionHint(String restrictionOption) {
        return " Remove the \"" + restrictionOption + "\" queue option to queue with that player.";
    }

    private static Optional<String> roleViolationReason(
            List<String> chooserRestrictions,
            Set<String> chooserRoleNames,
            Set<String> otherRoleNames,
            String otherId) {
        if (chooserRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyFloaters(chooserRestrictions)
                && !otherRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)) {
            return Optional.of("<@" + otherId + "> doesn't have the " + MatchmakingOptions.FLOATERS_ROLE_NAME + " role."
                    + removeOptionHint(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION));
        }
        if (chooserRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyWarriors(chooserRestrictions)
                && !otherRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)) {
            return Optional.of("<@" + otherId + "> doesn't have the " + MatchmakingOptions.WARRIORS_ROLE_NAME + " role."
                    + removeOptionHint(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION));
        }
        return Optional.empty();
    }
}
