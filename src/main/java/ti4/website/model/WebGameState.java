package ti4.website.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AgendaSummaryHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.combat.StartCombatService.CurrentCombat;

/**
 * Slim game-state payload for the web UI.
 *
 * <p>Phase inference: the bot's {@code Game.phaseOfGame} stores free-form strings such as
 * {@code action}, {@code statusHomework}, and {@code agendawaiting}. Agenda resolution is more
 * granular than the raw string: while the raw phase remains {@code agendawaiting},
 * {@code whensResolved} and {@code aftersResolved} indicate whether we are waiting on "when"s
 * or "after"s. This class is the single place where those code-level details are normalized for
 * web consumers.</p>
 */
@Data
public class WebGameState {
    private CanonicalPhase phase;
    private String activePlayer;
    private Long turnStartedAt;
    private String winner;
    private WebAgenda agenda;
    private String activeSystem;
    private WebCombat activeCombat;

    public static WebGameState fromGame(Game game) {
        WebGameState state = new WebGameState();
        state.phase = inferPhase(game);
        state.activePlayer = colorForPlayer(game.getActivePlayer());
        state.turnStartedAt = game.getLastActivePlayerChange().getTime() < 1_000_000
                ? null
                : game.getLastActivePlayerChange().getTime();
        state.winner = game.getWinner().map(WebGameState::colorForPlayer).orElse(null);
        state.agenda = WebAgenda.fromGame(game);
        state.activeSystem = blankToNull(game.getCurrentActiveSystem());
        state.activeCombat = WebCombat.fromGame(game);
        return state;
    }

    /**
     * Infers the canonical web phase for the given game.
     *
     * <p>This method intentionally keeps action and strategy phases coarse.
     * Tactical actions, combats, component actions, and other turn details are
     * not modeled as separate phases here.</p>
     */
    private static CanonicalPhase inferPhase(Game game) {
        if (game == null) {
            return CanonicalPhase.UNKNOWN;
        }

        if (game.isHasEnded() || game.hasWinner()) {
            return CanonicalPhase.FINISHED;
        }

        String rawPhase = game.getPhaseOfGame();
        if (rawPhase == null || rawPhase.isBlank()) {
            return CanonicalPhase.UNKNOWN;
        }

        return switch (rawPhase.toLowerCase()) {
            case "miltydraft" -> CanonicalPhase.SETUP_DRAFT;
            case "playersetup" -> CanonicalPhase.SETUP_PLAYERS;
            case "strategy" -> CanonicalPhase.STRATEGY;
            case "action" -> CanonicalPhase.ACTION;
            case "statusscoring", "status" -> CanonicalPhase.STATUS_SCORING;
            case "statushomework" -> CanonicalPhase.STATUS_HOMEWORK;
            case "agenda" -> CanonicalPhase.AGENDA_READY_TO_FLIP;
            case "agendawaiting" -> inferAgendaWaitingPhase(game);
            case "agendavoting", "voting" -> CanonicalPhase.AGENDA_VOTING;
            case "agendaend" -> CanonicalPhase.AGENDA_RESOLVING;
            default -> CanonicalPhase.UNKNOWN;
        };
    }

    private static CanonicalPhase inferAgendaWaitingPhase(Game game) {
        if (game.getStoredValue("whensResolved").isEmpty()) {
            return CanonicalPhase.AGENDA_WHENS;
        }
        return CanonicalPhase.AGENDA_AFTERS;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public enum CanonicalPhase {
        @JsonProperty("unknown")
        UNKNOWN,

        @JsonProperty("setup.draft")
        SETUP_DRAFT,

        @JsonProperty("setup.players")
        SETUP_PLAYERS,

        @JsonProperty("strategy")
        STRATEGY,

        @JsonProperty("action")
        ACTION,

        @JsonProperty("status.scoring")
        STATUS_SCORING,

        @JsonProperty("status.homework")
        STATUS_HOMEWORK,

        @JsonProperty("agenda.readyToFlip")
        AGENDA_READY_TO_FLIP,

        @JsonProperty("agenda.whens")
        AGENDA_WHENS,

        @JsonProperty("agenda.afters")
        AGENDA_AFTERS,

        @JsonProperty("agenda.voting")
        AGENDA_VOTING,

        @JsonProperty("agenda.resolving")
        AGENDA_RESOLVING,

        @JsonProperty("finished")
        FINISHED
    }

    @Data
    public static class WebAgenda {
        private String id;
        private Map<String, Integer> startVoteCounts;
        private Map<String, Integer> outcomeVoteCounts;

        static WebAgenda fromGame(Game game) {
            String agendaId = AgendaHelper.getCurrentAgendaId(game);
            if (agendaId == null || agendaId.isBlank()) {
                return null;
            }

            WebAgenda agenda = new WebAgenda();
            agenda.id = agendaId;
            agenda.startVoteCounts = AgendaHelper.getAgendaStartVoteCounts(game);
            agenda.outcomeVoteCounts = AgendaSummaryHelper.getCurrentOutcomeVoteCounts(game);
            return agenda;
        }
    }

    @Data
    public static class WebCombat {
        private String system;
        private String unitHolder;
        private Integer round;
        private String[] participantColors;

        static WebCombat fromGame(Game game) {
            CurrentCombat current = StartCombatService.getCurrentCombat(game);
            if (current == null) return null;
            WebCombat combat = new WebCombat();
            combat.system = current.tilePosition();
            combat.unitHolder = current.unitHolderName();
            combat.round = current.round();
            combat.participantColors = current.factions().stream()
                    .map(faction -> colorForFaction(game, faction))
                    .toArray(String[]::new);
            return combat;
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
