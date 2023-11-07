package ti4.commands.planet;

import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.map.Game;
import ti4.map.Player;
public class PlanetExhaust extends PlanetAddRemove {
    public PlanetExhaust() {
        super(Constants.PLANET_EXHAUST, "Exhaust Planet");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        if (!player.hasPlanetReady(planet)) return;
        DiscordantStarsHelper.handleOlradinPoliciesWhenExhaustingPlanets(activeGame, player, planet);
        player.exhaustPlanet(planet);
    }
}
