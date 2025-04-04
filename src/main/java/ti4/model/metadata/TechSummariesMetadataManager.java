package ti4.model.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

@UtilityClass
public class TechSummariesMetadataManager {

    private static final String TECH_SUMMARIES_FILE = "TechSummaries.json";

    public static synchronized void addTech(Game game, Player player, String techId, boolean isResearchAgreement) {
        if (game.isFowMode() || game.isHomebrewSCMode()) {
            return;
        }

        TechSummaries techSummaries = readFile();
        if (techSummaries == null) {
            return;
        }

        RoundTechSummaries roundTechSummaries = techSummaries.gameNameToTechSummary
            .computeIfAbsent(game.getName(), k -> new RoundTechSummaries(game.getRound(), new ArrayList<>()));
        if (roundTechSummaries.round != game.getRound()) {
            roundTechSummaries = new RoundTechSummaries(game.getRound(), new ArrayList<>());
            techSummaries.gameNameToTechSummary.put(game.getName(), roundTechSummaries);
        }

        FactionTechSummary factionTechSummary = roundTechSummaries.techSummaries.stream()
            .filter(summary -> summary.faction.equals(player.getFaction()))
            .findFirst()
            .orElseGet(() -> {
                var newFactionTechSummary = new FactionTechSummary(player.getFaction());
                techSummaries.gameNameToTechSummary.get(game.getName()).techSummaries.add(newFactionTechSummary);
                return newFactionTechSummary;
            });

        if (isResearchAgreement) {
            factionTechSummary.addResearchAgreementTech(techId);
        } else {
            factionTechSummary.addTech(techId);
        }

        persistFile(techSummaries);
    }

    public static synchronized void consumeAndPersist(Consumer<TechSummaries> consumer) {
        TechSummaries techSummaries = readFile();
        consumer.accept(techSummaries);
        persistFile(techSummaries);
    }

    private static TechSummaries readFile() {
        try {
            var techSummary = PersistenceManager.readObjectFromJsonFile(TECH_SUMMARIES_FILE, TechSummaries.class);
            return techSummary != null ? techSummary : new TechSummaries(new HashMap<>());
        } catch (IOException e) {
            BotLogger.error("Failed to read json data for TechSummaries.", e);
            return null;
        }
    }

    private static void persistFile(TechSummaries toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(TECH_SUMMARIES_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for TechSummaries.", e);
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

        public FactionTechSummary(String faction) {
            this.faction = faction;
        }

        public void addTech(String techId) {
            if (tech == null) {
                tech = new ArrayList<>();
            }
            tech.add(techId);
        }

        public void addResearchAgreementTech(String techId) {
            if (researchAgreementTech == null) {
                researchAgreementTech = new ArrayList<>();
            }
            researchAgreementTech.add(techId);
        }
    }
}
