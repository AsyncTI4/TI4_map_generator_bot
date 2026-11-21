package ti4.ai.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.trace.ThinkingAborted;
import ti4.ai.trace.ThinkingProfile;
import ti4.ai.trace.ThinkingProfile.SpanTotal;
import ti4.ai.trace.ThinkingStats;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class AbortReport {

    private static final List<String> EXPLORE_TRAITS = List.of("cultural", "hazardous", "industrial", "frontier");

    static String line(Game game, Player seat, ThinkingProfile profile, ThinkingAborted aborted) {
        return "AI thinking aborted game=" + game.getName() + " round=" + game.getRound() + " phase="
                + game.getPhaseOfGame() + " seat=" + seat.getFaction() + " after="
                + ThinkingStats.millis(profile.totalNanos()) + " reason=\"" + aborted.reason() + "\" path=["
                + aborted.compactTrail() + "] calls=[" + callsAlong(profile, aborted) + "] keys=["
                + String.join("; ", aborted.keys()) + "] inputs=[" + inputs(game, seat) + "]";
    }

    private static String callsAlong(ThinkingProfile profile, ThinkingAborted aborted) {
        Map<String, SpanTotal> byName =
                profile.spans().stream().collect(Collectors.toMap(SpanTotal::name, Function.identity()));
        Set<String> names = new LinkedHashSet<>(aborted.spans());
        return names.stream()
                .map(name -> {
                    SpanTotal span = byName.get(name);
                    if (span == null) return name;
                    return name + " x" + span.calls() + " " + ThinkingStats.millis(span.totalNanos());
                })
                .collect(Collectors.joining(", "));
    }

    private static String inputs(Game game, Player seat) {
        return "cards " + seat.getActionCards().keySet() + ", tokens " + seat.getTacticalCC() + "/"
                + seat.getFleetCC() + "/" + seat.getStrategicCC() + ", tg " + seat.getTg() + ", commodities "
                + seat.getCommodities() + ", explore " + exploreDecks(game) + ", active system "
                + game.getActiveSystem();
    }

    private static String exploreDecks(Game game) {
        return EXPLORE_TRAITS.stream()
                .map(trait -> trait + " " + game.getExploreDeck(trait).size() + "/"
                        + game.getExploreDiscard(trait).size())
                .collect(Collectors.joining(" "));
    }
}
