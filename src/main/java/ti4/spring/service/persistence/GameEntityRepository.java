package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameEntityRepository extends JpaRepository<GameEntity, String> {
    List<GameEntity> findByTwilightImperiumGlobalLeagueTrueAndEndedEpochMillisecondsIsNull();

    @Modifying
    @Query("DELETE FROM GameEntity g WHERE g.gameName = :gameName")
    void deleteByGameName(@Param("gameName") String gameName);

    @Modifying
    @Query("UPDATE GameEntity g SET g.revisionKey = :revisionKey, g.mappingVersion = :mappingVersion"
            + " WHERE g.gameName = :gameName")
    int updateRevision(
            @Param("gameName") String gameName,
            @Param("revisionKey") String revisionKey,
            @Param("mappingVersion") Integer mappingVersion);
}
