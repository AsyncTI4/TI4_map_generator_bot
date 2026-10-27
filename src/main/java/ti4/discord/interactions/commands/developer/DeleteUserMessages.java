package ti4.discord.interactions.commands.developer;

import java.time.OffsetDateTime;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.MessageSearchService;

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
        GuildMessageChannel channel = resolveTargetChannel(event);
        if (channel == null) return;

        User user = event.getOption(Constants.USER).getAsUser();
        int count = event.getOption(Constants.COUNT).getAsInt();
        OffsetDateTime newestDeletionTime = OffsetDateTime.now().minusSeconds(MINIMUM_MESSAGE_AGE_SECONDS);

        MessageSearchService.findMessagesByAuthor(channel, user, count)
                .thenAccept(messages -> deleteMessages(
                        event,
                        channel,
                        user,
                        messages.stream()
                                .filter(message -> !message.getTimeCreated().isAfter(newestDeletionTime))
                                .toList()))
                .exceptionally(error -> {
                    BotLogger.catchRestError(error);
                    MessageHelper.sendMessageToChannel(channel, "An error occurred while deleting messages.");
                    return null;
                });
    }

    private static void deleteMessages(
            SlashCommandInteractionEvent event, GuildMessageChannel channel, User user, List<Message> messages) {
        if (messages.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "No recent messages found for " + user.getAsMention() + " in <#" + channel.getId() + ">.");
            return;
        }

        channel.purgeMessages(messages);
        MessageHelper.sendMessageToChannel(
                channel,
                "Deleted " + messages.size() + " message(s) from " + user.getAsMention() + " in <#" + channel.getId()
                        + ">.");
    }

    private static GuildMessageChannel resolveTargetChannel(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        if (channelOption == null) {
            if (event.getChannel() instanceof GuildMessageChannel eventChannel) return eventChannel;
        } else {
            GuildChannelUnion channel = channelOption.getAsChannel();
            if (channel.getType().isMessage()) return channel.asGuildMessageChannel();
        }
        MessageHelper.sendMessageToEventChannel(event, "The selected channel must support messages.");
        return null;
    }
}
