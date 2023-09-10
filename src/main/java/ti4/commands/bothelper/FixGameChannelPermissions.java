package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

public class FixGameChannelPermissions extends BothelperSubcommandData {
    public FixGameChannelPermissions(){
        super(Constants.FIX_CHANNEL_PERMISSIONS, "Fixes the permissions for a channel. Either gives players the role or if no role, direct access.");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Helper.fixGameChannelPermissions(event.getGuild(), getActiveGame());
        sendMessage("Channel Permissions Fixed");
    }
}
