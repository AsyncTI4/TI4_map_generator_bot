package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class HelpAction extends Subcommand {

    public HelpAction() {
        super(Constants.HELP_DOCUMENTATION, "Show Help Documentation");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Async Help Documentation can be found in [this Google Doc](https://docs.google.com/document/d/1yrVH0lEzYj1MbXNzQIK3thgBWAVIZmErluPHLot-OQg/edit)\n> Also, check out this channel: https://discord.com/channels/943410040369479690/947727176105623642");
    }
}
