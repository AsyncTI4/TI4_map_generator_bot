package ti4.commands.bothelper;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

class FixGameChannelPermissions extends GameStateSubcommand {

    public FixGameChannelPermissions() {
        super(Constants.FIX_CHANNEL_PERMISSIONS, "Ensure players in this game have access", false, false);
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        var game = getGame();
        if (guild != null && game != null) {
            Helper.fixGameChannelPermissions(guild, game);
        }
        MessageHelper.sendMessageToEventChannel(event, "Channel Permissions Fixed");
    }
}
