package ti4.discord.interactions.commands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

class PlanetRefreshAbility extends PlanetAddRemove {

    PlanetRefreshAbility() {
        super(Constants.PLANET_REFRESH_ABILITY, "Ready Planet Ability");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        player.refreshPlanetAbility(planet);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
