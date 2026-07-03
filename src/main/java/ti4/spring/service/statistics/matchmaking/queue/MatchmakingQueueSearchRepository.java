package ti4.spring.service.statistics.matchmaking.queue;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface MatchmakingQueueSearchRepository extends JpaRepository<MatchmakingQueueSearch, Long> {

    Optional<MatchmakingQueueSearch> findByThreadId(String threadId);

    @Transactional
    void deleteByThreadId(String threadId);
}
