package ti4.commands2.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.player.PlayerStatisticsService;

class PlayerStatistics extends Subcommand {

    private static final String MINIMUM_GAME_COUNT_FILTER = "has_minimum_game_count";
    private static final String MAX_LIST_SIZE = "max_list_size";

    public PlayerStatistics() {
        super("players", "Player Statistics");
        addOptions(new OptionData(OptionType.STRING, Constants.PLAYER_STATISTIC, "Choose a stat to show").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.INTEGER, MINIMUM_GAME_COUNT_FILTER, "Filter by the minimum number of games player has played, default 10"));
        addOptions(new OptionData(OptionType.INTEGER, MAX_LIST_SIZE, "The maximum number of players listed, default 50"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlayerStatisticsService.queueReply(event);
    }
}
