package ti4.discord.interactions.slashcommands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.planet.AddPlanetService;

class PlanetAdd extends PlanetAddRemove {

    public PlanetAdd() {
        super(Constants.PLANET_ADD, "Add or transfer a planet card to your player area");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        AddPlanetService.addPlanet(player, planet, game, event, false);
    }
}
