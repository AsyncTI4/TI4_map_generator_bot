package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SearchWarrant extends SpecialSubcommandData {
    public SearchWarrant() {
        super(Constants.SEARCH_WARRANT, "Search Warrant set on/off. ");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color/Faction who gains the Search Warrent").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found.");
            return;
        }
        player.setSearchWarrant();
    }
}
