package ti4.discord.interactions.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.tigl.TiglReportService;

class TiglReportManually extends GameStateSubcommand {

    TiglReportManually() {
        super(Constants.TIGL_REPORT_MANUALLY, "Post a game's TIGL report into this channel", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game to report")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        // handleTiglReporting silently no-ops on these, so say why nothing happened
        if (!game.isCompetitiveTIGLGame()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), game.getName() + " is not a competitive TIGL game.");
            return;
        }
        if (game.getWinner().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), game.getName() + " has no winner - nothing to report.");
            return;
        }

        TiglReportService.handleTiglReporting(game, event.getMessageChannel());
    }
}
