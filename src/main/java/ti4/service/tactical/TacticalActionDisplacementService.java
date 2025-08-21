package ti4.service.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Space;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.service.regex.RegexService;

@UtilityClass
public class TacticalActionDisplacementService {

    public Map<String, Map<UnitKey, List<Integer>>> reverseAllUnitMovement(Game game, Player player) {
        Set<String> displacedKeys =
                new HashSet<>(game.getTacticalActionDisplacement().keySet());
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();

        Pattern rx = Pattern.compile(RegexHelper.posRegex(game) + "-" + RegexHelper.unitHolderRegex(game, "uh"));
        for (String uhKey : displacedKeys) {
            if (!displaced.containsKey(uhKey)) continue;
            if (uhKey.equals("unk")) continue;

            RegexService.runMatcher(rx, uhKey, matcher -> {
                Tile tile = game.getTileByPosition(matcher.group("pos"));
                reverseTileUnitMovement(game, player, tile);
            });
        }
        return game.getTacticalActionDisplacement();
    }

    public boolean hasUnknownDisplacement(Game game) {
        return game.getTacticalActionDisplacement().containsKey("unk");
    }

    public boolean hasPendingDisplacement(Game game) {
        return !game.getTacticalActionDisplacement().isEmpty();
    }

    public boolean hasDisplacementFromPosition(Game game, String position) {
        return game.getTacticalActionDisplacement().keySet().stream().anyMatch(s -> s.startsWith(position + "-"));
    }

    public Map<String, Map<UnitKey, List<Integer>>> reverseTileUnitMovement(Game game, Player player, Tile tile) {
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            String key = unitHolderKey(tile, uh);
            if (!displaced.containsKey(key)) continue;

            Map<UnitKey, List<Integer>> unitsToRestore = displaced.remove(key);
            for (Entry<UnitKey, List<Integer>> unit : unitsToRestore.entrySet()) {
                uh.addUnitsWithStates(unit.getKey(), unit.getValue());
            }
        }
        return displaced;
    }

    public Map<String, Map<UnitKey, List<Integer>>> moveAllFromTile(Game game, Player player, Tile tile) {
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();
        List<UnitType> movableFromPlanets = new ArrayList<>(List.of(UnitType.Infantry, UnitType.Mech));
        Set<Player> allowedAllies = resolveAllowedAllies(game, player, tile);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            processUnitHolderMovement(game, player, allowedAllies, tile, unitHolder, displaced, movableFromPlanets);
        }
        return displaced;
    }

    public Map<String, Map<UnitKey, List<Integer>>> moveAllShipsFromTile(Game game, Player player, Tile tile) {
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();

        UnitHolder space = tile.getSpaceUnitHolder();
        String uhKey = unitHolderKey(tile, space);

        Set<Player> allowedAllies = resolveAllowedAllies(game, player, tile);
        Map<UnitKey, List<Integer>> movement = displaced.getOrDefault(uhKey, new HashMap<>());
        for (UnitKey unitKey : new HashSet<>(space.getUnitsByState().keySet())) {
            if (!canMoveUnit(player, allowedAllies, unitKey)) continue;
            List<Integer> states = space.removeUnit(unitKey, space.getUnitCount(unitKey));
            movement.put(unitKey, states);
        }
        displaced.put(uhKey, movement);
        return displaced;
    }

    public Map<String, Map<UnitKey, List<Integer>>> moveSingleUnit(
            Game game,
            Player player,
            Tile tile,
            String planetName,
            UnitType type,
            int amt,
            UnitState state,
            String color) {
        UnitHolder uh = planetName == null ? tile.getSpaceUnitHolder() : tile.getUnitHolderFromPlanet(planetName);
        if (uh == null) return game.getTacticalActionDisplacement();
        String uhKey = unitHolderKey(tile, uh);

        Map<UnitKey, List<Integer>> displaced =
                game.getTacticalActionDisplacement().getOrDefault(uhKey, new HashMap<>());
        UnitHolder fakeUh = new Space("fake", null);
        String pColor = resolveColorOrDefault(player, color);
        UnitKey unitKey = Units.getUnitKey(type, pColor);
        if (displaced.containsKey(unitKey)) fakeUh.addUnitsWithStates(unitKey, displaced.get(unitKey));

        List<Integer> statesMoved = uh.removeUnit(unitKey, amt, state);
        fakeUh.addUnitsWithStates(unitKey, statesMoved);
        displaced.put(unitKey, fakeUh.getUnitsByState().get(unitKey));
        game.getTacticalActionDisplacement().put(uhKey, displaced);
        return game.getTacticalActionDisplacement();
    }

    public Map<String, Map<UnitKey, List<Integer>>> reverseSingleUnit(
            Game game,
            Player player,
            Tile tile,
            String planetName,
            UnitType type,
            int amt,
            UnitState state,
            String color) {
        UnitHolder uh = planetName == null ? tile.getSpaceUnitHolder() : tile.getUnitHolderFromPlanet(planetName);
        if (uh == null) return game.getTacticalActionDisplacement();
        String uhKey = unitHolderKey(tile, uh);

        Map<UnitKey, List<Integer>> displaced =
                game.getTacticalActionDisplacement().getOrDefault(uhKey, new HashMap<>());
        String pColor = resolveColorOrDefault(player, color);
        UnitKey unitKey = Units.getUnitKey(type, pColor);
        if (!displaced.containsKey(unitKey)) return game.getTacticalActionDisplacement();

        UnitHolder fakeUh = new Space("fake", null);
        fakeUh.addUnitsWithStates(unitKey, displaced.get(unitKey));

        List<Integer> statesReverted = fakeUh.removeUnit(unitKey, amt, state);
        uh.addUnitsWithStates(unitKey, statesReverted);

        List<Integer> newStates = fakeUh.getUnitsByState().get(unitKey);
        if (newStates == null) {
            displaced.remove(unitKey);
        } else {
            displaced.put(unitKey, newStates);
        }
        game.getTacticalActionDisplacement().put(uhKey, displaced);
        if (displaced.isEmpty()) {
            game.getTacticalActionDisplacement().remove(uhKey);
        }
        return game.getTacticalActionDisplacement();
    }

    public boolean applyDisplacementToActiveSystem(Game game, Player player, Tile tile) {
        boolean moved = false;
        UnitHolder activeSystemSpace = tile.getSpaceUnitHolder();
        for (Entry<String, Map<UnitKey, List<Integer>>> e :
                game.getTacticalActionDisplacement().entrySet()) {
            for (Entry<UnitKey, List<Integer>> unit : e.getValue().entrySet()) {
                if (unit.getValue().stream().mapToInt(Integer::intValue).sum() > 0) moved = true;
                activeSystemSpace.addUnitsWithStates(unit.getKey(), unit.getValue());
            }
        }
        game.getTacticalActionDisplacement().clear();
        return moved;
    }

    /**
     * Stage a full displacement payload and remove units from their origin unit-holders accordingly.
     * This does not move units into the active system; it only prepares the staged state for review.
     */
    public Map<String, Map<UnitKey, List<Integer>>> stageFullDisplacementAndRemoveFromOrigins(
            Game game, Player player, Map<String, Map<UnitKey, List<Integer>>> displacement) {
        if (displacement == null) {
            game.setTacticalActionDisplacement(new HashMap<>());
            return game.getTacticalActionDisplacement();
        }

        for (Entry<String, Map<UnitKey, List<Integer>>> e : displacement.entrySet()) {
            String uhKey = e.getKey();
            if (uhKey == null || "unk".equals(uhKey)) continue;
            int dash = uhKey.indexOf('-');
            if (dash <= 0) continue;

            String pos = uhKey.substring(0, dash);
            String uhName = uhKey.substring(dash + 1);
            Tile srcTile = game.getTileByPosition(pos);
            if (srcTile == null) continue;

            UnitHolder srcHolder = "space".equalsIgnoreCase(uhName)
                    ? srcTile.getSpaceUnitHolder()
                    : srcTile.getUnitHolderFromPlanet(uhName);
            if (srcHolder == null) continue;

            for (Entry<UnitKey, List<Integer>> unitEntry : e.getValue().entrySet()) {
                List<Integer> states = unitEntry.getValue();
                if (states == null) continue;
                for (int i = 0; i < states.size() && i < UnitState.values().length; i++) {
                    int amt = states.get(i) == null ? 0 : states.get(i);
                    if (amt <= 0) continue;
                    srcHolder.removeUnit(unitEntry.getKey(), amt, UnitState.values()[i]);
                }
            }
        }

        game.setTacticalActionDisplacement(displacement);
        return game.getTacticalActionDisplacement();
    }

    private void processUnitHolderMovement(
            Game game,
            Player player,
            Set<Player> allowedAllies,
            Tile tile,
            UnitHolder unitHolder,
            Map<String, Map<UnitKey, List<Integer>>> displaced,
            List<UnitType> movableFromPlanets) {
        String uhKey = unitHolderKey(tile, unitHolder);
        Map<UnitKey, List<Integer>> movement = displaced.getOrDefault(uhKey, new HashMap<>());

        for (UnitKey unitKey : new HashSet<>(unitHolder.getUnitsByState().keySet())) {
            if (!canMoveUnit(player, allowedAllies, unitKey)) continue;
            if (unitHolder instanceof Planet && !movableFromPlanets.contains(unitKey.getUnitType())) continue;

            List<Integer> states = unitHolder.removeUnit(unitKey, unitHolder.getUnitCount(unitKey));
            movement.put(unitKey, states);
        }
        displaced.put(uhKey, movement);
    }

    private boolean canMoveUnit(Player player, Set<Player> allowedAllies, UnitKey unitKey) {
        if (player.unitBelongsToPlayer(unitKey)) return true;

        UnitType unitType = unitKey.getUnitType();
        boolean eligibleType =
                unitType == UnitType.Infantry || unitType == UnitType.Fighter || unitType == UnitType.Mech;
        if (!eligibleType) return false;

        for (Player ally : allowedAllies) {
            if (ally.unitBelongsToPlayer(unitKey)) return true;
        }
        return false;
    }

    private Set<Player> resolveAllowedAllies(Game game, Player player, Tile tile) {
        Set<Player> allowed = new HashSet<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) continue;
            if (!player.getAllianceMembers().contains(p2.getFaction())) continue;
            if (tile.hasPlayerCC(p2)) continue;
            allowed.add(p2);
        }
        return allowed;
    }

    private String resolveColorOrDefault(Player player, String color) {
        return (color != null && !color.isEmpty()) ? color : player.getColor();
    }

    private String unitHolderKey(Tile tile, UnitHolder unitHolder) {
        return tile.getPosition() + "-" + unitHolder.getName();
    }
}
