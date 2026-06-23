package ti4.spring.service.statistics.matchmaking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface MatchmakingQueueEntryRepository extends JpaRepository<MatchmakingQueueEntryEntity, Long> {

    List<MatchmakingQueueEntryEntity> findAllByOrderByQueuedAtAsc();

    boolean existsByUserId(String userId);

    Optional<MatchmakingQueueEntryEntity> findByUserId(String userId);

    List<MatchmakingQueueEntryEntity> findAllByPartyId(String partyId);

    @Transactional
    long deleteByUserId(String userId);

    @Transactional
    long deleteByPartyId(String partyId);
}
