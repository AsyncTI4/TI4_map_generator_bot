package ti4.spring.service.statistics.matchmaking.queue;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

@UtilityClass
class MatchmakingCompatibilityService {

    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;
    private static final double MIN_MATCH_QUALITY = 0.70;
    private static final double RELAXED_MIN_MATCH_QUALITY = 0.5;
    private static final int NEW_PLAYER_GAME_THRESHOLD = 3;
    private static final GameInfo GAME_INFO = GameInfo.getDefaultGameInfo();

    static boolean areIncompatible(PlayerMatchmakingData a, PlayerMatchmakingData b, boolean relaxed) {
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

            List<ITeam> teams = List.of(
                    new Team(new Player<>(a.userId()), a.rating()), new Team(new Player<>(b.userId()), b.rating()));
            double matchQuality = new FactorGraphTrueSkillCalculator().calculateMatchQuality(GAME_INFO, teams);

            double minQuality = relaxed ? RELAXED_MIN_MATCH_QUALITY : MIN_MATCH_QUALITY;
            return matchQuality < minQuality;
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
