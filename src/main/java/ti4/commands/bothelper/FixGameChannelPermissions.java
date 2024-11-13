package ti4.commands.bothelper;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.GameManager;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class FixGameChannelPermissions extends Subcommand {

    public FixGameChannelPermissions() {
        super(Constants.FIX_CHANNEL_PERMISSIONS, "Ensure players in this game have access");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        var game = UserGameContextManager.getContextGame(event.getUser().getId());
        if (guild != null && game != null) {
            Helper.fixGameChannelPermissions(guild, GameManager.getManagedGame(game));
        }
        MessageHelper.sendMessageToEventChannel(event, "Channel Permissions Fixed");
    }
}
