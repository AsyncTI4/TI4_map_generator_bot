package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;

public class PlanetRefreshAbility extends PlanetAddRemove {
    public PlanetRefreshAbility() {
        super(Constants.PLANET_REFRESH_ABILITY, "Refresh Planet ability");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
        player.refreshPlanetAbility(planet);
    }
}
