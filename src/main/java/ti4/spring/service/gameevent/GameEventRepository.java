package ti4.spring.service.gameevent;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

interface GameEventRepository extends CrudRepository<GameEventEntity, Long> {
    List<GameEventEntity> findByGameNameAndSeqLessThanEqualOrderBySeqAsc(String gameName, long seq);

    long deleteByGameNameAndSeqGreaterThan(String gameName, long seq);
}
