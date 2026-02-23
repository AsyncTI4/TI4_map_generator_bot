package ti4.spring.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayerEntityRepository extends JpaRepository<PlayerEntity, Long> {

    @Query("SELECT p FROM PlayerEntity p JOIN p.game g WHERE g.endedEpochMilliseconds IS NULL")
    List<PlayerEntity> findAllPlayersOfActiveGames();

    List<PlayerEntity> findAllByUser_IdIn(List<String> userIds);
}
