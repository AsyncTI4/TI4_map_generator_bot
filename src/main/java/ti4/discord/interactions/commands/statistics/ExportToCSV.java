package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.service.statistics.ExportToCsvService;

class ExportToCSV extends Subcommand {

    ExportToCSV() {
        super("export_games_to_csv", "Export game data to a CSV file");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ExportToCsvService.queueReply(event);
    }
}
