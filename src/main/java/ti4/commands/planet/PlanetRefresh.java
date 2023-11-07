package ti4.commands.planet;

import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

public class PlanetRefresh extends PlanetAddRemove {
    public PlanetRefresh() {
        super(Constants.PLANET_REFRESH, "Ready Planet");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        player.refreshPlanet(planet);
    }
}
