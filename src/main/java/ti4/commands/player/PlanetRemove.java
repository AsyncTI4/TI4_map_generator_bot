package ti4.commands.player;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.UnitHolder;

public class PlanetRemove extends PlanetAddRemove {
    public PlanetRemove() {
        super(Constants.PLANET_REMOVE, "Remove Planet");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
        player.removePlanet(planet);
        UnitHolder unitHolder = map.getPlanetsInfo().get(planet);
        removePlayerControlToken(player, unitHolder);
    }

    public static void removePlayerControlToken(Player player, UnitHolder unitHolder) {
        String color = player.getColor();
        if (unitHolder != null && player.isActivePlayer()) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.removeControl(ccID);
            }
        }
    }
}
