package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.game.GameStatisticsService;

class GameStatistics extends Subcommand {

    public GameStatistics() {
        super(Constants.GAMES, "Game Statistics");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_STATISTIC, "Choose a statistic to show")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction that you wish to get the history of")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GameStatisticsService.queueReply(event);
    }
}
