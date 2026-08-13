package ti4.spring.service.statistics.overrule;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ti4.logging.BotLogger;

/**
 * @deprecated one-off startup migration that drains the count-based {@code overrule_choice} table
 *     into per-play {@code overrule_play} rows (with null {@code player_id} because the legacy
 *     schema never captured who played). Delete this class, {@link OverruleChoiceEntity} and
 *     {@link OverruleChoiceRepository} once the migration has run in every environment and the
 *     {@code overrule_choice} table has been dropped manually.
 */
@Deprecated
@Configuration
public class OverruleChoiceMigrationRunner {

    @Bean
    ApplicationRunner migrateOverruleChoiceToOverrulePlay(
            OverruleChoiceRepository legacyRepository, OverrulePlayRepository playRepository) {
        return args -> {
            try {
                int migrated = fanOutLegacyRowsIntoPlays(legacyRepository, playRepository);
                if (migrated > 0) {
                    BotLogger.info("Migrated " + migrated + " Overrule plays from overrule_choice to overrule_play.");
                }
            } catch (Exception e) {
                BotLogger.error("Failed to migrate overrule_choice into overrule_play at startup.", e);
            }
        };
    }

    // Package-private so it's unit-testable without spinning up a Spring context. Idempotent because
    // deleting the legacy rows after fan-out leaves nothing for the next run to migrate.
    static int fanOutLegacyRowsIntoPlays(
            OverruleChoiceRepository legacyRepository, OverrulePlayRepository playRepository) {
        List<OverruleChoiceEntity> legacyRows = legacyRepository.findAll();
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
        legacyRepository.deleteAll(legacyRows);
        return plays.size();
    }
}
