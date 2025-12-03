package ti4.listeners;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.listeners.BotMessageCache;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;

public class DeletionListener extends ListenerAdapter {

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        if (!validateEvent(event)) {
            return;
        }

        handleMessageDelete(event);
    }

    private static boolean validateEvent(MessageDeleteEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) {
            return false;
        }
        String eventGuild = event.getGuild().getId();
        return JdaService.isValidGuild(eventGuild);
    }

    private void handleMessageDelete(MessageDeleteEvent event) {
        try {
            String cachedMessage = BotMessageCache.getContent(event.getMessageIdLong());
            if (cachedMessage == null) {
                return;
            }

            TextChannel deletionLogChannel =
                    JdaService.guildPrimary.getTextChannelsByName("bot-message-deletion-log", true).stream()
                            .findFirst()
                            .orElse(null);
            if (deletionLogChannel == null) {
                return;
            }

            Guild guild = event.getGuild();
            long botId = event.getJDA().getSelfUser().getIdLong();

            guild.retrieveAuditLogs()
                    .type(ActionType.MESSAGE_DELETE)
                    .limit(5)
                    .queueAfter(
                            2,
                            TimeUnit.SECONDS,
                            auditLogs -> {
                                AuditLogEntry relevantLog = auditLogs.stream()
                                        .filter(log -> log.getTargetIdLong() == botId)
                                        .findFirst()
                                        .orElse(null);

                                User deleter = Optional.ofNullable(relevantLog)
                                        .map(AuditLogEntry::getUser)
                                        .orElse(null);
                                String deleterName = deleter == null ? "Unknown" : deleter.getName();

                                sendDeletionLog(event, deletionLogChannel, cachedMessage, deleterName);
                            },
                            error -> BotLogger.error("Failed to retrieve audit logs for message deletion", error));
        } catch (Exception e) {
            BotLogger.error("Error in handMessageDelete", e);
        }
    }

    private void sendDeletionLog(
            MessageDeleteEvent event, TextChannel deletionLogChannel, String cachedMessage, String deleterName) {
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        ManagedGame managedGame = GameManager.isValid(gameName) ? GameManager.getManagedGame(gameName) : null;
        Game game = managedGame == null ? null : managedGame.getGame();

        String channelLink = String.format(
                "https://discord.com/channels/%s/%s", event.getGuild().getId(), event.getChannel().getId());
        String tableTalkLink = Optional.ofNullable(game)
                .map(Game::getTableTalkChannel)
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");
        String mainChannelLink = Optional.ofNullable(game)
                .map(Game::getMainGameChannel)
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");

        String logMessage = String.format(
                "Bot message deleted.\n" +
                        "Message: %s\n" +
                        "Deleted by: %s\n" +
                        "Game: %s\n" +
                        "Deleted from: %s\n" +
                        "Table talk: %s\n" +
                        "Main game: %s",
                cachedMessage,
                deleterName,
                gameName,
                channelLink,
                tableTalkLink,
                mainChannelLink);

        MessageHelper.sendMessageToChannel(deletionLogChannel, logMessage);
    }
}
