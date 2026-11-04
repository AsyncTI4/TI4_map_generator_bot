package ti4.spring.service.persistence;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.game.persistence.ManagedGameState;

public interface GameParticipantEntityRepository extends JpaRepository<GameParticipantEntity, Long> {

    @Modifying
    @Query("DELETE FROM GameParticipantEntity p WHERE p.game.gameName = :gameName")
    void deleteByGameName(@Param("gameName") String gameName);

    @Query("SELECT new ti4.spring.service.persistence.GameParticipantRow(p.game.gameName, p.userId, p.userName,"
            + " p.realPlayer, p.realOrEliminated) FROM GameParticipantEntity p")
    List<GameParticipantRow> findAllRows();

    default Map<String, List<ManagedGameState.Participant>> findAllParticipantsByGameName() {
        return findAllRows().stream()
                .collect(Collectors.groupingBy(
                        GameParticipantRow::gameName,
                        Collectors.mapping(GameParticipantRow::toParticipant, Collectors.toList())));
    }
}
