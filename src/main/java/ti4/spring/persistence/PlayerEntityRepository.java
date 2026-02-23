package ti4.spring.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerEntityRepository extends JpaRepository<PlayerEntity, Long> {

    @Query("SELECT p FROM PlayerEntity p JOIN p.game g WHERE g.endedEpochMilliseconds IS NULL")
    List<PlayerEntity> findAllPlayersOfActiveGames();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.game g JOIN FETCH p.user u WHERE u.id IN :userIds")
    List<PlayerEntity> findAllByUserIds(@Param("userIds") List<String> userIds);
}
