package ti4.spring.service.statistics.matchmaking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface MatchmakingQueueEntryRepository extends JpaRepository<MatchmakingQueueEntryEntity, Long> {

    List<MatchmakingQueueEntryEntity> findAllByOrderByQueuedAtAsc();

    boolean existsByUserId(String userId);

    @Transactional
    long deleteByUserId(String userId);
}
