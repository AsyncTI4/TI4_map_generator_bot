package ti4.service.message;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.GameNameService;
import ti4.spring.service.messagecache.SavedBotMessagesService;

@UtilityClass
public class MessageDeletionService {

    public static void handleContextMenuDelete(MessageContextInteractionEvent event) {
        MessageChannel channel = event.getMessageChannel();
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        String msg = event.getTarget().getContentDisplay();

        handleMessageDelete(channel, gameName, event.getUser(), msg);
    }

    public static void handleMessageDelete(MessageDeleteEvent event) {
        MessageChannel channel = event.getChannel();
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        try {
            long messageId = event.getMessageIdLong();
            String cachedMessage = SavedBotMessagesService.getBean().getContent(messageId);
            if (cachedMessage == null) return;

            handleMessageDelete(channel, gameName, null, cachedMessage);
        } catch (Exception e) {
            BotLogger.error("Error in handleMessageDelete[event]", e);
        }
    }

    private static void handleMessageDelete(
            MessageChannel eventChannel, String gameName, User deleter, String messageText) {
        try {
            if (!GameManager.isValid(gameName) || eventChannel == null || messageText == null) return;

            TextChannel deletionLogChannel =
                    JdaService.guildPrimary.getTextChannelsByName("deletion-log", true).stream()
                            .findFirst()
                            .orElse(null);
            if (deletionLogChannel == null) return;

            Game game = GameManager.getManagedGame(gameName).getGame();
            sendDeletionLog((GuildMessageChannel) eventChannel, deletionLogChannel, game, deleter, messageText);
        } catch (Exception e) {
            BotLogger.error("Error in handleMessageDelete[generic]", e);
        }
    }

    private static void sendDeletionLog(
            GuildMessageChannel eventChannel, MessageChannel deletionLogChannel, Game game, User user, String msg) {
        if (user != null && user.isBot()) return;

        String channelLink = eventChannel.getJumpUrl();
        String ttLink = game.getTabletalkJumpLinkFormatted();
        String actionsLink = game.getActionsJumpLinkFormatted();
        String sanitizedMsg = msg.replace("@", "@\u200B");

        String deleterName = user == null ? "unknown" : user.getEffectiveName();
        String logMessage =
                String.format("""
                Game: **%s**, Deleted by: %s
                From: %s %s %s
                %s
                """, game.getName(), deleterName, channelLink, ttLink, actionsLink, sanitizedMsg);

        MessageHelper.sendMessageToChannel(deletionLogChannel, logMessage);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                "**A command string message was deleted."
                        + " If someone confesses to doing this intentionally, nothing further needs to be done."
                        + " The admins have been alerted.**\n__**:warning: Do not delete this message. :warning:**__");
    }
}
