package ti4.commands.player;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;

public class PlanetExhaustAbility extends PlanetAddRemove {
    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }

    @Override
    public void doAction(Player player, String planet, Map map) {
        player.exhaustPlanetAbility(planet);
    }
}
