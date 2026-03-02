package ti4.commands.tigl;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.spring.service.tigl.TiglGamesInfoService;

class Games extends Subcommand {

    Games() {
        super(Constants.GAMES, "Show ongoing TIGL games grouped by rank");
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.SHOW_GAME_IDS,
                "True to also show the game IDs for each rank (default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean showGameIds = event.getOption(Constants.SHOW_GAME_IDS, false, OptionMapping::getAsBoolean);
        String message = TiglGamesInfoService.getBean().getOngoingGamesByRankMessage(showGameIds);
        if (!showGameIds) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else {
            MessageHelper.sendMessageToThread(event.getChannel(), "Ongoing TIGL games", message);
        }
    }
}
