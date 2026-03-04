package ti4.spring.service.roundstats;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface GameRoundPlayerStatsRepository extends JpaRepository<GameRoundPlayerStats, GameRoundPlayerStatsId> {

    List<GameRoundPlayerStats> findByGameId(String gameId);

    List<GameRoundPlayerStats> findByUserDiscordIdAndGameIdIn(String userDiscordId, Collection<String> gameIds);

    @Transactional
    void deleteByGameId(String gameId);

    @Transactional
    void deleteByGameIdAndRoundGreaterThan(String gameId, int round);
}
