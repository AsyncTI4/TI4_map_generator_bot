package ti4.spring.service.statistics.matchmaking.queue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface MatchmakingQueueMemberRepository extends JpaRepository<MatchmakingQueueMember, Long> {

    Optional<MatchmakingQueueMember> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<MatchmakingQueueMember> findAllByPartyId(long partyId);

    List<MatchmakingQueueMember> findAllByPartyIdIn(Collection<Long> partyIds);

    @Transactional
    long deleteByPartyId(long partyId);

    @Transactional
    void deleteAllByPartyIdIn(Collection<Long> partyIds);
}
