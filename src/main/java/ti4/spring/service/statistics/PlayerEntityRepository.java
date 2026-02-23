package ti4.spring.service.statistics;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PlayerEntityRepository extends JpaRepository<PlayerEntity, Long> {

    @Query("SELECT p FROM PlayerEntity p JOIN p.game g WHERE g.endedEpochMilliseconds != null")
    List<PlayerEntity> findAllPlayersOfActiveGames();
}
