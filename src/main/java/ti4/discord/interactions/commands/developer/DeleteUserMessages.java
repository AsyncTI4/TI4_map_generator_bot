package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.entities.messages.MessageSearchResponse;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.MessageSearchAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class DeleteUserMessages extends Subcommand {

    private static final int MAX_DELETE_COUNT = 500;
    private static final int MINIMUM_MESSAGE_AGE_SECONDS = 30;

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
        Guild guild = event.getGuild();
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "This command can only be used in a server.");
            return;
        }
        MessageChannel channel = resolveTargetChannel(event);
        if (channel == null) return;

        User user = event.getOption(Constants.USER).getAsUser();
        int count = event.getOption(Constants.COUNT).getAsInt();
        // Only messages older than the minimum age are eligible; this also keeps the search
        // inside the window the eventually-consistent search index has already caught up on.
        long newestDeletionId =
                TimeUtil.getDiscordTimestamp(System.currentTimeMillis() - MINIMUM_MESSAGE_AGE_SECONDS * 1000L);

        List<Message> messagesToDelete;
        try {
            messagesToDelete = findMostRecentMessagesByUser(guild, channel, user, count, newestDeletionId);
        } catch (Exception e) {
            BotLogger.catchRestError(e);
            MessageHelper.sendMessageToEventChannel(event, "An error occurred while searching for messages.");
            return;
        }
        if (messagesToDelete == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "The server's message search index is still being built. Try again in a few minutes.");
            return;
        }
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

    private static List<Message> findMostRecentMessagesByUser(
            Guild guild, MessageChannel channel, User user, int count, long newestDeletionId) {
        List<Message> messages = new ArrayList<>(count);
        for (int offset = 0;
                messages.size() < count && offset <= MessageSearchAction.MAX_OFFSET;
                offset += MessageSearchAction.MAX_LIMIT) {
            MessageSearchResponse response = guild.searchMessages()
                    .channels(channel.getIdLong())
                    .authors(user)
                    .maxId(newestDeletionId)
                    .sortBy(MessageSearchAction.SortType.TIMESTAMP)
                    .sortOrder(MessageSearchAction.SortOrder.DESC)
                    .offset(offset)
                    .limit(MessageSearchAction.MAX_LIMIT)
                    .complete();
            if (response.isNotReady()) return null;

            List<Message> page = response.asResults().getMessages();
            for (Message message : page) {
                messages.add(message);
                if (messages.size() >= count) break;
            }
            if (page.size() < MessageSearchAction.MAX_LIMIT) break;
        }
        return messages;
    }
}
