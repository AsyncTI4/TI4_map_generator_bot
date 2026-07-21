package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

        Optional<String> gameLimitProblem = PartyValidator.validateGameLimits(allIds);
        if (gameLimitProblem.isPresent()) return gameLimitProblem;

        queueStore.createUnqueuedGroup(allIds);
        return Optional.empty();
    }

    public Optional<String> queue(String queuerId, boolean tigl) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        Optional<MatchmakingQueueMember> optionalMember = queueStore.findMember(queuerId);
        if (optionalMember.isPresent()) {
            MatchmakingQueueMember member = optionalMember.get();
            MatchmakingQueueParty party =
                    queueStore.findParty(member.getPartyId()).orElse(null);
            if (party != null) {
                if (party.isQueued()) return Optional.of("You are already queued for a game.");
                if (tigl) return Optional.of("You cannot queue for TIGL as a group. Leave your group first.");

                queueStore.markQueued(party, queuerId, tigl);
                return Optional.empty();
            }
            // This is defensive, in case we have an orphaned member
            queueStore.deleteMember(member);
        }

        // Solo queuer (group queue checks game limits on formation, so need to do so for solo here)
        Optional<String> error = PartyValidator.validateGameLimits(List.of(queuerId));
        if (error.isPresent()) return error;

        queueStore.createSoloQueuedParty(queuerId, tigl);
        return Optional.empty();
    }

    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;

        Optional<MatchmakingQueueMember> memberOpt = queueStore.findMember(userId);
        if (memberOpt.isEmpty()) return false;

        long partyId = memberOpt.get().getPartyId();
        queueStore.deleteParties(List.of(partyId));
        return true;
    }

    public long clearQueue() {
        if (DatabasePersistenceGate.isDisabled()) return 0;
        return queueStore.clearAll();
    }

    public List<String> removePlayer(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return List.of();

        Optional<MatchmakingQueueMember> memberOpt = queueStore.findMember(userId);
        if (memberOpt.isEmpty()) return List.of();

        long partyId = memberOpt.get().getPartyId();
        List<String> removedMemberIds = queueStore.partyMemberIds(userId);
        queueStore.deleteParties(List.of(partyId));
        return removedMemberIds;
    }

    @Transactional(readOnly = true)
    public List<GroupVerificationResult> verifyGroups() {
        if (DatabasePersistenceGate.isDisabled()) return List.of();

        List<GroupVerificationResult> results = new ArrayList<>();
        for (QueuedParty party : queueStore.loadQueuedParties()) {
            List<String> memberIds = party.members().stream()
                    .map(MatchmakingQueueMember::getUserId)
                    .toList();
            List<String> selected = party.leaderSettings().getMatchmakingRestrictions().stream()
                    .filter(MatchmakingOptions.RESTRICTION_OPTIONS::contains)
                    .toList();
            List<String> valid = PartyValidator.getValidRestrictions(memberIds, selected);
            List<String> invalid =
                    selected.stream().filter(r -> !valid.contains(r)).toList();
            results.add(new GroupVerificationResult(
                    party.party().getId(), memberIds, party.party().isTigl(), invalid));
        }
        return results;
    }

    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        List<QueuedParty> expiredParties;
        List<MatchedGame> gamesToCreate;
        MatchmakingQueueLock.LOCK.lock();
        try {
            Map<Boolean, List<QueuedParty>> queuedPartyByExpired = queueStore.loadQueuedParties().stream()
                    .collect(Collectors.partitioningBy(MatchmakerService::isExpired));
            expiredParties = queuedPartyByExpired.get(true);

            if (!expiredParties.isEmpty()) {
                queueStore.deleteParties(expiredParties.stream()
                        .map(queuedParty -> queuedParty.party().getId())
                        .toList());
            }

            List<QueuedParty> activeParties = queuedPartyByExpired.get(false);
            gamesToCreate = MatchmakingGrouper.formGames(activeParties);

            if (!gamesToCreate.isEmpty()) {
                queueStore.deleteParties(gamesToCreate.stream()
                        .flatMap(game -> game.parties().stream())
                        .map(MatchmakingQueueParty::getId)
                        .toList());
            }
        } finally {
            MatchmakingQueueLock.LOCK.unlock();
        }

        if (!expiredParties.isEmpty()) {
            MatchmakingNotifier.notifyExpired(expiredParties);
        }
        MatchmakingNotifier.postMatchedGames(gamesToCreate);
    }

    private static boolean isExpired(QueuedParty queuedParty) {
        return queuedParty
                .party()
                .getQueuedAt()
                .plus(Duration.ofHours(
                        MatchmakingOptions.getHours(queuedParty.leaderSettings().getMatchmakingMaxQueueTime())))
                .isBefore(Instant.now());
    }

    public static MatchmakerService get() {
        return SpringContext.getBean(MatchmakerService.class);
    }
}
