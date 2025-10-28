package ti4.service.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.model.UnitModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AddUnitService {

    public static void addUnits(GenericInteractionCreateEvent event, Game game, List<RemovedUnit> removedUnits) {
        for (RemovedUnit unit : removedUnits) {
            unit.uh().addUnitsWithStates(unit.unitKey(), unit.states());

            Tile tile = unit.tile();
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(
                    event, tile, unit.uh().getName(), game);

            String color = unit.unitKey().getColorID();
            handleFogOfWar(tile, color, game, unit.unitKey() + " " + unit.getTotalRemoved());
            checkFleetCapacity(tile, color, game);
        }
    }

    public static void addUnits(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            String color,
            String unitList,
            List<RemovedUnit> removed) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            List<Integer> states = pickStatesForAddedUnit(parsedUnit, removed);
            tile.getUnitHolders().get(parsedUnit.getLocation()).addUnitsWithStates(parsedUnit.getUnitKey(), states);
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.getLocation(), game);
        }

        handleFogOfWar(tile, color, game, unitList);
        checkFleetCapacity(tile, color, game);
    }

    public static void addUnits(
            GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            tile.addUnit(parsedUnit.getLocation(), parsedUnit.getUnitKey(), parsedUnit.getCount());
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.getLocation(), game);
        }

        handleFogOfWar(tile, color, game, unitList);
        checkFleetCapacity(tile, color, game);
    }

    /**
     * Ignore any locations in the provided unit list, and place them all in "default" locations.
     * This is useful when the unitList is for a different tile than what you're placing.
     * Ex. You draft a starting fleet, and place it on a different home system.
     */
    public static void addUnitsToDefaultLocations(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        Player player = game.getPlayerFromColorOrFaction(color);
        if(player == null) {
            throw new IllegalArgumentException("No player found for color/faction: " + color);
        }
        // Combine units of the same type, ignoring location
        Map<UnitType, Integer> unitCounts = Helper.getUnitList(unitList);
        // Get the planet names, ordered by resources descending
        List<String> planetNames = tile.getPlanetUnitHolders().stream()
                .sorted((ph1, ph2) -> Integer.compare(
                        ph2.getPlanetModel().getResources(),
                        ph1.getPlanetModel().getResources()))
                .map(UnitHolder::getName)
                .toList();

        // Distribute non-space units amongst planets evenly, and dump ships into space
        List<ParsedUnit> assignedUnits = new ArrayList<>();
        for(Entry<UnitType, Integer> entry : unitCounts.entrySet()) {
            UnitType unitType = entry.getKey();
            Integer totalAmt = entry.getValue();
            UnitModel mod =
                    player.getUnitsByAsyncID(unitType.getValue().toLowerCase()).getFirst();
            // Ships go to space
            if(mod.getIsShip()|| (UnitType.Spacedock == unitType
                                    && (player.hasUnit("saar_spacedock") || player.hasUnit("tf-floatingfactory")))) {
                assignedUnits.add(new ParsedUnit(Units.getUnitKey(unitType, color), totalAmt, Constants.SPACE));
                continue;
            }

            // Non-ships get distributed to planets, prioritizing high-resource planets
            int minUnitsPerPlanet = totalAmt / planetNames.size();
            int remainder = totalAmt % planetNames.size();
            for (int i = 0; i < planetNames.size(); i++) {
                int amtForThisPlanet = minUnitsPerPlanet + (i < remainder ? 1 : 0);
                if (amtForThisPlanet > 0) {
                    assignedUnits.add(
                            new ParsedUnit(Units.getUnitKey(unitType, color), amtForThisPlanet, planetNames.get(i)));
                }
            }
        }

        // Now actually add the units
        StringBuilder unitListBuilder = new StringBuilder();
        boolean first = true;
        for (ParsedUnit parsedUnit : assignedUnits) {
            tile.addUnit(parsedUnit.getLocation(), parsedUnit.getUnitKey(), parsedUnit.getCount());
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.getLocation(), game);
            if (!first) {
                unitListBuilder.append(", ");
            }
            unitListBuilder.append(parsedUnit.getCount()).append(" ").append(parsedUnit.getUnitKey().asyncID()).append(" ").append(parsedUnit.getLocation());
            first = false;
        }

        handleFogOfWar(tile, color, game, unitListBuilder.toString());
        checkFleetCapacity(tile, color, game);
    }

    private static void handleFogOfWar(Tile tile, String color, Game game, String unitList) {
        if (!game.isFowMode()) return;

        if (isTileAlreadyPinged(game, tile)) return;

        FoWHelper.pingSystem(
                game,
                tile.getPosition(),
                ColorEmojis.getColorEmojiWithName(color) + " has modified units in the system: " + unitList);

        markTileAsPinged(game, tile);
    }

    private static boolean isTileAlreadyPinged(Game game, Tile tile) {
        return game.getListOfTilesPinged().contains(tile.getPosition());
    }

    private static void markTileAsPinged(Game game, Tile tile) {
        game.setTileAsPinged(tile.getPosition());
    }

    private static void checkFleetCapacity(Tile tile, String color, Game game) {
        Player player = game.getPlayerFromColorOrFaction(color);
        if (player != null) {
            ButtonHelper.checkFleetAndCapacity(player, game, tile);
        }
    }

    private static List<Integer> pickStatesForAddedUnit(ParsedUnit unit, List<RemovedUnit> removed) {
        int amtRemaining = unit.getCount();
        List<Integer> states = UnitState.emptyList();
        for (UnitState state : UnitState.defaultAddStatusOrder()) {
            for (RemovedUnit rm : removed) {
                if (!rm.unitKey().equals(unit.getUnitKey())) continue;
                if (rm.getTotalRemoved() == 0) continue; // depleted

                int amtRemoved = rm.states().get(state.ordinal());
                if (amtRemoved > 0 && amtRemaining > 0) {
                    int take = Math.min(amtRemaining, amtRemoved);
                    rm.states().set(state.ordinal(), amtRemoved - take);
                    states.set(state.ordinal(), states.get(state.ordinal()) + take);
                    amtRemaining -= take;
                }
            }
        }
        if (amtRemaining > 0) {
            states.set(0, states.getFirst() + amtRemaining);
        }
        return states;
    }
}
