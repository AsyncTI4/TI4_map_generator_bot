package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerEntityRepository extends JpaRepository<PlayerEntity, Long> {

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.user u")
    List<PlayerEntity> findAllWithUsers();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.user u JOIN p.game g WHERE g.completed IS TRUE")
    List<PlayerEntity> findAllWithUsersByCompletedGame();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.user u JOIN p.game g WHERE g.endedEpochMilliseconds IS NULL")
    List<PlayerEntity> findAllWithUsersByActiveGame();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.user u WHERE u.id IN (:userIds)")
    List<PlayerEntity> findAllWithUsersByUserIdIn(@Param("userIds") List<String> userIds);

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.user u JOIN FETCH p.game g WHERE u.id IN (:userIds)")
    List<PlayerEntity> findAllWithUsersAndGamesByUserIdIn(@Param("userIds") List<String> userIds);

    @Query("""
        SELECT p FROM PlayerEntity p
                JOIN FETCH p.user u
                JOIN FETCH p.game g
                WHERE u.id = (:userId)
                        AND g.completed IS TRUE
                        AND g.playerCount = 6
        """)
    List<PlayerEntity> findAllWithGamesByUserIdEquals(@Param("userId") String userId);

    @Query("""
        SELECT p FROM PlayerEntity p
                JOIN FETCH p.game g
                WHERE p.user.id = (:userId)
                        AND p.replaced IS FALSE
                        AND g.completed IS TRUE
                        AND g.endedEpochMilliseconds IS NOT NULL
        """)
    List<PlayerEntity> findAllWithCompletedGamesByUserIdEquals(@Param("userId") String userId);

    @Query("""
            SELECT p FROM PlayerEntity p
            JOIN FETCH p.user u
            JOIN FETCH p.game g
            WHERE g.completed IS TRUE
              AND g.allianceMode IS FALSE
              AND g.playerCount >= 3
              AND g.playerCount <= 8
              AND (:onlyTiglGames IS FALSE OR g.twilightImperiumGlobalLeague IS TRUE)
            """)
    List<PlayerEntity> findAllWithUsersAndGamesByCompletedSixPlayerNonAllianceGame(
            @Param("onlyTiglGames") boolean onlyTiglGames);
}
