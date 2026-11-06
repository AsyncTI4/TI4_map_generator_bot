package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameEntityRepository extends JpaRepository<GameEntity, String> {
    List<GameEntity> findByTwilightImperiumGlobalLeagueTrueAndStatisticsIgnoredFalseAndEndedEpochMillisecondsIsNull();

    @Query("SELECT new ti4.spring.service.persistence.GameRevisionRow(g.gameName, g.revisionKey)"
            + " FROM GameEntity g WHERE g.mappingVersion = :mappingVersion AND g.revisionKey IS NOT NULL")
    List<GameRevisionRow> findRevisionRows(@Param("mappingVersion") int mappingVersion);

    @Modifying
    @Query("DELETE FROM GameEntity g WHERE g.gameName = :gameName")
    void deleteByGameName(@Param("gameName") String gameName);
}
