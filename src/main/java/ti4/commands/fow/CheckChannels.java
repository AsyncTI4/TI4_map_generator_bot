package ti4.commands.fow;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class CheckChannels extends FOWSubcommandData {

    public CheckChannels() {
        super(Constants.CHECK_CHANNELS, "Ping each channel that is set up");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        if (FoWHelper.isPrivateGame(event)) {
            MessageHelper.replyToMessage(event, "This command is not available in fog of war private channels.");
            return;
        }

        StringBuilder output = new StringBuilder();
        output.append("**__Currently set channels:__**\n>>> ");
        boolean first = true;
        for (Player player : game.getPlayers().values()) {
            if (!first) output.append("\n");
            first = false;

            output.append(player.getUserName()).append(" - ");
            MessageChannel channel = player.getPrivateChannel();
            if (channel == null) {
                output.append("No private channel");
            } else {
                output.append(channel.getAsMention());
            }
            output.append(" - ");

            Role roleForCommunity = player.getRoleForCommunity();
            if (roleForCommunity == null) {
                output.append("No community role");
            } else {
                output.append(roleForCommunity.getAsMention());
            }
        }

        MessageHelper.replyToMessage(event, output.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
    }
}
