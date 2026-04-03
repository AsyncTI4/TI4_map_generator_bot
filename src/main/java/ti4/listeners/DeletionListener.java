package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.executors.ExecutorServiceManager;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;
import ti4.spring.service.messagecache.SavedBotMessagesService;

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

    public static void handleContextMenuDelete(MessageContextInteractionEvent event) {
        MessageChannel channel = event.getMessageChannel();
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        String msg = event.getTarget().getContentDisplay();

        handleMessageDelete(channel, gameName, event.getUser(), msg);
    }

    private static void handleMessageDelete(MessageDeleteEvent event) {
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
        // prevent @'ing anyone.
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
