package ti4.spring.service.statistics.matchmaking.queue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.helpers.TIGLHelper;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
class QueuedGameJoinValidator {

    static Optional<JoinBlocker> findJoinBlocker(
            PlayerSearchCriteria criteria,
            String joiningUserId,
            List<String> existingMemberIds,
            Set<String> exemptUserIds) {
        List<String> others = otherMembers(existingMemberIds, joiningUserId);

        Optional<JoinBlocker> alwaysEnforced = findAlwaysEnforcedBlocker(criteria, joiningUserId, others);
        if (alwaysEnforced.isPresent()) return alwaysEnforced;

        Optional<JoinBlocker> paceBlocker = findPaceBlocker(criteria, joiningUserId);
        if (paceBlocker.isPresent()) return paceBlocker;

        List<String> nonExemptOthers =
                others.stream().filter(id -> !exemptUserIds.contains(id)).toList();
        return findActiveHoursBlocker(criteria, joiningUserId, nonExemptOthers);
    }

    static Optional<JoinBlocker> findMemberAddBlocker(
            PlayerSearchCriteria criteria, String joiningUserId, List<String> existingMemberIds) {
        return findAlwaysEnforcedBlocker(criteria, joiningUserId, otherMembers(existingMemberIds, joiningUserId));
    }

    static Optional<JoinBlocker> findAvoidListBlocker(String joiningUserId, List<String> existingMemberIds) {
        return findAvoidListBlockerAmong(joiningUserId, otherMembers(existingMemberIds, joiningUserId));
    }

    private static Optional<JoinBlocker> findAlwaysEnforcedBlocker(
            PlayerSearchCriteria criteria, String joiningUserId, List<String> others) {
        Optional<JoinBlocker> playerCountBlocker = findPlayerCountBlocker(criteria, others.size());
        if (playerCountBlocker.isPresent()) return playerCountBlocker;

        Optional<JoinBlocker> rankBlocker = findTiglRankBlocker(criteria, joiningUserId);
        if (rankBlocker.isPresent()) return rankBlocker;

        return findAvoidListBlockerAmong(joiningUserId, others);
    }

    private static List<String> otherMembers(List<String> existingMemberIds, String joiningUserId) {
        return existingMemberIds.stream()
                .filter(id -> !id.equals(joiningUserId))
                .toList();
    }

    private static Optional<JoinBlocker> findPlayerCountBlocker(PlayerSearchCriteria criteria, int signedUpCount) {
        int maxPlayers = criteria.maxPlayerCount();
        if (maxPlayers <= 0 || signedUpCount < maxPlayers) return Optional.empty();
        return blocked(
                "it is queued for **" + maxPlayers + "** players and already has " + signedUpCount + " signed up.");
    }

    private static Optional<JoinBlocker> findPaceBlocker(PlayerSearchCriteria criteria, String joiningUserId) {
        if (criteria.paces().isEmpty()) return Optional.empty();
        List<String> playablePaces = PartyValidator.getValidPaces(List.of(joiningUserId));
        if (!Collections.disjoint(criteria.paces(), playablePaces)) return Optional.empty();
        return blockedButAddable("it is queued at a **" + String.join(", ", criteria.paces())
                + "** pace, which the matchmaker cannot determine you can meet.");
    }

    private static Optional<JoinBlocker> findTiglRankBlocker(PlayerSearchCriteria criteria, String joiningUserId) {
        if (!criteria.tigl() || criteria.tiglRanks().isEmpty()) return Optional.empty();
        User user = findUser(joiningUserId);
        if (user == null) return Optional.empty();
        if (!TIGLHelper.filterStandardTiglRankOptionsAtOrBelow(user, criteria.tiglRanks())
                .isEmpty()) {
            return Optional.empty();
        }
        return blocked("it is queued as a TIGL game for **" + String.join(", ", criteria.tiglRanks()) + "**.");
    }

    private static Optional<JoinBlocker> findAvoidListBlockerAmong(String joiningUserId, List<String> others) {
        List<String> joinerAvoids = UserSettingsManager.get(joiningUserId).getMatchmakingAvoidList();
        for (String otherId : others) {
            if (joinerAvoids.contains(otherId)
                    || UserSettingsManager.get(otherId)
                            .getMatchmakingAvoidList()
                            .contains(joiningUserId)) {
                return blocked("you and a player already signed up have each other on an avoid list.");
            }
        }
        return Optional.empty();
    }

    private static Optional<JoinBlocker> findActiveHoursBlocker(
            PlayerSearchCriteria criteria, String joiningUserId, List<String> others) {
        if (!MatchmakingOptions.wantsSimilarActiveHours(criteria.restrictions())) return Optional.empty();

        Map<String, PlayerMatchmakingData> dataById = PlayerMatchmakingDataFactory.buildForUsers(
                Stream.concat(Stream.of(joiningUserId), others.stream()).toList(), criteria.restrictions());
        PlayerMatchmakingData joiner = dataById.get(joiningUserId);
        if (!MatchmakingCompatibilityService.hasEnoughActiveHourDataToMatch(joiner)) {
            return blockedButAddable("it is queued with the **" + MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION
                    + "** restriction and you have not set enough active hours. Use `/user active_hours` to set them.");
        }
        for (String otherId : others) {
            if (!MatchmakingCompatibilityService.shareEnoughActiveHours(joiner, dataById.get(otherId))) {
                return blockedButAddable(
                        "it is queued with the **" + MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION
                                + "** restriction and your active hours do not overlap enough with the players already signed up.");
            }
        }
        return Optional.empty();
    }

    private static Optional<JoinBlocker> blocked(String reason) {
        return Optional.of(new JoinBlocker(reason, false));
    }

    private static Optional<JoinBlocker> blockedButAddable(String reason) {
        return Optional.of(new JoinBlocker(reason, true));
    }

    private static User findUser(String userId) {
        Member member = JdaService.guildPrimary == null ? null : JdaService.guildPrimary.getMemberById(userId);
        return member == null ? null : member.getUser();
    }
}
