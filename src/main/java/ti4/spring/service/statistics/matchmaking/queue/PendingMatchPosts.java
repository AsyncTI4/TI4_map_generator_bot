package ti4.spring.service.statistics.matchmaking.queue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class PendingMatchPosts {

    private final Set<Long> heldPartyIds = ConcurrentHashMap.newKeySet();

    void hold(Collection<Long> partyIds) {
        heldPartyIds.addAll(partyIds);
    }

    void release(Collection<Long> partyIds) {
        heldPartyIds.removeAll(partyIds);
    }

    List<QueuedParty> withoutHeld(List<QueuedParty> parties) {
        return parties.stream()
                .filter(party -> !heldPartyIds.contains(party.party().getId()))
                .toList();
    }
}
