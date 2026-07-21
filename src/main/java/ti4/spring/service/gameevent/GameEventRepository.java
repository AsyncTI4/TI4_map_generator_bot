package ti4.spring.service.gameevent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface GameEventRepository extends CrudRepository<GameEventEntity, Long> {
    List<GameEventEntity> findByGameNameAndSeqLessThanEqualOrderBySeqAsc(String gameName, long seq);

    Optional<GameEventEntity> findFirstByGameNameAndSeqLessThanEqualAndMapStateIsNotNullOrderBySeqDesc(
            String gameName, long seq);

    @Modifying
    @Query("delete from GameEventEntity event where event.gameName = :gameName and event.seq > :seq")
    int deleteFutureEvents(@Param("gameName") String gameName, @Param("seq") long seq);
}
