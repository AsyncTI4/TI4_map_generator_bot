package ti4.commands2.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.PlanetService;

public class PlanetRefresh extends PlanetAddRemove {

    public PlanetRefresh() {
        super(Constants.PLANET_REFRESH, "Ready Planet");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        PlanetService.refreshPlanet(player, planet);
    }
}
