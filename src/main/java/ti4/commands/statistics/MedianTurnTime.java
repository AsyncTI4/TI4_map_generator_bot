package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.MedianTurnTimeService;

class MedianTurnTime extends Subcommand {

    public MedianTurnTime() {
        super(Constants.MEDIAN_TURN_TIME, "Median turn time across all games for all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_TURNS, "Minimum number of turns to show (Default = 1)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MedianTurnTimeService.queueReply(event);
    }
}
