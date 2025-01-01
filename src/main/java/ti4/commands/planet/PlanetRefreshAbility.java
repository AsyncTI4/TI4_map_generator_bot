package ti4.commands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

public class PlanetRefreshAbility extends PlanetAddRemove {

    public PlanetRefreshAbility() {
        super(Constants.PLANET_REFRESH_ABILITY, "Ready Planet Ability");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        player.refreshPlanetAbility(planet);
    }
}
