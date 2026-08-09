package ti4.spring.service.statistics.overrule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.GameStats.OverruleTargetMigration.OverruleEntry;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@Service
public class OverruleStatsService {

    private final OverrulePlayRepository repository;

    public OverruleStatsService(OverrulePlayRepository repository) {
        this.repository = repository;
    }

    public static OverruleStatsService get() {
        return SpringContext.getBean(OverruleStatsService.class);
    }

    @Transactional
    public synchronized void recordPlay(String gameName, String playerId, String strategyCard) {
        try {
            repository.save(new OverrulePlayEntity(gameName, playerId, strategyCard));
        } catch (Exception e) {
            BotLogger.error("Failed to record Overrule play '" + strategyCard + "' for game " + gameName + ".", e);
        }
    }

    /**
     * @deprecated one-off used by the action card target migration. Remove once that migration has
     *     run against all games.
     */
    @Deprecated
    @Transactional
    public synchronized void addMigratedPlays(String gameName, List<OverruleEntry> plays) {
        try {
            List<OverrulePlayEntity> entities = new ArrayList<>(plays.size());
            for (OverruleEntry play : plays) {
                entities.add(new OverrulePlayEntity(gameName, play.playerId(), play.strategyCard()));
            }
            repository.saveAll(entities);
        } catch (Exception e) {
            BotLogger.error("Failed to migrate " + plays.size() + " Overrule plays for game " + gameName + ".", e);
        }
    }

    public List<OverrulePlayEntity> findPlaysForGames(Set<String> gameNames) {
        List<OverrulePlayEntity> plays = new ArrayList<>();
        for (OverrulePlayEntity play : repository.findAll()) {
            if (gameNames.contains(play.getGameName())) {
                plays.add(play);
            }
        }
        return plays;
    }
}
