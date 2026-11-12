package ti4.ai.agenda;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;

// Test-side window on the package-private agenda value code, for self-play trackers outside ti4.ai.agenda. Each value
// is the seat's own view (it reads only the seat's own secrets), so nothing hidden from that seat leaks in.
public final class AgendaUtilityProbe {

    private AgendaUtilityProbe() {}

    public static String currentAgenda(Game game) {
        return AgendaPolicy.currentAgenda(game);
    }

    // AgendaUtility of every outcome the table can vote for right now, in TG, from this seat's own view.
    public static Map<String, Double> utilities(Game game, Player seat) {
        String agenda = AgendaPolicy.currentAgenda(game);
        List<String> outcomes = AgendaOutcomes.of(game);
        Map<String, Double> values = new LinkedHashMap<>();
        if (agenda.isBlank()) return values;
        for (String outcome : outcomes) values.put(outcome, AgendaUtility.of(game, seat, true, agenda, outcome));
        return values;
    }
}
