package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.AverageTurnTimeService;

class AverageTurnTime extends Subcommand {

    public AverageTurnTime() {
        super(Constants.AVERAGE_TURN_TIME, "Average turn time across all games for all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_TURNS, "Minimum number of turns to show (Default = 1)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_MEDIAN, "True to also show median next to average (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AverageTurnTimeService.queueReply(event);
    }
}
