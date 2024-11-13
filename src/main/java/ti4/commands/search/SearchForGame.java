package ti4.commands.search;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class SearchForGame extends SearchSubcommandData {

    public SearchForGame() {
        super(Constants.SEARCH_FOR_GAME, "Get a specific games channels");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game to find channels of").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = null;
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!GameManager.getGameNameToGame().containsKey(mapName)) {
                MessageHelper.replyToMessage(event, "Game with such name does not exists, use /list_games");
                return;
            }
            game = GameManager.getGame(mapName);
        } else {
            game = GameManager.getUserActiveGame(event.getUser().getId());
            if (game == null) {
                MessageHelper.replyToMessage(event, "No active game set, need to specify what map to show");
                return;
            }
        }
        String gameChannelLink = game.getActionsChannel() == null ? "" : game.getActionsChannel().getAsMention();
        String tabletalkLink = game.getTableTalkChannel() == null ? "" : game.getTableTalkChannel().getAsMention();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Here's the requested channels:\n" + tabletalkLink + "\n" + gameChannelLink);
    }

}
