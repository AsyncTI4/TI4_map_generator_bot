package ti4.discord.interactions.commands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.planet.PlanetService;

class PlanetRefresh extends PlanetAddRemove {

    PlanetRefresh() {
        super(Constants.PLANET_REFRESH, "Ready Planet");
        markSuspicious();
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        PlanetService.refreshPlanet(player, planet);
    }
}
