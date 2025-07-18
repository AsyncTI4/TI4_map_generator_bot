package ti4.service.unit;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitState;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.service.emoji.ColorEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
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
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, unit.uh().getName(), game);

            String color = unit.unitKey().getColorID();
            handleFogOfWar(tile, color, game, unit.unitKey() + " " + unit.getTotalRemoved());
            checkFleetCapacity(tile, color, game);
        }
    }

    public static void addUnits(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList, List<RemovedUnit> removed) {
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

    public static void addUnits(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            tile.addUnit(parsedUnit.getLocation(), parsedUnit.getUnitKey(), parsedUnit.getCount());
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.getLocation(), game);
        }

        handleFogOfWar(tile, color, game, unitList);
        checkFleetCapacity(tile, color, game);
    }

    private static void handleFogOfWar(Tile tile, String color, Game game, String unitList) {
        if (!game.isFowMode()) return;

        if (isTileAlreadyPinged(game, tile)) return;

        FoWHelper.pingSystem(game, tile.getPosition(),
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
            states.set(0, states.get(0) + amtRemaining);
        }
        return states;
    }

}
