package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.ExecutorServiceManager;
import ti4.jda.UserHelper;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class DeletionListener extends ListenerAdapter {

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        if (!validateEvent(event)) return;
        ExecutorServiceManager.runAsync("DeletionListener task", () -> handleMessageDelete(event));
    }

    private static boolean validateEvent(MessageDeleteEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            return false;
        }
        String eventGuild = event.getGuild().getId();
        return AsyncTI4DiscordBot.isValidGuild(eventGuild);
    }

    private void handleMessageDelete(MessageDeleteEvent event) {
        try {
            TextChannel deletionLogChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("deletion-log", true).stream()
                .findFirst().orElse(null);
            if (deletionLogChannel == null) return;
            long messageId = event.getMessageIdLong();
            long channelId = event.getChannel().getIdLong();
            Guild guild = event.getGuild();

            guild.retrieveAuditLogs().type(ActionType.MESSAGE_DELETE).limit(10)
                .queueAfter(10, TimeUnit.SECONDS, auditLogs -> {
                    AuditLogEntry relevantLog = auditLogs.stream()
                        .filter(log -> log.getTargetIdLong() == messageId)
                        .findFirst()
                        .orElse(null);

                    if (relevantLog != null) {
                        String deleter = relevantLog.getUser().getName();
                        String deletedMessageAuthor = UserHelper.getUser(relevantLog.getChanges().get(0).getOldValue()).getName();
                        MessageHelper.sendMessageToChannel(deletionLogChannel, "Message " + messageId + " deleted in channel " + channelId + " by " + deleter + " message was authored by " + deletedMessageAuthor + "\nHere: " + event.getJumpUrl());
                    }
                });
        } catch (Exception e) {
            BotLogger.error("Error in handMessageDelete", e);
        }
    }

}
