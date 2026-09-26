package ti4.spring.service.statistics.matchmaking.queue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface MatchmakingQueueSearchRepository extends JpaRepository<MatchmakingQueueSearch, Long> {

    Optional<MatchmakingQueueSearch> findByThreadId(String threadId);

    List<MatchmakingQueueSearch> findAllByOrderByCreatedAtAsc();

    @Transactional
    void deleteByThreadId(String threadId);
}
