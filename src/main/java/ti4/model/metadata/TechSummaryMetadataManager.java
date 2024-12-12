package ti4.model.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import ti4.json.PersistenceManager;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

public class TechSummaryMetadataManager {

    private static final String TECH_SUMMARIES_PATH = "TechSummaries.json";

    public static synchronized void updateTechSummaryMetadata(Game game, Player player, String techId, boolean isResearchAgreement) {
        if (game.isFowMode() || game.isHomebrewSCMode()) {
            return;
        }

        var techSummaries = readFile();
        if (techSummaries == null) {
            return;
        }

        var roundTechSummaries = techSummaries.gameNameToTechSummary
            .computeIfAbsent(game.getName(), k -> new RoundTechSummaries(game.getRound(), new ArrayList<>()));

        FactionTechSummary factionTechSummary = roundTechSummaries.techSummaries.stream()
            .filter(summary -> summary.faction.equals(player.getFaction()))
            .findFirst()
            .orElseGet(() -> {
                var newSummary = new FactionTechSummary(player.getFaction());
                roundTechSummaries.techSummaries.add(newSummary);
                return newSummary;
            });

        if (isResearchAgreement) {
            factionTechSummary.addResearchAgreementTech(techId);
        } else {
            factionTechSummary.addTech(techId);
        }

        persistFile(techSummaries);
    }

    public static TechSummaries readFile() {
        try {
            var techSummary = PersistenceManager.readObjectFromJsonFile(TECH_SUMMARIES_PATH, TechSummaries.class);
            return techSummary != null ? techSummary : new TechSummaries(new HashMap<>());
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for TechSummaries.", e);
            return null;
        }
    }

    public static void persistFile(TechSummaries toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(TECH_SUMMARIES_PATH, toPersist);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for TechSummaries.", e);
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
