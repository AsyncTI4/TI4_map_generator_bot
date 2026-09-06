package ti4.spring.service.statistics.matchmaking.queue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    static Optional<String> findJoinBlocker(
            PlayerSearchCriteria criteria, String joiningUserId, List<String> existingMemberIds) {
        List<String> others = existingMemberIds.stream()
                .filter(id -> !id.equals(joiningUserId))
                .toList();

        Optional<String> playerCountBlocker = findPlayerCountBlocker(criteria, others.size());
        if (playerCountBlocker.isPresent()) return playerCountBlocker;

        Optional<String> paceBlocker = findPaceBlocker(criteria, joiningUserId);
        if (paceBlocker.isPresent()) return paceBlocker;

        Optional<String> rankBlocker = findTiglRankBlocker(criteria, joiningUserId);
        if (rankBlocker.isPresent()) return rankBlocker;

        Optional<String> avoidBlocker = findAvoidListBlocker(joiningUserId, others);
        if (avoidBlocker.isPresent()) return avoidBlocker;

        return findActiveHoursBlocker(criteria, joiningUserId, others);
    }

    private static Optional<String> findPlayerCountBlocker(PlayerSearchCriteria criteria, int signedUpCount) {
        int maxPlayers = criteria.maxPlayerCount();
        if (maxPlayers <= 0 || signedUpCount < maxPlayers) return Optional.empty();
        return Optional.of(
                "it is queued for **" + maxPlayers + "** players and already has " + signedUpCount + " signed up.");
    }

    private static Optional<String> findPaceBlocker(PlayerSearchCriteria criteria, String joiningUserId) {
        if (criteria.paces().isEmpty()) return Optional.empty();
        List<String> playablePaces = PartyValidator.getValidPaces(List.of(joiningUserId));
        if (!Collections.disjoint(criteria.paces(), playablePaces)) return Optional.empty();
        return Optional.of("it is queued at a **" + String.join(", ", criteria.paces())
                + "** pace, which requires a game you completed recently enough.");
    }

    private static Optional<String> findTiglRankBlocker(PlayerSearchCriteria criteria, String joiningUserId) {
        if (!criteria.tigl() || criteria.tiglRanks().isEmpty()) return Optional.empty();
        User user = findUser(joiningUserId);
        if (user == null) return Optional.empty();
        if (!TIGLHelper.filterStandardTiglRankOptionsAtOrBelow(user, criteria.tiglRanks())
                .isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("it is queued as a TIGL game for **" + String.join(", ", criteria.tiglRanks()) + "**.");
    }

    private static Optional<String> findAvoidListBlocker(String joiningUserId, List<String> others) {
        List<String> joinerAvoids = UserSettingsManager.get(joiningUserId).getMatchmakingAvoidList();
        for (String otherId : others) {
            if (joinerAvoids.contains(otherId)
                    || UserSettingsManager.get(otherId)
                            .getMatchmakingAvoidList()
                            .contains(joiningUserId)) {
                return Optional.of("you and a player already signed up have each other on an avoid list.");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findActiveHoursBlocker(
            PlayerSearchCriteria criteria, String joiningUserId, List<String> others) {
        if (!MatchmakingOptions.wantsSimilarActiveHours(criteria.restrictions())) return Optional.empty();

        Map<String, PlayerMatchmakingData> dataById = PlayerMatchmakingDataFactory.buildForUsers(
                Stream.concat(Stream.of(joiningUserId), others.stream()).toList(), criteria.restrictions());
        PlayerMatchmakingData joiner = dataById.get(joiningUserId);
        if (!MatchmakingCompatibilityService.hasEnoughActiveHourDataToMatch(joiner)) {
            return Optional.of("it is queued with the **" + MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION
                    + "** restriction and you have not set enough active hours. Use `/user active_hours` to set them.");
        }
        for (String otherId : others) {
            if (!MatchmakingCompatibilityService.shareEnoughActiveHours(joiner, dataById.get(otherId))) {
                return Optional.of(
                        "it is queued with the **" + MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION
                                + "** restriction and your active hours do not overlap enough with the players already signed up.");
            }
        }
        return Optional.empty();
    }

    private static User findUser(String userId) {
        Member member = JdaService.guildPrimary == null ? null : JdaService.guildPrimary.getMemberById(userId);
        return member == null ? null : member.getUser();
    }
}
