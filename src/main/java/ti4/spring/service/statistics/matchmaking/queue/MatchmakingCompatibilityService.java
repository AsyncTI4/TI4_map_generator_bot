package ti4.spring.service.statistics.matchmaking.queue;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

@UtilityClass
class MatchmakingCompatibilityService {

    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;

    private static final double SIMILAR_SKILL_STARTING_THRESHOLD = 0.80;
    private static final double SIMILAR_SKILL_THRESHOLD_DECAY_PER_INTERVAL = 0.10;
    private static final Duration SIMILAR_SKILL_DECAY_INTERVAL = Duration.ofHours(1);
    private static final double SIMILAR_SKILL_MIN_THRESHOLD = 0.30;

    static final int NEW_PLAYER_GAME_THRESHOLD = 3;
    private static final GameInfo GAME_INFO = GameInfo.getDefaultGameInfo();

    static boolean areIncompatible(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        return areIncompatible(a, b, null);
    }

    static boolean areIncompatible(PlayerMatchmakingData a, PlayerMatchmakingData b, String expansion) {
        if (a.avoidList().contains(b.userId()) || b.avoidList().contains(a.userId())) {
            return true;
        }

        List<String> aRestrictions = a.restrictions();
        List<String> bRestrictions = b.restrictions();

        if (MatchmakingOptions.wantsTigl(aRestrictions) != MatchmakingOptions.wantsTigl(bRestrictions)) {
            return true;
        }

        if (expansion != null && MatchmakingOptions.wantsTigl(aRestrictions) && hasDifferentTiglRank(a, b, expansion)) {
            return true;
        }

        if (violatesRoleRestriction(aRestrictions, a.roleNames(), b.roleNames())
                || violatesRoleRestriction(bRestrictions, b.roleNames(), a.roleNames())) {
            return true;
        }

        boolean aIsNew = a.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        boolean bIsNew = b.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        if (bIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(aRestrictions)) return true;
        if (aIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(bRestrictions)) return true;

        if (MatchmakingOptions.wantsSimilarActiveHours(aRestrictions)
                || MatchmakingOptions.wantsSimilarActiveHours(bRestrictions)) {
            long sharedBuckets = a.activeHourBuckets().stream()
                    .filter(b.activeHourBuckets()::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) return true;
        }

        boolean aWantsSimilarSkill = MatchmakingOptions.wantsSimilarPlayerSkill(aRestrictions);
        boolean bWantsSimilarSkill = MatchmakingOptions.wantsSimilarPlayerSkill(bRestrictions);
        if (aWantsSimilarSkill || bWantsSimilarSkill) {
            double skillSimilarity = calculateSkillSimilarity(a, b);
            if (aWantsSimilarSkill && skillSimilarity < getSimilarSkillThreshold(a.queueWait())) {
                return true;
            }
            return bWantsSimilarSkill && skillSimilarity < getSimilarSkillThreshold(b.queueWait());
        }

        return false;
    }

    private static boolean hasDifferentTiglRank(PlayerMatchmakingData a, PlayerMatchmakingData b, String expansion) {
        if (MatchmakingOptions.usesFracturedRank(expansion)) {
            return !a.tiglFracturedRank().equals(b.tiglFracturedRank());
        }
        return !a.tiglRank().equals(b.tiglRank());
    }

    private static double calculateSkillSimilarity(PlayerMatchmakingData a, PlayerMatchmakingData b) {
        List<ITeam> teams =
                List.of(new Team(new Player<>(a.userId()), a.rating()), new Team(new Player<>(b.userId()), b.rating()));
        return new FactorGraphTrueSkillCalculator().calculateMatchQuality(GAME_INFO, teams);
    }

    private static double getSimilarSkillThreshold(Duration waited) {
        long intervalsElapsed = waited.toMinutes() / SIMILAR_SKILL_DECAY_INTERVAL.toMinutes();
        double decayed =
                SIMILAR_SKILL_STARTING_THRESHOLD - SIMILAR_SKILL_THRESHOLD_DECAY_PER_INTERVAL * intervalsElapsed;
        return Math.max(SIMILAR_SKILL_MIN_THRESHOLD, decayed);
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
