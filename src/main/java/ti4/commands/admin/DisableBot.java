package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.settings.GlobalSettings;

class DisableBot extends Subcommand {

    DisableBot() {
        super("disable_bot", "Quickly run /developer settings READY_TO_RECEIVE_COMMANDS = false");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
        MessageHelper.sendMessageToEventChannel(event, "Bot has been told to stop processing events.");
    }
}
