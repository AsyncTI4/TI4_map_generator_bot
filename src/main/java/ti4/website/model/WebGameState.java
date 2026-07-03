package ti4.website.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AgendaSummaryHelper;
import ti4.website.PhaseInferenceService;
import ti4.website.PhaseInferenceService.CanonicalPhase;

@Data
public class WebGameState {
    private CanonicalPhase phase;
    private WebAgenda agenda;
    private String activeSystem;
    private WebCombat activeCombat;

    public static WebGameState fromGame(Game game) {
        WebGameState state = new WebGameState();
        state.phase = PhaseInferenceService.infer(game);
        state.agenda = WebAgenda.fromGame(game, state.phase);
        state.activeSystem = blankToNull(game.getCurrentActiveSystem());
        state.activeCombat = WebCombat.fromGame(game);
        return state;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Data
    public static class WebAgenda {
        private String id;
        private String voter;
        private Map<String, Integer> startVoteCounts;
        private Map<String, Integer> outcomeVoteCounts;

        static WebAgenda fromGame(Game game, CanonicalPhase phase) {
            String agendaId = AgendaHelper.getCurrentAgendaId(game);
            if (agendaId == null || agendaId.isBlank()) {
                return null;
            }

            WebAgenda agenda = new WebAgenda();
            agenda.id = agendaId;
            if (phase == CanonicalPhase.AGENDA_VOTING) {
                agenda.voter = colorForPlayer(game.getActivePlayer());
            }
            agenda.startVoteCounts = AgendaHelper.getAgendaStartVoteCounts(game);
            agenda.outcomeVoteCounts = AgendaSummaryHelper.getCurrentOutcomeVoteCounts(game);
            return agenda;
        }
    }

    @Data
    public static class WebCombat {
        private String system;
        private String[] participantColors;

        static WebCombat fromGame(Game game) {
            String factionsInCombat = game.getStoredValue("factionsInCombat");
            if (factionsInCombat == null || factionsInCombat.isBlank()) {
                return null;
            }

            WebCombat combat = new WebCombat();
            combat.system = inferCombatSystem(game, factionsInCombat.split("_"));
            combat.participantColors = Arrays.stream(factionsInCombat.split("_"))
                    .map(faction -> colorForFaction(game, faction))
                    .toArray(String[]::new);
            return combat;
        }

        private static String inferCombatSystem(Game game, String[] factions) {
            for (String key : game.getStoredValueMap().keySet()) {
                if (!key.startsWith("combatRoundTracker")) {
                    continue;
                }

                String suffix = key.substring("combatRoundTracker".length());
                for (String faction : factions) {
                    if (faction == null || faction.isBlank() || !suffix.startsWith(faction)) {
                        continue;
                    }

                    String location = suffix.substring(faction.length());
                    return game.getTileMap().keySet().stream()
                            .filter(location::startsWith)
                            .max(Comparator.comparingInt(String::length))
                            .orElse(null);
                }
            }
            return null;
        }
    }

    private static String colorForFaction(Game game, String faction) {
        String color = colorForPlayer(game.getPlayerFromColorOrFaction(faction));
        return color == null ? faction : color;
    }

    private static String colorForPlayer(Player player) {
        if (player == null || player.getColor() == null || player.getColor().isBlank()) {
            return null;
        }
        return player.getColor();
    }
}
