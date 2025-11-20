package ti4.commands.fow;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class CheckChannels extends GameStateSubcommand {

    public CheckChannels() {
        super(Constants.CHECK_CHANNELS, "Check fow channel setup", false, false);
    }

    public void execute(SlashCommandInteractionEvent event) {
        if (FoWHelper.isPrivateGame(event)) {
            MessageHelper.replyToMessage(event, "This command is not available in fog of war private channels.");
            return;
        }

        List<Button> createChannelButtons = new ArrayList<>();
        StringBuilder output = new StringBuilder();
        output.append("**__Currently set channels:__**\n>>> ");
        boolean first = true;
        for (Player player : getGame().getPlayers().values()) {
            boolean isNeutral = "neutral".equals(player.getFaction());
            if (!first) output.append("\n");
            first = false;

            output.append(player.getUserName());
            output.append(" ");
            output.append(player.getFactionEmojiOrColor());
            output.append(" - ");
            MessageChannel channel = player.getPrivateChannel();
            if (channel == null) {
                output.append("No private channel");
            } else {
                output.append(channel.getAsMention());
                if (!((TextChannel) channel).getMembers().contains(player.getMember())) {
                    output.append(" - No access");
                }
            }
            output.append(" - ");

            if (isNeutral) {
                output.append("Is Neutral");
            } else {
                Role roleForCommunity = player.getRoleForCommunity();
                if (roleForCommunity == null) {
                    output.append("No community role");
                } else {
                    output.append(roleForCommunity.getAsMention());
                }
            }

            if (player.getPrivateChannel() == null && !isNeutral && player.getMember() != null) {
                createChannelButtons.add(Buttons.green(
                        "fowCreateChannelFor_" + player.getUserID(), "Create Channel for " + player.getUserName()));
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), output.toString(), createChannelButtons);
    }
}
