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

        Optional<String> gameLimitProblem = PartyValidator.validateGameLimits(allIds);
        if (gameLimitProblem.isPresent()) return gameLimitProblem;

        queueStore.createUnqueuedGroup(allIds);
        return Optional.empty();
    }

    @Transactional
    public Optional<String> queue(String queuerId, boolean tigl) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        Optional<MatchmakingQueueMember> optionalMember = queueStore.findMember(queuerId);
        if (optionalMember.isPresent()) {
            MatchmakingQueueMember member = optionalMember.get();
            MatchmakingQueueParty party =
                    queueStore.findParty(member.getPartyId()).orElse(null);
            if (party != null) {
                if (party.isQueued()) return Optional.of("You are already queued for a game.");

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

    @Transactional
    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;

        Optional<MatchmakingQueueMember> memberOpt = queueStore.findMember(userId);
        if (memberOpt.isEmpty()) return false;

        long partyId = memberOpt.get().getPartyId();
        queueStore.deleteParties(List.of(partyId));
        return true;
    }

    @Transactional
    public long clearQueue() {
        if (DatabasePersistenceGate.isDisabled()) return 0;
        return queueStore.clearAll();
    }

    @Transactional
    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        Map<Boolean, List<QueuedParty>> queuedPartyByExpired = queueStore.loadQueuedParties().stream()
                .collect(Collectors.partitioningBy(MatchmakerService::isExpired));
        List<QueuedParty> expiredParties = queuedPartyByExpired.get(true);

        if (!expiredParties.isEmpty()) {
            queueStore.deleteParties(expiredParties.stream()
                    .map(queuedParty -> queuedParty.party().getId())
                    .toList());
            MatchmakingNotifier.notifyExpired(expiredParties);
        }

        List<QueuedParty> activeParties = queuedPartyByExpired.get(false);
        List<MatchedGame> gamesToCreate = MatchmakingGrouper.formGames(activeParties);

        if (!gamesToCreate.isEmpty()) {
            queueStore.deleteParties(gamesToCreate.stream()
                    .flatMap(game -> game.parties().stream())
                    .map(MatchmakingQueueParty::getId)
                    .toList());
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
