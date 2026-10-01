package ti4.spring.service.statistics.overrule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@Service
public class OverruleStatsService {

    private final OverruleChoiceRepository repository;

    public OverruleStatsService(OverruleChoiceRepository repository) {
        this.repository = repository;
    }

    public static OverruleStatsService get() {
        return SpringContext.getBean(OverruleStatsService.class);
    }

    @Transactional
    public synchronized void recordChoice(String gameName, String strategyCardName) {
        addChoices(gameName, strategyCardName, 1);
    }

    /**
     * @deprecated one-off used by the action card target migration. Remove once that migration has
     *     run against all games.
     */
    @Deprecated
    @Transactional
    public synchronized void addMigratedCounts(String gameName, Map<String, Integer> countPerStrategyCard) {
        countPerStrategyCard.forEach((strategyCardName, count) -> addChoices(gameName, strategyCardName, count));
    }

    public Map<String, Integer> getCountPerStrategyCard(Set<String> gameNames) {
        Map<String, Integer> countPerStrategyCard = new HashMap<>();
        for (OverruleChoiceEntity choice : repository.findAll()) {
            if (gameNames.contains(choice.getGameName())) {
                countPerStrategyCard.merge(choice.getStrategyCard(), (int) choice.getCount(), Integer::sum);
            }
        }
        return countPerStrategyCard;
    }

    // Incrementing before inserting keeps this an upsert without a delete, which would collide with
    // the unique constraint on (game_name, strategy_card).
    private void addChoices(String gameName, String strategyCardName, long delta) {
        try {
            if (repository.incrementExistingBy(gameName, strategyCardName, delta) == 0) {
                repository.insertCount(gameName, strategyCardName, delta);
            }
        } catch (Exception e) {
            BotLogger.error(
                    "Failed to record Overrule choice '" + strategyCardName + "' for game " + gameName + ".", e);
        }
    }
}
