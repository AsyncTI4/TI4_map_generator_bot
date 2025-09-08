package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.service.statistics.StatisticsOptInOutService;

class OptOut extends Subcommand {

    public OptOut() {
        super("opt_out", "Opt out of other players being able to view your individual stats");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StatisticsOptInOutService.optOut(event);
    }
}
