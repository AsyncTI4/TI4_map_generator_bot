package ti4.spring.service.statistics.overrule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@Service
public class OverruleStatsService {

    private final OverrulePlayRepository playRepository;

    @SuppressWarnings("deprecation") // one-off dependency drained by migrateChoicesToPlays
    private final OverruleChoiceRepository legacyChoiceRepository;

    @SuppressWarnings("deprecation")
    public OverruleStatsService(
            OverrulePlayRepository playRepository, OverruleChoiceRepository legacyChoiceRepository) {
        this.playRepository = playRepository;
        this.legacyChoiceRepository = legacyChoiceRepository;
    }

    public static OverruleStatsService get() {
        return SpringContext.getBean(OverruleStatsService.class);
    }

    @Transactional
    public synchronized void recordPlay(String gameName, String playerId, String strategyCard) {
        try {
            playRepository.save(new OverrulePlayEntity(gameName, playerId, strategyCard));
        } catch (Exception e) {
            BotLogger.error("Failed to record Overrule play '" + strategyCard + "' for game " + gameName + ".", e);
        }
    }

    public List<OverrulePlayEntity> findPlaysForGames(Set<String> gameNames) {
        List<OverrulePlayEntity> plays = new ArrayList<>();
        for (OverrulePlayEntity play : playRepository.findAll()) {
            if (gameNames.contains(play.getGameName())) {
                plays.add(play);
            }
        }
        return plays;
    }

    /**
     * @deprecated one-off. Drains the count-based {@code overrule_choice} table into per-play
     *     {@code overrule_play} rows (with null {@code player_id} because the legacy schema never
     *     captured who chose the target), then deletes the legacy rows so a re-run is a no-op.
     *     Remove this method along with {@link OverruleChoiceEntity} and {@link
     *     OverruleChoiceRepository} once the migration has run in every environment and the
     *     {@code overrule_choice} table has been dropped manually.
     */
    @Deprecated
    @Transactional
    @SuppressWarnings("deprecation")
    public synchronized int migrateChoicesToPlays() {
        List<OverruleChoiceEntity> legacyRows = legacyChoiceRepository.findAll();
        if (legacyRows.isEmpty()) {
            return 0;
        }
        List<OverrulePlayEntity> plays = new ArrayList<>();
        for (OverruleChoiceEntity row : legacyRows) {
            for (int i = 0; i < row.getCount(); i++) {
                plays.add(new OverrulePlayEntity(row.getGameName(), null, row.getStrategyCard()));
            }
        }
        playRepository.saveAll(plays);
        legacyChoiceRepository.deleteAll(legacyRows);
        return plays.size();
    }
}
