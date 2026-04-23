package ti4.discord.interactions.slashcommands.search;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class SearchForGame extends GameStateSubcommand {

    public SearchForGame() {
        super(Constants.SEARCH_FOR_GAME, "Get a specific games channels", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game to find channels of")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String gameChannelLink =
                game.getActionsChannel() == null ? "" : game.getActionsChannel().getAsMention();
        String tableTalkLink = game.getTableTalkChannel() == null
                ? ""
                : game.getTableTalkChannel().getAsMention();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Here's the requested channels:\n" + tableTalkLink + "\n" + gameChannelLink);
    }
}
