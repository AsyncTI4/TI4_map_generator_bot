package ti4.ai.agenda;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;

// Test-side window on the package-private agenda value code, for self-play trackers outside ti4.ai.agenda. Each value
// is the seat's own view (it reads only the seat's own secrets), so nothing hidden from that seat leaks in.
//
// Every seat is scored on one fixed yardstick, the expert value table, whatever its AiExperiments flags: in an A/B on
// AGENDA_VALUE_TABLE the control seats vote with the old formula but are graded with the same table as the treatment
// seats, so "treatment minus control" compares play, not two value scales.
public final class AgendaUtilityProbe {

    private static final boolean REFERENCE_TABLE = true;

    private AgendaUtilityProbe() {}

    public static String currentAgenda(Game game) {
        return AgendaPolicy.currentAgenda(game);
    }

    // AgendaUtility of every outcome the table can vote for right now, in TG, from this seat's own view.
    public static Map<String, Double> utilities(Game game, Player seat) {
        return utilities(game, seat, AgendaPolicy.currentAgenda(game));
    }

    // The same for a named agenda over the current outcomes: the tracker passes the agenda hidden under Covert
    // Legislation (the outcomes already follow the announced target). Measurement only; never fed to the AI.
    public static Map<String, Double> utilities(Game game, Player seat, String agenda) {
        List<String> outcomes = AgendaOutcomes.of(game);
        Map<String, Double> values = new LinkedHashMap<>();
        if (agenda == null || agenda.isBlank()) return values;
        for (String outcome : outcomes)
            values.put(outcome, AgendaUtility.of(game, seat, true, REFERENCE_TABLE, agenda, outcome));
        return values;
    }
}
