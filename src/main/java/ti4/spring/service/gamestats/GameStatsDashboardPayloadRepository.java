package ti4.spring.service.gamestats;

import java.util.Collection;
import org.springframework.data.repository.CrudRepository;

interface GameStatsDashboardPayloadRepository extends CrudRepository<GameStatsDashboardPayloadData, String> {
    void deleteByGameNameNotIn(Collection<String> gameNames);
}
