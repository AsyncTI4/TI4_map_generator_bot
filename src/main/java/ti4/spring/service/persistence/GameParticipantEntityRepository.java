package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameParticipantEntityRepository extends JpaRepository<GameParticipantEntity, Long> {

    @Modifying
    @Query("DELETE FROM GameParticipantEntity p WHERE p.game.gameName = :gameName")
    void deleteByGameName(@Param("gameName") String gameName);

    @Query("SELECT new ti4.spring.service.persistence.GameParticipantRow(p.game.gameName, p.userId, p.userName,"
            + " p.realPlayer) FROM GameParticipantEntity p")
    List<GameParticipantRow> findAllRows();
}
