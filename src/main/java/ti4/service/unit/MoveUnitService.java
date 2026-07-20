package ti4.service.unit;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class MoveUnitService {

    public void moveUnits(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            String color,
            String unitList,
            Tile dest,
            String uhDest) {
        moveUnits(event, tile, game, color, unitList, dest, uhDest, false);
    }

    public void moveUnits(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            String color,
            String unitList,
            Tile dest,
            String uhDest,
            boolean dmg) {
        Player player = game.getPlayerFromColorOrFaction(color);
        boolean stationWasExhausted =
                player != null && player.getExhaustedPlanets().contains("aurelionstation");
        boolean stationAbilityWasExhausted =
                player != null && player.getExhaustedPlanetsAbilities().contains("aurelionstation");
        List<RemovedUnit> removed = RemoveUnitService.removeUnits(event, tile, game, color, unitList, dmg);
        List<RemovedUnit> toAdd =
                removed.stream().map(r -> r.onUnitHolder(dest, uhDest)).toList();
        AddUnitService.addUnits(event, game, toAdd);

        // Moving Aurelion briefly removes its attached station card; retain its prior exhausted state.
        if (player != null
                && removed.stream().anyMatch(unit -> unit.unitKey().unitType() == UnitType.Aurelion)
                && player.hasPlanet("aurelionstation")) {
            if (stationWasExhausted) {
                player.exhaustPlanet("aurelionstation");
            }
            if (stationAbilityWasExhausted) {
                player.exhaustPlanetAbility("aurelionstation");
            }
        }
    }

    public void replaceUnit(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder uh,
            UnitType oldType,
            UnitType newType) {
        replaceUnit(event, game, player, tile, uh, oldType, newType, 1);
    }

    // Replacing with a unit from reinforcments clears state
    public void replaceUnit(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder uh,
            UnitType oldType,
            UnitType newType,
            int amt) {
        String unitList = amt + " " + newType.getValue();
        if (uh instanceof Planet) unitList += " " + uh.getName();
        AddUnitService.addUnits(event, tile, game, player.getColor(), unitList);
        RemoveUnitService.removeUnit(event, tile, game, player, uh, oldType, amt);
    }
}
