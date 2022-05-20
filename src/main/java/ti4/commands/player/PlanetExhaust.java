package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;

public class PlanetExhaust extends PlanetAddRemove {
    public PlanetExhaust() {
        super(Constants.PLANET_EXHAUST, "Exhaust Planet");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
        player.exhaustPlanet(planet);
    }
}
