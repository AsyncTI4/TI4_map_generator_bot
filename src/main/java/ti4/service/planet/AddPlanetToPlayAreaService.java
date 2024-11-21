package ti4.service.planet;

import java.util.HashSet;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@UtilityClass
public class AddPlanetToPlayAreaService {

    public static void addPlanetToPlayArea(GenericInteractionCreateEvent event, Tile tile, String planetName, Game game) {
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

        if (unitColors.size() != 1) {
            return;
        }
        String unitColor = unitColors.iterator().next();
        for (Player player : game.getPlayers().values()) {
            if (player.getFaction() != null && player.getColor() != null) {
                String colorID = Mapper.getColorID(player.getColor());
                if (unitColor.equals(colorID)) {
                    if (!player.getPlanetsAllianceMode().contains(planetName)) {
                        AddPlanetService.addPlanet(player, planetName, game, event, false);
                    }
                    break;
                }
            }
        }
    }
}
