package ti4.spring.service.gameevent;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface GameEventRepository extends CrudRepository<GameEventEntity, Long> {
    List<GameEventEntity> findByGameNameAndSeqLessThanEqualOrderBySeqAsc(String gameName, long seq);

    @Query("select e.mapState from GameEventEntity e where e.gameName = :gameName and e.seq <= :seq"
            + " and e.mapState is not null order by e.seq desc")
    List<String> findLatestMapStates(@Param("gameName") String gameName, @Param("seq") long seq, Pageable pageable);

    long deleteByGameNameAndSeqGreaterThan(String gameName, long seq);
}
