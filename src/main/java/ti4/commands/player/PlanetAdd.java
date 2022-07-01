package ti4.commands.player;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.UnitHolder;

import java.util.List;

public class PlanetAdd extends PlanetAddRemove {
    public PlanetAdd() {
        super(Constants.PLANET_ADD, "Add Planet");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
        player.addPlanet(planet);
        player.exhaustPlanet(planet);
        if (planet.equals("mirage")){
            map.clearPlanetsCache();
        }
        UnitHolder unitHolder = map.getPlanetsInfo().get(planet);
        String color = player.getColor();
        if (unitHolder != null && color != null && !"white".equals(color)) {
            String ccID = Mapper.getControlID(color);
            String ccPath = Mapper.getCCPath(ccID);
            if (ccPath != null) {
                unitHolder.addControl(ccID);
            }
        }
        for (Player player_ : map.getPlayers().values()) {
            if (player_ != player) {
                List<String> planets = player_.getPlanets();
                if (planets.contains(planet)) {
                    if (player_.getExhaustedPlanetsAbilities().contains(planet)) {
                        player.exhaustPlanetAbility(planet);
                    }
                    player_.removePlanet(planet);
                }
            }
        }

    }
}
