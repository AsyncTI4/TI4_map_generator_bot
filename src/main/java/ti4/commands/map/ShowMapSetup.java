package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

class ShowMapSetup extends GameStateSubcommand {

    public ShowMapSetup() {
        super("show_setup_positions", "Show the system positions to aid in manually building the map.", true, false);
        addOption(OptionType.BOOLEAN, "show", "True = ON | False = OFF", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        game.setShowMapSetup(event.getOption("show", false, OptionMapping::getAsBoolean));
        MessageHelper.sendMessageToEventChannel(event, "Setup option set to: " + game.isShowMapSetup());
    }
}
