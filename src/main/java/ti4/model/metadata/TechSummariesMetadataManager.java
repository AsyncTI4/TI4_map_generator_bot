package ti4.model.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.spring.service.techsummary.TechSummaryService;

@UtilityClass
public class TechSummariesMetadataManager {

    public static synchronized void addTech(Game game, Player player, String techId, boolean isResearchAgreement) {
        if (game.isFowMode() || game.isHomebrewSCMode()) {
            return;
        }

        try {
            TechSummaryService service = TechSummaryService.getBean();
            RoundTechSummaries roundTechSummaries = service.find(game.getName())
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

            service.save(game.getName(), roundTechSummaries);
        } catch (Exception e) {
            BotLogger.error("Failed to record a tech summary for game " + game.getName() + ".", e);
        }
    }

    public static synchronized void consumeAndPersist(Consumer<TechSummaries> consumer) {
        TechSummaryService service;
        Map<String, RoundTechSummaries> summariesByGameName;
        try {
            service = TechSummaryService.getBean();
            summariesByGameName = service.findAll();
        } catch (Exception e) {
            BotLogger.error("Failed to read tech summaries.", e);
            consumer.accept(null);
            return;
        }

        Set<String> previousGameNames = Set.copyOf(summariesByGameName.keySet());
        consumer.accept(new TechSummaries(summariesByGameName));
        try {
            service.replaceAll(previousGameNames, summariesByGameName);
        } catch (Exception e) {
            BotLogger.error("Failed to write tech summaries.", e);
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
