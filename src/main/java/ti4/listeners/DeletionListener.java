package ti4.listeners;

import java.util.Optional;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.executors.ExecutorServiceManager;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;
import ti4.spring.service.messagecache.BotMessageCacheService;

public class DeletionListener extends ListenerAdapter {

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        if (!validateEvent(event)) return;

        ExecutorServiceManager.runAsync("DeletionListener task", () -> handleMessageDelete(event));
    }

    private static boolean validateEvent(MessageDeleteEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) return false;

        String eventGuild = event.getGuild().getId();
        return JdaService.isValidGuild(eventGuild);
    }

    private void handleMessageDelete(MessageDeleteEvent event) {
        try {
            String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
            if (!GameManager.isValid(gameName)) return;

            TextChannel deletionLogChannel =
                    JdaService.guildPrimary.getTextChannelsByName("deletion-log", true).stream()
                            .findFirst()
                            .orElse(null);
            if (deletionLogChannel == null) return;

            long messageId = event.getMessageIdLong();
            String cachedMessage = BotMessageCacheService.getBean().getContent(messageId);
            if (cachedMessage == null) return;

            Game game = GameManager.getManagedGame(gameName).getGame();
            sendDeletionLog(event, deletionLogChannel, game, cachedMessage);
        } catch (Exception e) {
            BotLogger.error("Error in handleMessageDelete", e);
        }
    }

    private void sendDeletionLog(
            MessageDeleteEvent event, MessageChannel deletionLogChannel, Game game, String cachedMessage) {
        String channelLink = event.getJumpUrl();
        String tableTalkLink = Optional.ofNullable(game.getTableTalkChannel())
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");
        String mainChannelLink = Optional.ofNullable(game.getMainGameChannel())
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");
        // prevent @'ing anyone.
        String sanitizedCachedMessage = cachedMessage.replace("@", "@\u200B");

        String logMessage =
                String.format("""
                **%s**
                From: %s Talk Channel: %s Actions Channel: %s
                %s
                """, game.getName(), channelLink, tableTalkLink, mainChannelLink, sanitizedCachedMessage);

        MessageHelper.sendMessageToChannel(deletionLogChannel, logMessage);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                ":warning:**A command string message was deleted."
                        + " If someone confesses to doing this intentionally, nothing further needs to be done."
                        + " The admins have been alerted. Do not delete this message.**:warning:");
    }
}
