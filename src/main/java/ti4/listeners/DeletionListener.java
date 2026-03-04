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
import ti4.map.persistence.ManagedGame;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;

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
            String cachedMessage = BotMessageCache.getContent(event.getMessageIdLong());
            if (cachedMessage == null) return;

            TextChannel deletionLogChannel =
                    JdaService.guildPrimary.getTextChannelsByName("bot-message-deletion-log", true).stream()
                            .findFirst()
                            .orElse(null);
            if (deletionLogChannel == null) return;

            long messageId = event.getMessageIdLong();
            String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
            if (GameManager.isValid(gameName)) {
                Game game = GameManager.getManagedGame(gameName).getGame();
                GameMessageManager.getAll(gameName, GameMessageType.COMMAND_EVIDENCE).stream()
                        .filter(gameMessage -> gameMessage.messageId().equals(String.valueOf(messageId)))
                        .findFirst()
                        .ifPresent(gameMessage -> {
                            GameMessageManager.remove(gameName, gameMessage.messageId());
                            MessageHelper.sendMessageToChannel(
                                    deletionLogChannel,
                                    "A command string message was deleted in game " + gameName + "."
                                            + game.getMainGameChannel().getJumpUrl()
                                            + ". Check audit logs for the culprit.");
                            MessageHelper.sendMessageToChannel(
                                    game.getActionsChannel(),
                                    "A command string message was deleted. If someone confesses to doing this intentionally, nothing further needs to be done. The admins have been alerted.");
                        });
            }
        } catch (Exception e) {
            BotLogger.error("Error in handMessageDelete", e);
        }
    }

    private void sendDeletionLog(
            MessageDeleteEvent event, MessageChannel deletionLogChannel, String cachedMessage, String deleterName) {
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        ManagedGame managedGame = GameManager.isValid(gameName) ? GameManager.getManagedGame(gameName) : null;

        String channelLink = event.getJumpUrl();
        String tableTalkLink = Optional.ofNullable(managedGame)
                .map(ManagedGame::getTableTalkChannel)
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");
        String mainChannelLink = Optional.ofNullable(managedGame)
                .map(ManagedGame::getMainGameChannel)
                .map(TextChannel::getJumpUrl)
                .orElse("Unavailable");

        String logMessage = String.format(
                """
                Bot message deleted.
                Message: %s
                Deleted by: %s
                Game: %s
                Deleted from: %s
                Table talk: %s
                Main game: %s""",
                cachedMessage, deleterName, gameName, channelLink, tableTalkLink, mainChannelLink);

        MessageHelper.sendMessageToChannel(deletionLogChannel, logMessage);
    }
}
