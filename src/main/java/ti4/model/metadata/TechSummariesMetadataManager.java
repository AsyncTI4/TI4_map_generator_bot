package ti4.model.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.json.JsonFileStore;

@UtilityClass
public class TechSummariesMetadataManager {

    private static final JsonFileStore<TechSummaries> store =
            new JsonFileStore<>("TechSummaries.json", TechSummaries.class, () -> new TechSummaries(new HashMap<>()));

    public static synchronized void addTech(Game game, Player player, String techId, boolean isResearchAgreement) {
        if (game.isFowMode() || game.isHomebrewSCMode()) {
            return;
        }

        TechSummaries techSummaries = store.read();
        if (techSummaries == null) {
            return;
        }

        RoundTechSummaries existing = techSummaries.gameNameToTechSummary.get(game.getName());
        RoundTechSummaries roundTechSummaries = existing != null && existing.round == game.getRound()
                ? existing
                : new RoundTechSummaries(game.getRound(), new ArrayList<>());
        techSummaries.gameNameToTechSummary.put(game.getName(), roundTechSummaries);

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

        store.write(techSummaries);
    }

    public static synchronized void consumeAndPersist(Consumer<TechSummaries> consumer) {
        TechSummaries techSummaries = store.read();
        consumer.accept(techSummaries);
        store.write(techSummaries);
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
