package ti4.discord.interactions.commands.plot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

abstract class PlotAddRemove extends GameStateSubcommand {

    PlotAddRemove(String name, String description) {
        super(name, description, true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLOT_CARDS, "Plot card ID")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String plotID = event.getOption(Constants.PLOT_CARDS, null, OptionMapping::getAsString);
        if (plotID == null || Mapper.getPlot(plotID) == null) {
            MessageHelper.replyToMessage(event, "Could not find plot card with ID: `" + plotID + "`");
            return;
        }
        doAction(player, plotID, event);
    }

    protected abstract void doAction(Player player, String plotID, SlashCommandInteractionEvent event);
}
