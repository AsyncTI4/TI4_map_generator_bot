package ti4.discord.interactions.commands.plot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.service.franken.FrankenPlotService;

class PlotAdd extends PlotAddRemove {

    PlotAdd() {
        super("plot_add", "Add a plot card to a faction");
    }

    @Override
    protected void doAction(Player player, String plotID, SlashCommandInteractionEvent event) {
        FrankenPlotService.addPlot(event, player, plotID);
    }
}
