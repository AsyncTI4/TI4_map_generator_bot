package ti4.helpers;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.AgendaModel;
import ti4.model.Mapper;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;

@UtilityClass
public class AgendaEvents {

    static final String EMERGENCY_SESSION = "emergency session";
    static final String NO_LAWS = "no laws in play";
    static final String NO_SCORED_SECRETS = "no scored secrets";
    static final String HIDDEN_NO_LAWS = "hidden agenda had no laws in play";
    static final String HIDDEN_NO_SCORED_SECRETS = "hidden agenda had no scored secrets";
    static final String QUASH = "quash";
    static final String VETO = "veto";
    static final String VOTE = "vote";
    static final String PRESET_ABSTENTION = "preset abstention";
    static final String NO_VOTES = "no votes";
    static final String FORCED_ABSTENTION = "forced abstention";
    static final String GENETIC_RECOMBINATION = "genetic recombination";

    static void revealed(Game game, String agendaId, int agendaNumber, boolean fromBottom, boolean duringAction) {
        GameEventService.commit(
                game,
                GameEventType.AGENDA_REVEALED,
                null,
                revealedPayload(agendaId, agendaNumber, fromBottom, duringAction));
    }

    static void discardedOnReveal(
            Game game, String agendaId, int agendaNumber, boolean fromBottom, boolean duringAction, String reason) {
        revealed(game, agendaId, agendaNumber, fromBottom, duringAction);
        discarded(game, null, agendaId, reason);
    }

    public static void discarded(Game game, Player player, String agendaId, String reason) {
        GameEventService.commit(game, GameEventType.AGENDA_RESOLVED, player, discardedPayload(agendaId, reason));
    }

    static void voteCast(Game game, Player player, String outcome, String votes, String source) {
        GameEventService.commit(
                game,
                GameEventType.AGENDA_VOTE_CAST,
                player,
                voteCastPayload(outcome, NumberUtils.toInt(votes), source, game.isHiddenAgendaMode()));
    }

    static Map<String, Object> revealedPayload(
            String agendaId, int agendaNumber, boolean fromBottom, boolean duringAction) {
        Map<String, Object> payload = agendaPayload(agendaId);
        payload.put("agendaNumber", agendaNumber);
        payload.put("fromBottom", fromBottom);
        payload.put("duringAction", duringAction);
        return payload;
    }

    static Map<String, Object> discardedPayload(String agendaId, String reason) {
        Map<String, Object> payload = agendaPayload(agendaId);
        payload.put("discarded", reason);
        return payload;
    }

    static Map<String, Object> voteCastPayload(String outcome, int votes, String source, boolean hidden) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("source", source);
        if (hidden) {
            payload.put("hidden", true);
            return payload;
        }
        payload.put("votes", votes);
        if (votes > 0 && outcome != null && !outcome.isEmpty()) {
            payload.put("outcome", outcome);
        }
        return payload;
    }

    private static Map<String, Object> agendaPayload(String agendaId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agendaId", agendaId);
        AgendaModel agenda = agendaId == null ? null : Mapper.getAgenda(agendaId);
        if (agenda != null) {
            payload.put("agendaName", agenda.getName());
        }
        return payload;
    }
}
