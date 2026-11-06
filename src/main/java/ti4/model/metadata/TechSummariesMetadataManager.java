package ti4.model.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.json.LegacyPersistenceFile;
import ti4.logging.BotLogger;
import ti4.spring.service.techsummary.TechSummaryService;

@UtilityClass
public class TechSummariesMetadataManager {

    // TODO: Remove the TechSummaries.json fallback (this constant, databaseInUse, store, usesDatabase,
    // importLegacyFile and LegacyTechSummariesFile) with JsonStoreRolloverCron once every environment has imported it.
    @Deprecated(forRemoval = true, since = "2026-11")
    private static final String LEGACY_TECH_SUMMARIES_FILE = "TechSummaries.json";

    @Deprecated(forRemoval = true, since = "2026-11")
    private static boolean databaseInUse;

    public static synchronized void addTech(Game game, Player player, String techId, boolean isResearchAgreement) {
        if (game.isFowMode() || game.isHomebrewSCMode()) {
            return;
        }

        try {
            TechSummaryStore store = store();
            RoundTechSummaries roundTechSummaries = store.find(game.getName())
                    .filter(summaries -> summaries.round == game.getRound())
                    .orElseGet(() -> new RoundTechSummaries(game.getRound(), new ArrayList<>()));

            FactionTechSummary factionTechSummary = roundTechSummaries.techSummaries.stream()
                    .filter(summary -> summary.faction.equals(player.getFaction()))
                    .findFirst()
                    .orElseGet(() -> {
                        var newFactionTechSummary = new FactionTechSummary(player.getFaction());
                        roundTechSummaries.techSummaries.add(newFactionTechSummary);
                        return newFactionTechSummary;
                    });

            if (isResearchAgreement) {
                factionTechSummary.addResearchAgreementTech(techId);
            } else {
                factionTechSummary.addTech(techId);
            }

            store.save(game.getName(), roundTechSummaries);
        } catch (Exception e) {
            BotLogger.error("Failed to record a tech summary for game " + game.getName() + ".", e);
        }
    }

    public static synchronized void consumeAndPersist(Consumer<TechSummaries> consumer) {
        TechSummaryStore store;
        Map<String, RoundTechSummaries> summariesByGameName;
        try {
            store = store();
            summariesByGameName = store.findAll();
        } catch (Exception e) {
            BotLogger.error("Failed to read tech summaries.", e);
            consumer.accept(null);
            return;
        }

        Set<String> previousGameNames = Set.copyOf(summariesByGameName.keySet());
        consumer.accept(new TechSummaries(summariesByGameName));
        try {
            store.replaceAll(previousGameNames, summariesByGameName);
        } catch (Exception e) {
            BotLogger.error("Failed to write tech summaries.", e);
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    public static synchronized int importLegacyFile() {
        TechSummaryService service = TechSummaryService.getBean();
        if (usesDatabase(service)) return 0;
        TechSummaries legacy = LegacyPersistenceFile.read(LEGACY_TECH_SUMMARIES_FILE, TechSummaries.class);
        int imported = legacy == null || legacy.gameNameToTechSummary() == null
                ? 0
                : service.importMissing(legacy.gameNameToTechSummary());
        databaseInUse = true;
        LegacyPersistenceFile.markImported(LEGACY_TECH_SUMMARIES_FILE);
        return imported;
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static TechSummaryStore store() {
        TechSummaryService service = TechSummaryService.getBean();
        return usesDatabase(service) ? new DatabaseTechSummaryStore(service) : new LegacyTechSummariesFile();
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static boolean usesDatabase(TechSummaryService service) {
        if (!databaseInUse && (service.hasAny() || !LegacyPersistenceFile.exists(LEGACY_TECH_SUMMARIES_FILE))) {
            databaseInUse = true;
        }
        return databaseInUse;
    }

    private interface TechSummaryStore {
        Optional<RoundTechSummaries> find(String gameName);

        void save(String gameName, RoundTechSummaries summaries);

        Map<String, RoundTechSummaries> findAll();

        void replaceAll(Set<String> previousGameNames, Map<String, RoundTechSummaries> summariesByGameName);
    }

    private record DatabaseTechSummaryStore(TechSummaryService service) implements TechSummaryStore {

        public Optional<RoundTechSummaries> find(String gameName) {
            return service.find(gameName);
        }

        public void save(String gameName, RoundTechSummaries summaries) {
            service.save(gameName, summaries);
        }

        public Map<String, RoundTechSummaries> findAll() {
            return service.findAll();
        }

        public void replaceAll(Set<String> previousGameNames, Map<String, RoundTechSummaries> summariesByGameName) {
            service.replaceAll(previousGameNames, summariesByGameName);
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static final class LegacyTechSummariesFile implements TechSummaryStore {

        public Optional<RoundTechSummaries> find(String gameName) {
            return Optional.ofNullable(findAll().get(gameName));
        }

        public void save(String gameName, RoundTechSummaries summaries) {
            Map<String, RoundTechSummaries> all = findAll();
            all.put(gameName, summaries);
            write(all);
        }

        public Map<String, RoundTechSummaries> findAll() {
            TechSummaries legacy = LegacyPersistenceFile.read(LEGACY_TECH_SUMMARIES_FILE, TechSummaries.class);
            return legacy == null || legacy.gameNameToTechSummary() == null
                    ? new HashMap<>()
                    : new HashMap<>(legacy.gameNameToTechSummary());
        }

        public void replaceAll(Set<String> previousGameNames, Map<String, RoundTechSummaries> summariesByGameName) {
            write(summariesByGameName);
        }

        private static void write(Map<String, RoundTechSummaries> summariesByGameName) {
            LegacyPersistenceFile.write(LEGACY_TECH_SUMMARIES_FILE, new TechSummaries(summariesByGameName));
        }
    }

    public record TechSummaries(Map<String, RoundTechSummaries> gameNameToTechSummary) {}

    public record RoundTechSummaries(int round, List<FactionTechSummary> techSummaries) {}

    @Getter
    @NoArgsConstructor
    public static class FactionTechSummary {

        private String faction;
        private List<String> tech;
        private List<String> researchAgreementTech;

        FactionTechSummary(String faction) {
            this.faction = faction;
        }

        void addTech(String techId) {
            if (tech == null) {
                tech = new ArrayList<>();
            }
            tech.add(techId);
        }

        void addResearchAgreementTech(String techId) {
            if (researchAgreementTech == null) {
                researchAgreementTech = new ArrayList<>();
            }
            researchAgreementTech.add(techId);
        }
    }
}
