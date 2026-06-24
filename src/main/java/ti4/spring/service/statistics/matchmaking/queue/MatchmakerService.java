package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;

@Service
@AllArgsConstructor
public class MatchmakerService {

    private final MatchmakingQueueStore queueStore;

    public static boolean isQueueingDisabled() {
        return DatabasePersistenceGate.isDisabled();
    }

    public boolean isUserQueued(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return queueStore.isUserQueued(userId);
    }

    public boolean isUserInParty(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return queueStore.existsForUser(userId);
    }

    public List<String> partyMemberIds(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return List.of(userId);
        return queueStore.partyMemberIds(userId);
    }

    @Transactional
    public Optional<String> formGroup(String creatorId, List<String> memberIds) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        List<String> allIds = PartyValidator.distinctMembers(creatorId, memberIds);

        List<String> alreadyGrouped =
                allIds.stream().filter(queueStore::existsForUser).toList();
        if (!alreadyGrouped.isEmpty()) {
            return Optional.of("These players are already in a group or the queue and must leave first: "
                    + alreadyGrouped.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(", ")));
        }

        Optional<String> hasAvoidListConflict = PartyValidator.hasAvoidListConflicts(allIds);
        if (hasAvoidListConflict.isPresent()) return hasAvoidListConflict;

        queueStore.createUnqueuedGroup(allIds);
        return Optional.empty();
    }

    @Transactional
    public Optional<String> queue(String queuerId) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        Optional<MatchmakingQueueMember> memberOpt = queueStore.findMember(queuerId);
        if (memberOpt.isPresent()) {
            MatchmakingQueueParty party =
                    queueStore.findParty(memberOpt.get().getPartyId()).orElse(null);
            if (party == null) {
                queueStore.deleteMember(memberOpt.get());
            } else if (party.isQueued()) {
                return Optional.of("You are already queued for a game.");
            } else {
                List<String> otherIds = queueStore.membersOf(party.getId()).stream()
                        .map(MatchmakingQueueMember::getUserId)
                        .filter(id -> !id.equals(queuerId))
                        .toList();
                Optional<String> error = PartyValidator.validate(queuerId, otherIds);
                if (error.isPresent()) return error;

                queueStore.markQueued(party, queuerId);
                return Optional.empty();
            }
        }

        Optional<String> error = PartyValidator.validate(queuerId, List.of());
        if (error.isPresent()) return error;

        queueStore.createSoloQueuedParty(queuerId);
        return Optional.empty();
    }

    @Transactional
    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        Optional<MatchmakingQueueMember> memberOpt = queueStore.findMember(userId);
        if (memberOpt.isEmpty()) return false;

        long partyId = memberOpt.get().getPartyId();
        List<MatchmakingQueueMember> partyMembers = queueStore.membersOf(partyId);
        queueStore.deleteParties(List.of(partyId));
        MatchmakingNotifier.notifyPartyRemoved(partyMembers, userId);
        return true;
    }

    @Transactional
    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        Instant now = Instant.now();
        Map<Boolean, List<QueuedParty>> byExpired = queueStore.loadQueuedParties().stream()
                .collect(Collectors.partitioningBy(party -> isExpired(party, now)));
        List<QueuedParty> expired = byExpired.get(true);
        List<QueuedParty> active = byExpired.get(false);

        if (!expired.isEmpty()) {
            queueStore.deleteParties(
                    expired.stream().map(party -> party.party().getId()).toList());
            MatchmakingNotifier.notifyExpired(expired);
        }

        Map<MatchmakingQueueMember, PlayerMatchData> matchData = PlayerMatchDataFactory.buildForParties(active);
        List<MatchedGame> gamesToCreate = MatchmakingGrouper.formGames(active, matchData);

        if (!gamesToCreate.isEmpty()) {
            queueStore.deleteParties(gamesToCreate.stream()
                    .flatMap(game -> game.parties().stream())
                    .map(MatchmakingQueueParty::getId)
                    .toList());
        }

        MatchmakingNotifier.postMatchedGames(gamesToCreate);
    }

    private static boolean isExpired(QueuedParty party, Instant now) {
        Instant expiry = party.party()
                .getQueuedAt()
                .plus(Duration.ofHours(
                        MatchmakingOptions.getHours(party.leaderSettings().getMatchmakingMaxQueueTime())));
        return expiry.isBefore(now);
    }

    public static MatchmakerService get() {
        return SpringContext.getBean(MatchmakerService.class);
    }
}
