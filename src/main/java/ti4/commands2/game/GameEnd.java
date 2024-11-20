package ti4.commands2.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.game.EndGameService;

class GameEnd extends GameStateSubcommand {

    public GameEnd() {
        super(Constants.GAME_END, "Declare the game has ended", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm ending the game with 'YES'").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.PUBLISH, "True to publish results to #pbd-chronicles. (Default: True)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ARCHIVE_CHANNELS, "True to archive the channels and delete the game role (Default: True)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.REMATCH, "True to start another game using the same channels (Default: False)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String gameName = game.getName();
        if (!gameName.equals(StringUtils.substringBefore(event.getChannel().getName(), "-"))) {
            MessageHelper.replyToMessage(event, "`/game end` must be executed in game channel only!");
            return;
        }
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (!"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with 'YES'");
            return;
        }
        boolean publish = event.getOption(Constants.PUBLISH, true, OptionMapping::getAsBoolean);
        boolean archiveChannels = event.getOption(Constants.ARCHIVE_CHANNELS, true, OptionMapping::getAsBoolean);
        boolean rematch = event.getOption(Constants.REMATCH, false, OptionMapping::getAsBoolean);
        EndGameService.secondHalfOfGameEnd(event, game, publish, archiveChannels, rematch);
    }
}
