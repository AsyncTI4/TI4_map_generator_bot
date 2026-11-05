package ti4.spring.service.persistence;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.game.persistence.ManagedGameState;

public interface PlayerEntityRepository extends JpaRepository<PlayerEntity, Long> {

    @Modifying
    @Query("DELETE FROM PlayerEntity p WHERE p.game.gameName = :gameName")
    void deleteByGameName(@Param("gameName") String gameName);

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.statsUser u JOIN FETCH p.game g")
    List<PlayerEntity> findAllWithUsersAndGames();

    @Query("SELECT new ti4.spring.service.persistence.ParticipantRow(p.game.gameName, p.playingUserId,"
            + " p.playingUserName, p.playing, p.eliminated, p.npc) FROM PlayerEntity p")
    List<ParticipantRow> findAllParticipantRows();

    default Map<String, List<ManagedGameState.Participant>> findAllParticipantsByGameName() {
        return findAllParticipantRows().stream()
                .collect(Collectors.groupingBy(
                        ParticipantRow::gameName,
                        Collectors.mapping(ParticipantRow::toParticipant, Collectors.toList())));
    }

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.statsUser u JOIN p.game g"
            + " WHERE g.statisticsIgnored IS FALSE AND p.playing IS TRUE")
    List<PlayerEntity> findAllWithUsers();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.statsUser u JOIN p.game g"
            + " WHERE g.completed IS TRUE AND g.statisticsIgnored IS FALSE AND p.playing IS TRUE")
    List<PlayerEntity> findAllWithUsersByCompletedGame();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.statsUser u JOIN p.game g"
            + " WHERE g.endedEpochMilliseconds IS NULL AND g.statisticsIgnored IS FALSE AND p.playing IS TRUE")
    List<PlayerEntity> findAllWithUsersByActiveGame();

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.statsUser u JOIN p.game g"
            + " WHERE u.id IN (:userIds) AND g.statisticsIgnored IS FALSE AND p.playing IS TRUE")
    List<PlayerEntity> findAllWithUsersByUserIdIn(@Param("userIds") List<String> userIds);

    @Query("SELECT p FROM PlayerEntity p JOIN FETCH p.statsUser u JOIN FETCH p.game g"
            + " WHERE u.id IN (:userIds) AND g.statisticsIgnored IS FALSE AND p.playing IS TRUE")
    List<PlayerEntity> findAllWithUsersAndGamesByUserIdIn(@Param("userIds") List<String> userIds);

    @Query("""
        SELECT p FROM PlayerEntity p
                JOIN FETCH p.statsUser u
                JOIN FETCH p.game g
                WHERE u.id = (:userId)
                        AND g.statisticsIgnored IS FALSE
                        AND p.playing IS TRUE
                        AND g.completed IS TRUE
                        AND g.playerCount = 6
        """)
    List<PlayerEntity> findAllWithGamesByUserIdEquals(@Param("userId") String userId);

    @Query("""
        SELECT p FROM PlayerEntity p
                JOIN FETCH p.statsUser u
                JOIN FETCH p.game g
                WHERE g.completed IS TRUE
                        AND g.statisticsIgnored IS FALSE
                        AND p.playing IS TRUE
                        AND g.playerCount = 6
        """)
    List<PlayerEntity> findAllWithUsersAndSixPlayerCompletedGames();

    @Query("""
        SELECT p FROM PlayerEntity p
                JOIN FETCH p.game g
                WHERE p.statsUser.id = (:userId)
                        AND p.replaced IS FALSE
                        AND g.statisticsIgnored IS FALSE
                        AND p.playing IS TRUE
                        AND g.completed IS TRUE
                        AND g.endedEpochMilliseconds IS NOT NULL
        """)
    List<PlayerEntity> findAllWithCompletedGamesByUserIdEquals(@Param("userId") String userId);

    @Query("""
            SELECT p FROM PlayerEntity p
            JOIN FETCH p.statsUser u
            JOIN FETCH p.game g
            WHERE g.completed IS TRUE
              AND g.statisticsIgnored IS FALSE
              AND p.playing IS TRUE
              AND g.allianceMode IS FALSE
              AND g.playerCount >= 5
              AND g.playerCount <= 8
              AND (:onlyTiglGames IS FALSE OR g.twilightImperiumGlobalLeague IS TRUE)
            """)
    List<PlayerEntity> findAllWithUsersAndGamesByCompletedNonAllianceGame(
            @Param("onlyTiglGames") boolean onlyTiglGames);
}
