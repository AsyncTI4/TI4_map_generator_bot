package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

public class FixGameChannelPermissions extends BothelperSubcommandData {
    public FixGameChannelPermissions() {
        super(Constants.FIX_CHANNEL_PERMISSIONS, "Ensure players in this game have access");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Helper.fixGameChannelPermissions(event.getGuild(), getActiveGame());
        sendMessage("Channel Permissions Fixed");
    }
}
