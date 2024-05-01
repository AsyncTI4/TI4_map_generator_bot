package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.message.MessageHelper;

public class ShowMapSetup extends MapSubcommandData {

    public ShowMapSetup() {
        super("show_setup_positions", "Show the tile positions to aid in manually building the map.");
        addOption(OptionType.BOOLEAN, "show", "True = ON | False = OFF", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        getActiveGame().setShowMapSetup(event.getOption("show", false, OptionMapping::getAsBoolean));
        MessageHelper.sendMessageToEventChannel(event, "Setup option set to: " + getActiveGame().isShowMapSetup());
    }
}
