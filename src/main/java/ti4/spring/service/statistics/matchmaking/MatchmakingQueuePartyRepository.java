package ti4.spring.service.statistics.matchmaking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MatchmakingQueuePartyRepository extends JpaRepository<MatchmakingQueueParty, Long> {

    List<MatchmakingQueueParty> findAllByQueuedTrueOrderByQueuedAtAsc();
}
