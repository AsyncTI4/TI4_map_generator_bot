package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.UnitModel;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
public class ThirtyKSecrets {

    private static final List<String> TRAITS = List.of("hazardous", "cultural", "industrial");

    public static boolean isAutoChecked(String objID) {
        return threshold(objID) > 0;
    }

    public static int threshold(String objID) {
        return switch (objID) {
            case "options30k" -> 7;
            case "preparationforwar30k" -> 8;
            case "unnecessarypredictions30k" -> 3;
            case "observingthepossibilitiesjmsc" -> 4;
            case "newworldsjmsc", "distantflagsjmsc" -> 2;
            case "unspentreserves30k",
                    "braveextinction30k",
                    "lightmilitary30k",
                    "typecast30k",
                    "participationtrophy30k",
                    "reclusivenature30k",
                    "nothingtolose30k",
                    "longdistancerelationsjmsc",
                    "wordofmouthjmsc",
                    "payingrespectsjmsc",
                    "proudflagbearerjmsc" -> 1;
            default -> 0;
        };
    }

    public static int progress(String objID, Game game, Player player) {
        return switch (objID) {
            case "unspentreserves30k" -> asProgress(hasFullCommodities(player));
            case "braveextinction30k" ->
                asProgress(ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "infantry", false) < 4);
            case "lightmilitary30k" -> asProgress(hasLightMilitary(game, player));
            case "typecast30k" -> asProgress(isTypecast(game, player));
            case "participationtrophy30k" -> asProgress(earnsParticipationTrophy(game, player, objID));
            case "options30k" -> player.getAcCount();
            case "reclusivenature30k" -> asProgress(isReclusive(game, player));
            case "nothingtolose30k" -> asProgress(hasNothingToLose(player));
            case "preparationforwar30k" -> player.getTacticalCC() + player.getFleetCC() + player.getStrategicCC();
            case "unnecessarypredictions30k" -> player.getStrategicCC();
            case "longdistancerelationsjmsc" -> asProgress(holdsDistantNote(game, player, "_an"));
            case "wordofmouthjmsc" -> asProgress(holdsDistantNote(game, player, "_sftt"));
            case "payingrespectsjmsc" -> asProgress(hasShipsNextToEliminatedHome(game, player));
            case "newworldsjmsc" -> distantControlledPlanets(game, player);
            case "observingthepossibilitiesjmsc" -> distinctWormholeSystems(game, player);
            case "distantflagsjmsc" -> distantStructures(game, player);
            case "proudflagbearerjmsc" -> asProgress(hasLoneDistantFlagship(game, player));
            default -> 0;
        };
    }

    private static int asProgress(boolean met) {
        return met ? 1 : 0;
    }

    private static boolean hasFullCommodities(Player player) {
        int max = player.getCommoditiesTotal();
        return max > 0 && player.getCommodities() >= max;
    }

    private static boolean hasLightMilitary(Game game, Player player) {
        int nonFighterShips = 0;
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space == null) continue;
            for (UnitKey key : space.getUnitKeysForPlayer(player)) {
                int count = space.getUnitCount(key);
                if (count < 1) continue;
                UnitType type = key.unitType();
                if (type == UnitType.Dreadnought || type == UnitType.Warsun || type == UnitType.Flagship) return false;
                UnitModel model = player.getUnitFromUnitKey(key);
                if (model != null && model.getIsShip() && type != UnitType.Fighter) nonFighterShips += count;
            }
        }
        return nonFighterShips > 8;
    }

    private static boolean isTypecast(Game game, Player player) {
        List<Planet> planets = controlledPlanets(game, player);
        if (planets.isEmpty()) return false;
        Map<String, Integer> perTrait = new HashMap<>();
        for (Planet planet : planets) {
            if (planet.isHomePlanet(game)) continue;
            for (String trait : planet.getPlanetTypes()) {
                if (TRAITS.contains(trait)) perTrait.merge(trait, 1, Integer::sum);
            }
        }
        int best = perTrait.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return best * 2 > planets.size();
    }

    private static boolean earnsParticipationTrophy(Game game, Player player, String objID) {
        String scoredPublic = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
        boolean scoredNoPublic = scoredPublic.isBlank() || "None".equalsIgnoreCase(scoredPublic);
        boolean noOtherUnscoredSecret =
                player.getSecretsUnscored().keySet().stream().allMatch(objID::equals);
        return scoredNoPublic && noOtherUnscoredSecret;
    }

    private static boolean isReclusive(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return false;
        Set<String> allowed = new HashSet<>(FoWHelper.getAdjacentTiles(game, home.getPosition(), player, false, true));
        allowed.add(home.getPosition());
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .allMatch(tile -> allowed.contains(tile.getPosition()));
    }

    private static boolean hasNothingToLose(Player player) {
        return player.getAcCount() == 0
                && player.getTg() == 0
                && player.getReadiedPlanets().isEmpty();
    }

    private static boolean holdsDistantNote(Game game, Player player, String noteSuffix) {
        return Stream.concat(
                        player.getPromissoryNotesInPlayArea().stream(), player.getPromissoryNotes().keySet().stream())
                .filter(pn -> pn.endsWith(noteSuffix))
                .map(game::getPNOwner)
                .filter(Objects::nonNull)
                .filter(owner -> owner != player)
                .anyMatch(owner -> ThirtyKDistance.homesMoreThanFiveApart(game, player, owner));
    }

    private static boolean hasShipsNextToEliminatedHome(Game game, Player player) {
        for (Player eliminated : game.getRealAndEliminatedPlayers()) {
            if (!eliminated.isEliminated() || eliminated == player) continue;
            Tile home = eliminated.getHomeSystemTile();
            if (home == null) continue;
            for (String position : FoWHelper.getAdjacentTiles(game, home.getPosition(), player, false, false)) {
                Tile adjacent = game.getTileByPosition(position);
                if (adjacent != null && FoWHelper.playerHasShipsInSystem(player, adjacent)) return true;
            }
        }
        return false;
    }

    private static int distantControlledPlanets(Game game, Player player) {
        Predicate<String> farFromHome = farFromHome(game, player);
        int count = 0;
        for (Planet planet : controlledPlanets(game, player)) {
            Tile tile = game.getTileFromPlanet(planet.getName());
            if (tile != null && farFromHome.test(tile.getPosition())) count++;
        }
        return count;
    }

    private static int distinctWormholeSystems(Game game, Player player) {
        List<Set<Wormhole>> wormholesPerSystem = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasUnitsInSystem(player, tile)) continue;
            Set<Wormhole> wormholes = new HashSet<>(tile.getWormholes(game));
            if (!wormholes.isEmpty()) wormholesPerSystem.add(wormholes);
        }
        return largestDistinctMatching(wormholesPerSystem);
    }

    static <T> int largestDistinctMatching(Collection<? extends Set<T>> options) {
        List<Set<T>> rows = new ArrayList<>(options);
        Map<T, Integer> claimedBy = new HashMap<>();
        int matched = 0;
        for (int row = 0; row < rows.size(); row++) {
            if (claim(row, rows, claimedBy, new HashSet<>())) matched++;
        }
        return matched;
    }

    private static <T> boolean claim(int row, List<Set<T>> rows, Map<T, Integer> claimedBy, Set<T> visited) {
        for (T option : rows.get(row)) {
            if (!visited.add(option)) continue;
            Integer holder = claimedBy.get(option);
            if (holder == null || claim(holder, rows, claimedBy, visited)) {
                claimedBy.put(option, row);
                return true;
            }
        }
        return false;
    }

    private static int distantStructures(Game game, Player player) {
        Predicate<String> farFromHome = farFromHome(game, player);
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (!farFromHome.test(tile.getPosition())) continue;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                    UnitModel model = player.getUnitFromUnitKey(key);
                    if (model != null && model.getIsStructure()) count += holder.getUnitCount(key);
                }
            }
        }
        return count;
    }

    private static boolean hasLoneDistantFlagship(Game game, Player player) {
        Predicate<String> farFromHome = farFromHome(game, player);
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space == null || space.getUnitCount(UnitType.Flagship, player) < 1) continue;
            if (farFromHome.test(tile.getPosition()) && otherNonFighterShips(player, space) == 0) return true;
        }
        return false;
    }

    private static int otherNonFighterShips(Player player, UnitHolder space) {
        int count = 0;
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitType type = key.unitType();
            if (type == UnitType.Flagship || type == UnitType.Fighter) continue;
            UnitModel model = player.getUnitFromUnitKey(key);
            if (model != null && model.getIsShip()) count += space.getUnitCount(key);
        }
        return count;
    }

    private static List<Planet> controlledPlanets(Game game, Player player) {
        return player.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .filter(planet -> planet.getPlanetModel() == null
                        || !planet.getPlanetModel().getPlanetTypes().contains(PlanetType.FAKE))
                .filter(planet -> !planet.isSpaceStation(game))
                .toList();
    }

    public static Predicate<String> farFromHome(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return position -> false;
        Map<String, Integer> distances = CheckDistanceHelper.getTileDistances(
                game, null, home.getPosition(), ThirtyKDistance.FAR_AWAY + 1, true);
        return position -> {
            Integer distance = distances.get(position);
            return distance == null || distance > ThirtyKDistance.FAR_AWAY;
        };
    }
}
