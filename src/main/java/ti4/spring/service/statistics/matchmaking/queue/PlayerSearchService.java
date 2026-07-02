package ti4.spring.service.statistics.matchmaking.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.settings.users.UserSettings;
import ti4.spring.context.SpringContext;

@Service
@AllArgsConstructor
public class PlayerSearchService {

    private final MatchmakingQueueStore queueStore;

    public static PlayerSearchService get() {
        return SpringContext.getBean(PlayerSearchService.class);
    }

    @Transactional
    public List<String> searchAndAdd(PlayerSearchCriteria criteria, List<String> existingMemberIds) {
        if (DatabasePersistenceGate.isDisabled()) return List.of();

        int targetSize = maxPlayerCount(criteria.playerCounts());
        int openSlots = targetSize - existingMemberIds.size();
        if (openSlots <= 0) return List.of();

        List<QueuedParty> queued = new ArrayList<>(queueStore.loadQueuedParties());
        queued.sort(Comparator.comparing(party -> party.party().getQueuedAt()));

        Map<MatchmakingQueueMember, PlayerMatchmakingData> partyData =
                PlayerMatchmakingDataFactory.buildForParties(queued);
        Map<String, PlayerMatchmakingData> hostData = PlayerMatchmakingDataFactory.buildForUsers(
                existingMemberIds, criteria.restrictions(), criteria.tigl(), criteria.tiglRanks());

        Set<String> takenUserIds = new HashSet<>(existingMemberIds);
        List<PlayerMatchmakingData> selectedGroup = new ArrayList<>(hostData.values());
        Set<String> commonTiglRanks = criteria.tigl() ? new HashSet<>(criteria.tiglRanks()) : Set.of();

        List<String> addedUserIds = new ArrayList<>();
        List<Long> partiesToRemove = new ArrayList<>();

        for (QueuedParty party : queued) {
            if (addedUserIds.size() >= openSlots) break;
            if (party.size() > openSlots - addedUserIds.size()) continue;
            if (party.members().stream().anyMatch(member -> takenUserIds.contains(member.getUserId()))) continue;
            if (!doesConfigMatch(criteria, party, targetSize)) continue;

            Set<String> partyRanks = partyRanks(party, partyData);
            Set<String> narrowedRanks = null;
            if (criteria.tigl()) {
                narrowedRanks = new HashSet<>(commonTiglRanks);
                narrowedRanks.retainAll(partyRanks);
                if (narrowedRanks.isEmpty()) continue;
            }

            if (!isPartyCompatible(party, partyData, selectedGroup)) continue;

            for (MatchmakingQueueMember member : party.members()) {
                selectedGroup.add(partyData.get(member));
                takenUserIds.add(member.getUserId());
                addedUserIds.add(member.getUserId());
            }
            partiesToRemove.add(party.party().getId());
            if (narrowedRanks != null) commonTiglRanks = narrowedRanks;
        }

        queueStore.deleteParties(partiesToRemove);
        return addedUserIds;
    }

    private static boolean isPartyCompatible(
            QueuedParty party,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> partyData,
            List<PlayerMatchmakingData> selectedGroup) {
        for (MatchmakingQueueMember member : party.members()) {
            PlayerMatchmakingData candidate = partyData.get(member);
            for (PlayerMatchmakingData existing : selectedGroup) {
                if (MatchmakingCompatibilityService.areIncompatible(existing, candidate)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean doesConfigMatch(PlayerSearchCriteria criteria, QueuedParty party, int targetSize) {
        UserSettings settings = party.leaderSettings();
        if (party.size() > targetSize) return false;
        if (!settings.getMatchmakingPlayerCounts().contains(String.valueOf(targetSize))) return false;
        if (Collections.disjoint(criteria.expansions(), settings.getMatchmakingExpansions())) return false;
        if (Collections.disjoint(criteria.victoryPointGoals(), settings.getMatchmakingVictoryPointGoals())) {
            return false;
        }
        return !Collections.disjoint(criteria.paces(), settings.getMatchmakingPaces());
    }

    private static Set<String> partyRanks(
            QueuedParty party, Map<MatchmakingQueueMember, PlayerMatchmakingData> partyData) {
        PlayerMatchmakingData data = partyData.get(party.members().getFirst());
        return data == null ? Set.of() : new HashSet<>(data.tiglRanks());
    }

    private static int maxPlayerCount(List<String> playerCounts) {
        return playerCounts.stream().mapToInt(Integer::parseInt).max().orElse(0);
    }
}
