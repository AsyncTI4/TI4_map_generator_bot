package ti4.commands2.bothelper;

import java.util.Objects;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands2.Subcommand;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

class ListButtons extends Subcommand {

    public ListButtons() {
        super("list_buttons", "list button IDs on a message");
        addOption(OptionType.STRING, "message_id", "Message ID of the message of which to list buttons", true);
        addOption(OptionType.STRING, "channel_id", "Channel where the message is located", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String messageId = event.getOption("message_id", null, OptionMapping::getAsString);
        String channelId = event.getOption("channel_id", event.getChannel().getId(), OptionMapping::getAsString);
        if (messageId == null || channelId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find channel id");
            return;
        }

        Guild guild = event.getGuild();
        TextChannel channel = guild.getTextChannelById(channelId);
        ThreadChannel threadChannel = guild.getThreadChannelById(channelId);
        if (channel == null && threadChannel == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find channel/thread");
            return;
        }

        Message msg = Objects.requireNonNullElse(channel, threadChannel).getHistoryAround(messageId, 1).complete().getMessageById(messageId);

        if (msg == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find message");
            return;
        }

        msg.getButtons();
        StringBuilder sb = new StringBuilder("Button details:\n>>> ");
        for (Button b : msg.getButtons()) {
            sb.append(ButtonHelper.getButtonRepresentation(b)).append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}