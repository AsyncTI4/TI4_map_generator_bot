package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.settings.users.UserSettingsManager;

@Service
@AllArgsConstructor
public class MatchmakingQueueStore {

    private final MatchmakingQueuePartyRepository partyRepository;
    private final MatchmakingQueueMemberRepository memberRepository;

    Optional<MatchmakingQueueMember> findMember(String userId) {
        return memberRepository.findByUserId(userId);
    }

    boolean existsForUser(String userId) {
        return memberRepository.existsByUserId(userId);
    }

    Optional<MatchmakingQueueParty> findParty(long partyId) {
        return partyRepository.findById(partyId);
    }

    void deleteMember(MatchmakingQueueMember member) {
        memberRepository.delete(member);
    }

    boolean isUserQueued(String userId) {
        return memberRepository
                .findByUserId(userId)
                .flatMap(member -> partyRepository.findById(member.getPartyId()))
                .map(MatchmakingQueueParty::isQueued)
                .orElse(false);
    }

    List<String> partyMemberIds(String userId) {
        return memberRepository
                .findByUserId(userId)
                .map(member -> memberRepository.findAllByPartyId(member.getPartyId()).stream()
                        .map(MatchmakingQueueMember::getUserId)
                        .toList())
                .filter(ids -> !ids.isEmpty())
                .orElse(List.of(userId));
    }

    void createUnqueuedGroup(List<String> userIds) {
        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setQueued(false);
        long partyId = partyRepository.save(party).getId();
        saveMembers(userIds, partyId);
    }

    void markQueued(MatchmakingQueueParty party, String leaderId) {
        party.setLeaderId(leaderId);
        party.setQueued(true);
        party.setQueuedAt(Instant.now());
        partyRepository.save(party);
    }

    void createSoloQueuedParty(String leaderId) {
        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setQueued(true);
        party.setQueuedAt(Instant.now());
        party.setLeaderId(leaderId);
        long partyId = partyRepository.save(party).getId();
        saveMembers(List.of(leaderId), partyId);
    }

    private void saveMembers(List<String> userIds, long partyId) {
        List<MatchmakingQueueMember> members = userIds.stream()
                .map(id -> {
                    MatchmakingQueueMember member = new MatchmakingQueueMember();
                    member.setUserId(id);
                    member.setPartyId(partyId);
                    return member;
                })
                .toList();
        memberRepository.saveAll(members);
    }

    void deleteParties(Collection<Long> partyIds) {
        if (partyIds.isEmpty()) return;
        memberRepository.deleteAllByPartyIdIn(partyIds);
        partyRepository.deleteAllById(partyIds);
    }

    List<QueuedParty> loadQueuedParties() {
        List<MatchmakingQueueParty> parties = partyRepository.findAllByQueuedTrueOrderByQueuedAtAsc();
        if (parties.isEmpty()) return List.of();

        List<Long> partyIds = parties.stream().map(MatchmakingQueueParty::getId).toList();
        Map<Long, List<MatchmakingQueueMember>> membersByParty = memberRepository.findAllByPartyIdIn(partyIds).stream()
                .collect(Collectors.groupingBy(MatchmakingQueueMember::getPartyId));

        List<QueuedParty> queuedParties = new ArrayList<>();
        for (MatchmakingQueueParty party : parties) {
            List<MatchmakingQueueMember> members = membersByParty.getOrDefault(party.getId(), List.of());
            if (members.isEmpty()) continue;
            queuedParties.add(new QueuedParty(party, members, UserSettingsManager.get(party.getLeaderId())));
        }
        return queuedParties;
    }
}
