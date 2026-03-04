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
            TextChannel deletionLogChannel =
                    JdaService.guildPrimary.getTextChannelsByName("deletion-log", true).stream()
                            .findFirst()
                            .orElse(null);
            if (deletionLogChannel == null) return;

            String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
            if (!GameManager.isValid(gameName)) return;

            Game game = GameManager.getManagedGame(gameName).getGame();
            long messageId = event.getMessageIdLong();
            sendDeletionLog(event, deletionLogChannel, game, messageId);
        } catch (Exception e) {
            BotLogger.error("Error in handleMessageDelete", e);
        }
    }

    private void sendDeletionLog(
            MessageDeleteEvent event, MessageChannel deletionLogChannel, Game game, long messageId) {
        String channelLink = event.getJumpUrl();
        String tableTalkLink = Optional.ofNullable(game.getTableTalkChannel())
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");
        String mainChannelLink = Optional.ofNullable(game.getMainGameChannel())
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");
        String cachedMessage = BotMessageCacheService.getBean().getContent(messageId);

        String logMessage =
                String.format("""
                Bot message deleted.
                Message: %s
                Game: %s
                Deleted from: %s
                Table talk: %s
                Main game: %s""", cachedMessage, game.getName(), channelLink, tableTalkLink, mainChannelLink);

        MessageHelper.sendMessageToChannel(deletionLogChannel, logMessage);
    }
}
