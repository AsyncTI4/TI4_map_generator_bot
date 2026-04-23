package ti4.discord.interactions.slashcommands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.planet.PlanetService;

class PlanetRefresh extends PlanetAddRemove {

    PlanetRefresh() {
        super(Constants.PLANET_REFRESH, "Ready Planet");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        PlanetService.refreshPlanet(player, planet);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
