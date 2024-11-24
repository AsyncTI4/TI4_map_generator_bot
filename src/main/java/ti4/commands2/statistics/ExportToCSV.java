package ti4.commands2.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.Subcommand;
import ti4.service.statistics.ExportToCsvService;

class ExportToCSV extends Subcommand {

    public ExportToCSV() {
        super("export_games_to_csv", "Export game data to a CSV file");
        addOptions(GameStatisticFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ExportToCsvService.queueReply(event);
    }
}
