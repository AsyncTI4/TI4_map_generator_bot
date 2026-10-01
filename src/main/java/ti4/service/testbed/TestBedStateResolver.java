package ti4.service.testbed;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;

@UtilityClass
public class TestBedStateResolver {

    public static final String STORED_PREFIX = "stored:";
    public static final String GAME_PREFIX = "game.";
    public static final List<String> GAME_FIELDS = List.of(
            "phase",
            "round",
            "activePlayer",
            "speaker",
            "playedScs",
            "acDiscard",
            "agendaDiscard",
            "laws",
            "revealedPos",
            "purgedPns",
            "exploreDiscard");
    public static final List<String> SEAT_FIELDS = List.of(
            "tg",
            "commodities",
            "ccs",
            "scs",
            "acs",
            "acIds",
            "sos",
            "soIds",
            "relics",
            "planets",
            "exhaustedPlanets",
            "techs",
            "passed",
            "followed",
            "leaders",
            "pns",
            "pnsInPlay",
            "sosScored",
            "exhaustedTechs",
            "purgedTechs",
            "exhaustedRelics");

    @Nullable
    public static String validatePath(String path) {
        if (path.startsWith(STORED_PREFIX)) {
            return path.length() > STORED_PREFIX.length() ? null : "`stored:` needs a key";
        }
        if (path.startsWith(GAME_PREFIX)) {
            String field = path.substring(GAME_PREFIX.length());
            return GAME_FIELDS.contains(field) ? null : "unknown game field `" + field + "`; use " + GAME_FIELDS;
        }
        int dot = path.indexOf('.');
        if (dot <= 0) return "state `" + path + "` must be `<seat>.<field>`, `game.<field>` or `stored:<key>`";
        String field = path.substring(dot + 1);
        return SEAT_FIELDS.contains(field) ? null : "unknown seat field `" + field + "`; use " + SEAT_FIELDS;
    }

    public static String resolve(Game game, String path, Function<String, Player> seatLookup) {
        if (path.startsWith(STORED_PREFIX)) return game.getStoredValue(path.substring(STORED_PREFIX.length()));
        if (path.startsWith(GAME_PREFIX)) return gameField(game, path.substring(GAME_PREFIX.length()));
        int dot = path.indexOf('.');
        Player seat = seatLookup.apply(path.substring(0, dot));
        if (seat == null) return "<no seat " + path.substring(0, dot) + ">";
        return seatField(seat, path.substring(dot + 1));
    }

    private static String gameField(Game game, String field) {
        return switch (field) {
            case "phase" -> game.getPhaseOfGame();
            case "round" -> String.valueOf(game.getRound());
            case "activePlayer" -> factionOf(game.getActivePlayer());
            case "speaker" -> factionOf(game.getSpeaker());
            case "playedScs" ->
                joinSorted(game.getPlayedSCs().stream().map(String::valueOf).toList());
            case "acDiscard" -> joinSorted(game.getDiscardActionCards().keySet());
            case "agendaDiscard" -> joinSorted(game.getDiscardAgendas().keySet());
            case "laws" -> joinSorted(game.getLaws().keySet());
            case "revealedPos" -> joinSorted(game.getRevealedPublicObjectives().keySet());
            case "purgedPns" -> joinSorted(game.getPurgedPN());
            case "exploreDiscard" -> joinSorted(game.getAllExploreDiscard());
            default -> "<unknown " + field + ">";
        };
    }

    private static String seatField(Player seat, String field) {
        return switch (field) {
            case "tg" -> String.valueOf(seat.getTg());
            case "commodities" -> String.valueOf(seat.getCommodities());
            case "ccs" -> seat.getTacticalCC() + "/" + seat.getFleetCC() + "/" + seat.getStrategicCC();
            case "scs" -> joinSorted(seat.getSCs().stream().map(String::valueOf).toList());
            case "acs" -> String.valueOf(seat.getActionCards().size());
            case "acIds" -> joinSorted(seat.getActionCards().keySet());
            case "sos" -> String.valueOf(seat.getSecretsUnscored().size());
            case "soIds" -> joinSorted(seat.getSecretsUnscored().keySet());
            case "relics" -> joinSorted(seat.getRelics());
            case "planets" -> joinSorted(seat.getPlanets());
            case "exhaustedPlanets" -> joinSorted(seat.getExhaustedPlanets());
            case "techs" -> joinSorted(seat.getTechs());
            case "passed" -> String.valueOf(seat.isPassed());
            case "followed" ->
                joinSorted(IntStream.rangeClosed(1, 8)
                        .filter(seat::hasFollowedSC)
                        .mapToObj(String::valueOf)
                        .toList());
            case "leaders" ->
                joinSorted(seat.getLeaders().stream()
                        .map(TestBedStateResolver::leaderState)
                        .toList());
            case "pns" -> joinSorted(seat.getPromissoryNotes().keySet());
            case "pnsInPlay" -> joinSorted(seat.getPromissoryNotesInPlayArea());
            case "sosScored" -> joinSorted(seat.getSecretsScored().keySet());
            case "exhaustedTechs" -> joinSorted(seat.getExhaustedTechs());
            case "purgedTechs" -> joinSorted(seat.getPurgedTechs());
            case "exhaustedRelics" -> joinSorted(seat.getExhaustedRelics());
            default -> "<unknown " + field + ">";
        };
    }

    private static String leaderState(Leader leader) {
        String state = leader.isLocked() ? "locked" : leader.isExhausted() ? "exhausted" : "ready";
        return leader.getId() + ":" + state;
    }

    private static String factionOf(@Nullable Player player) {
        return player == null ? "" : player.getFaction();
    }

    private static String joinSorted(Collection<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }

    static Map<String, String> snapshot(Player seat) {
        return SEAT_FIELDS.stream().collect(Collectors.toMap(field -> field, field -> seatField(seat, field)));
    }
}
