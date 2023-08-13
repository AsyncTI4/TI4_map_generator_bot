package ti4.commands.planet;

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
    public void doAction(Player player, String planet, Map activeMap) {
        player.removePlanet(planet);
        UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
        removePlayerControlToken(player, unitHolder);
    }

    public static void removePlayerControlToken(Player player, UnitHolder unitHolder) {
        String color = player.getColor();
        if (unitHolder != null && color != null && !"null".equals(color)) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.removeControl(ccID);
            }
        }
    }
}
