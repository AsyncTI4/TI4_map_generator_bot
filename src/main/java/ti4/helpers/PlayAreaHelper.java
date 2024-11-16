package ti4.helpers;

import java.util.HashSet;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.planet.PlanetAdd;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@UtilityClass
public class PlayAreaHelper {

    public static void addPlanetToPlayArea(Game game, GenericInteractionCreateEvent event, Tile tile, String planetName) {
        if (Constants.SPACE.equals(planetName)) {
            return;
        }
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        if (unitHolder == null) {
            return;
        }
        Set<Units.UnitKey> allUnitsOnPlanet = unitHolder.getUnits().keySet();
        Set<String> unitColors = new HashSet<>();
        for (Units.UnitKey unit_ : allUnitsOnPlanet) {
            String unitColor = unit_.getColorID();
            if (unit_.getUnitType() != Units.UnitType.Fighter) {
                unitColors.add(unitColor);
            }
        }

        if (unitColors.size() == 1) {
            String unitColor = unitColors.iterator().next();
            for (Player player : game.getPlayers().values()) {
                if (player.getFaction() != null && player.getColor() != null) {
                    String colorID = Mapper.getColorID(player.getColor());
                    if (unitColor.equals(colorID)) {
                        if (!player.getPlanetsAllianceMode().contains(planetName)) {
                            PlanetAdd.doAction(player, planetName, game, event, false);
                        }
                        break;
                    }
                }
            }
        }
    }
}
