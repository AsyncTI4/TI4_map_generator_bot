package ti4.spring.service.roundstats;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface GameRoundPlayerStatsRepository extends JpaRepository<GameRoundPlayerStats, GameRoundPlayerStatsId> {

    List<GameRoundPlayerStats> findByGameId(String gameId);

    @Transactional
    void deleteByGameId(String gameId);

    @Transactional
    void deleteByGameIdAndRoundGreaterThan(String gameId, int round);
}
