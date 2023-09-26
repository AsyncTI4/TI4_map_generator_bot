package ti4.commands.planet;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;

public class PlanetRemove extends PlanetAddRemove {
    public PlanetRemove() {
        super(Constants.PLANET_REMOVE, "Remove Planet");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        player.removePlanet(planet);
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
        removePlayerControlToken(player, unitHolder);

        if (Constants.MR.equals(planet) && player.hasCustodiaVigilia()) {
            Planet mecatolRex = (Planet) unitHolder;
            if (mecatolRex != null) {
                mecatolRex.setSpaceCannonDieCount(0);
                mecatolRex.setSpaceCannonHitsOn(0);
            }
        }
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
