package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class DeleteUserMessages extends Subcommand {

    private static final int HISTORY_BATCH_SIZE = 100;
    private static final int MAX_DELETE_COUNT = 500;
    private static final int MAX_HISTORY_SCAN = 5000;

    DeleteUserMessages() {
        super("delete_user_messages", "Delete the last N messages from a user in a channel.");
        addOptions(
                new OptionData(OptionType.USER, Constants.USER, "User to delete messages for").setRequired(true),
                new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of recent messages to delete")
                        .setRequired(true)
                        .setMinValue(1)
                        .setMaxValue(MAX_DELETE_COUNT),
                new OptionData(
                        OptionType.CHANNEL, Constants.CHANNEL, "Channel to clean (defaults to current channel)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageChannel channel = resolveTargetChannel(event);
        if (channel == null) return;

        User user = event.getOption(Constants.USER).getAsUser();
        int count = event.getOption(Constants.COUNT).getAsInt();

        List<Message> messagesToDelete = findMostRecentMessagesByUser(channel, user.getId(), count);
        if (messagesToDelete.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "No recent messages found for " + user.getAsMention() + " in <#" + channel.getId() + ">.");
            return;
        }

        try {
            channel.purgeMessages(messagesToDelete);
            MessageHelper.sendMessageToChannel(
                    channel,
                    "Deleted " + messagesToDelete.size() + " message(s) from " + user.getAsMention() + " in <#"
                            + channel.getId() + ">.");
        } catch (Exception e) {
            BotLogger.catchRestError(e);
            MessageHelper.sendMessageToChannel(channel, "An error occurred while deleting messages.");
        }
    }

    private static MessageChannel resolveTargetChannel(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        if (channelOption == null) return event.getChannel();
        GuildChannelUnion channel = channelOption.getAsChannel();
        if (channel.getType().isMessage()) return channel.asGuildMessageChannel();
        MessageHelper.sendMessageToEventChannel(event, "The selected channel must support messages.");
        return null;
    }

    private static List<Message> findMostRecentMessagesByUser(MessageChannel channel, String userId, int count) {
        List<Message> messages = new ArrayList<>(count);
        List<Message> batch =
                channel.getHistory().retrievePast(HISTORY_BATCH_SIZE).complete();
        int scanned = 0;

        while (!batch.isEmpty() && messages.size() < count) {
            scanned += batch.size();
            for (Message message : batch) {
                if (userId.equals(message.getAuthor().getId())) {
                    messages.add(message);
                    if (messages.size() >= count) break;
                }
            }
            if (messages.size() >= count || scanned >= MAX_HISTORY_SCAN) break;
            batch = channel.getHistoryBefore(batch.getLast().getId(), HISTORY_BATCH_SIZE)
                    .complete()
                    .getRetrievedHistory();
        }
        return messages;
    }
}
