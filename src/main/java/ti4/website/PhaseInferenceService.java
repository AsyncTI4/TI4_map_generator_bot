package ti4.website;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

/**
 * Converts the bot's historical {@code Game.phaseOfGame} string plus a small
 * amount of agenda state into a stable, web-facing phase enum.
 *
 * <p>The saved game state currently stores phase as free-form strings such as
 * {@code action}, {@code statusHomework}, and {@code agendawaiting}. Agenda
 * resolution is more granular than that raw string: while raw phase remains
 * {@code agendawaiting}, {@code whensResolved} and {@code aftersResolved}
 * indicate whether the agenda is waiting on "when"s or "after"s. This service
 * is the single place where those code-level details are normalized for web
 * consumers.</p>
 */
@UtilityClass
public class PhaseInferenceService {

    /**
     * Infers the canonical web phase for the given game.
     *
     * <p>This method intentionally keeps action and strategy phases coarse.
     * Tactical actions, combats, component actions, and other turn details are
     * not modeled as separate phases here.</p>
     */
    public static CanonicalPhase infer(Game game) {
        if (game == null) {
            return CanonicalPhase.UNKNOWN;
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
        AGENDA_RESOLVING
    }
}
