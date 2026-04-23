package ti4.discord.interactions.slashcommands.planet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.planet.PlanetService;

public class PlanetRefreshAll extends GameStateSubcommand {

    public PlanetRefreshAll() {
        super(Constants.PLANET_REFRESH_ALL, "Ready All Planets", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        PlanetService.refreshAllPlanets(player);
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " readied all planets.");
    }
}
