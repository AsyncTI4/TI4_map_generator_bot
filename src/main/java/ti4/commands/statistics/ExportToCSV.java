package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.service.statistics.ExportToCsvService;

class ExportToCSV extends Subcommand {

    public ExportToCSV() {
        super("export_games_to_csv", "Export game data to a CSV file");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
        addOptions(new OptionData(
                        OptionType.STRING,
                        GameStatisticsFilterer.WINNING_FACTION_FILTER,
                        "Filter games by if the game was won by said faction")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ExportToCsvService.queueReply(event);
    }
}
