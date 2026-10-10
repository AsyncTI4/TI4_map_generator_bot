package ti4.service.testbed;

import java.util.ArrayList;
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
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;

@UtilityClass
public class TestBedStateResolver {

    public static final String STORED_PREFIX = "stored:";
    public static final String GAME_PREFIX = "game.";
    public static final String TILE_PREFIX = "tile.";
    public static final String PLANET_PREFIX = "planet.";
    public static final List<String> TILE_FIELDS = List.of("units", "ccs", "tokens", "planets");
    public static final List<String> PLANET_FIELDS = List.of("owner", "units", "tokens");
    public static final List<String> GAME_FIELDS = List.of(
            "activeSystem",
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
            "exploreDiscard",
            "borderAnomalies");
    public static final List<String> SEAT_FIELDS = List.of(
            "tg",
            "commodities",
            "ccs",
            "tacticalCcs",
            "fleetCcs",
            "strategyCcs",
            "vp",
            "debt",
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
            "posScored",
            "fragments",
            "breakthroughs",
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
        if (path.startsWith(TILE_PREFIX)) return validateLocationPath(path, TILE_PREFIX, "<position>", TILE_FIELDS);
        if (path.startsWith(PLANET_PREFIX)) {
            return validateLocationPath(path, PLANET_PREFIX, "<planet>", PLANET_FIELDS);
        }
        int dot = path.indexOf('.');
        if (dot <= 0) return "state `" + path + "` must be `<seat>.<field>`, `game.<field>` or `stored:<key>`";
        String field = path.substring(dot + 1);
        return SEAT_FIELDS.contains(field) ? null : "unknown seat field `" + field + "`; use " + SEAT_FIELDS;
    }

    @Nullable
    private static String validateLocationPath(String path, String prefix, String placeholder, List<String> fields) {
        String rest = path.substring(prefix.length());
        int dot = rest.lastIndexOf('.');
        if (dot <= 0) return "state `" + path + "` must be `" + prefix + placeholder + ".<field>`";
        String field = rest.substring(dot + 1);
        return fields.contains(field) ? null : "unknown " + prefix + " field `" + field + "`; use " + fields;
    }

    public static String resolve(Game game, String path, Function<String, Player> seatLookup) {
        if (path.startsWith(STORED_PREFIX)) return game.getStoredValue(path.substring(STORED_PREFIX.length()));
        if (path.startsWith(GAME_PREFIX)) return gameField(game, path.substring(GAME_PREFIX.length()));
        if (path.startsWith(TILE_PREFIX)) return tileField(game, path.substring(TILE_PREFIX.length()));
        if (path.startsWith(PLANET_PREFIX)) return planetField(game, path.substring(PLANET_PREFIX.length()));
        int dot = path.indexOf('.');
        Player seat = seatLookup.apply(path.substring(0, dot));
        if (seat == null) return "<no seat " + path.substring(0, dot) + ">";
        return seatField(seat, path.substring(dot + 1));
    }

    private static String gameField(Game game, String field) {
        return switch (field) {
            case "activeSystem" -> game.getActiveSystem() == null ? "" : game.getActiveSystem();
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
            case "borderAnomalies" ->
                joinSorted(game.getBorderAnomalies().stream()
                        .map(border -> border.getTile() + ":" + border.getDirection() + ":" + border.getType())
                        .toList());
            default -> "<unknown " + field + ">";
        };
    }

    private static String seatField(Player seat, String field) {
        return switch (field) {
            case "tg" -> String.valueOf(seat.getTg());
            case "commodities" -> String.valueOf(seat.getCommodities());
            case "ccs" -> seat.getTacticalCC() + "/" + seat.getFleetCC() + "/" + seat.getStrategicCC();
            case "tacticalCcs" -> String.valueOf(seat.getTacticalCC());
            case "fleetCcs" -> String.valueOf(seat.getFleetCC());
            case "strategyCcs" -> String.valueOf(seat.getStrategicCC());
            case "vp" -> String.valueOf(seat.getTotalVictoryPoints());
            case "debt" ->
                joinSorted(seat.getDebtTokens().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toList());
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
            case "fragments" -> joinSorted(seat.getFragments());
            case "breakthroughs" ->
                joinSorted(seat.getBreakthroughIDs().stream()
                        .map(id -> id + ":" + breakthroughState(seat, id))
                        .toList());
            case "posScored" ->
                joinSorted(seat.getGame().getScoredPublicObjectives().entrySet().stream()
                        .filter(entry -> entry.getValue().contains(seat.getUserID()))
                        .map(Map.Entry::getKey)
                        .toList());
            case "exhaustedTechs" -> joinSorted(seat.getExhaustedTechs());
            case "purgedTechs" -> joinSorted(seat.getPurgedTechs());
            case "exhaustedRelics" -> joinSorted(seat.getExhaustedRelics());
            default -> "<unknown " + field + ">";
        };
    }

    private static String tileField(Game game, String positionAndField) {
        int dot = positionAndField.lastIndexOf('.');
        String position = positionAndField.substring(0, dot);
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return "<no tile at " + position + ">";
        Collection<UnitHolder> holders = tile.getUnitHolders().values();
        return switch (positionAndField.substring(dot + 1)) {
            case "units" ->
                joinSorted(holders.stream()
                        .flatMap(holder -> units(holder).stream())
                        .toList());
            case "ccs" -> joinSorted(tile.getSpaceUnitHolder().getCcList());
            case "tokens" ->
                joinSorted(holders.stream()
                        .flatMap(holder -> holder.getTokenList().stream().map(token -> holder.getName() + ":" + token))
                        .toList());
            case "planets" ->
                joinSorted(tile.getPlanetUnitHolders().stream()
                        .map(Planet::getName)
                        .toList());
            default -> "<unknown " + positionAndField + ">";
        };
    }

    private static String planetField(Game game, String planetAndField) {
        int dot = planetAndField.lastIndexOf('.');
        String planetId = planetAndField.substring(0, dot);
        Planet planet = game.getUnitHolderFromPlanet(planetId);
        if (planet == null) return "<no planet " + planetId + ">";
        return switch (planetAndField.substring(dot + 1)) {
            case "owner" -> factionOf(game.getPlayerThatControlsPlanet(planetId));
            case "units" -> joinSorted(units(planet));
            case "tokens" -> joinSorted(planet.getTokenList());
            default -> "<unknown " + planetAndField + ">";
        };
    }

    private static List<String> units(UnitHolder holder) {
        List<String> units = new ArrayList<>();
        for (Map.Entry<UnitKey, Integer> unit : holder.getUnits().entrySet()) {
            if (unit.getValue() <= 0) continue;
            UnitKey key = unit.getKey();
            units.add(holder.getName() + ":" + key.getColor() + "_" + key.asyncID() + "=" + unit.getValue());
        }
        return units;
    }

    private static String breakthroughState(Player seat, String id) {
        if (!seat.isBreakthroughUnlocked(id)) return "locked";
        return seat.isBreakthroughExhausted(id) ? "exhausted" : "unlocked";
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
