package ti4.discord.interactions.commands.plot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.service.franken.FrankenPlotService;

class PlotRemove extends PlotAddRemove {

    PlotRemove() {
        super("plot_remove", "Remove a plot card from a faction");
    }

    @Override
    protected void doAction(Player player, String plotID, SlashCommandInteractionEvent event) {
        FrankenPlotService.removePlot(event, player, plotID);
    }
}
