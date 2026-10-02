package ti4.spring.service.gameevent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface GameEventRepository extends CrudRepository<GameEventEntity, Long> {
    List<GameEventEntity> findByGameNameAndSeqLessThanEqualOrderBySeqAsc(String gameName, long seq);

    Optional<GameEventEntity> findFirstByGameNameAndSeqLessThanEqualAndMapStateIsNotNullOrderBySeqDesc(
            String gameName, long seq);

    long deleteByGameNameAndSeqGreaterThan(String gameName, long seq);
}
