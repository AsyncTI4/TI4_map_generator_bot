package ti4.service.fow;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

@UtilityClass
public class WhisperService {

    private static final Pattern FUTURE = Pattern.compile("future");
    private static final Pattern PATTERN = Pattern.compile("[^a-zA-Z0-9]+$");

    public static void sendWhisper(
            Game game,
            Player player,
            Player player_,
            String msg,
            String anonY,
            MessageChannel feedbackChannel,
            Guild guild) {
        if (game.isWhispersDisabled()) {
            MessageHelper.sendMessageToChannel(
                    feedbackChannel,
                    "Whispers are disabled in this game. To reenable them, use `/game setup whispers_enabled:true`.");
            return;
        }

        String message;
        String realIdentity = player_.getRepresentationUnfogged();
        String player1 = ColorEmojis.getColorEmojiWithName(player.getColor());
        if (!game.isFowMode() && !(feedbackChannel instanceof ThreadChannel)) {
            feedbackChannel = player.getCardsInfoThread();
            MessageHelper.sendMessageToChannel(
                    feedbackChannel,
                    player.getRepresentation()
                            + " Reminder you should start all whispers from your `#cards-info` thread, and do not need to use the `/fow whisper` command,"
                            + " you can just start a message with `toblue message...` or something.");
        }
        if (!game.isFowMode()) {
            player1 = player.getFactionEmoji() + "(" + StringUtils.capitalize(player.getFaction()) + ") " + player1;
        }
        for (Player player2 : game.getRealPlayers()) {
            if ("".equalsIgnoreCase(player2.getPing())) {
                continue;
            }
            msg = msg.replace(player2.getPing(), player2.getUserName());
        }

        if (anonY.compareToIgnoreCase("y") == 0) {
            message = "Attention " + realIdentity + "! [REDACTED] says: " + msg;
        } else {
            message = "Attention " + realIdentity + "! " + player1 + " says: " + msg;
        }
        if (game.isFowMode()) {
            String fail = "Could not notify receiving player.";
            String success;
            String player2 = ColorEmojis.getColorEmojiWithName(player_.getColor());
            if (message.contains("[REDACTED]")) {
                success = player1 + "(You) anonymously said: \"" + msg + "\" to " + player2;
            } else {
                success = player1 + "(You) said: \"" + msg + "\" to " + player2;
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, game, feedbackChannel, message, fail, success);
            if (!player.getNeighbouringPlayers(true).contains(player_)) {
                MessageHelper.sendMessageToChannel(
                        feedbackChannel, "In FoW, communicate only to your neighbours, which " + player2 + " isn't.");
            }
        } else {
            String fail = "Could not notify receiving player.";
            String success;
            String player2 = ColorEmojis.getColorEmojiWithName(player_.getColor());
            player2 = player_.getFactionEmoji() + "(" + StringUtils.capitalize(player_.getFaction()) + ") " + player2;
            if (message.contains("[REDACTED]")) {
                success = player1 + "(You) anonymously said: \"" + msg + "\" to " + player2;
            } else {
                success = player1 + "(You) said: \"" + msg + "\" to " + player2;
            }
            String key = player.getFaction() + "whisperHistoryTo" + player_.getFaction();
            String whisperHistory = game.getStoredValue(key);
            if (!"pbd1000".equalsIgnoreCase(game.getName())) {
                if (whisperHistory.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getFactionEmoji() + " is whispering for the first time this turn to "
                                    + player_.getFactionEmoji());
                    game.setStoredValue(key, "1");
                } else {
                    int num = Integer.parseInt(whisperHistory);
                    num += 1;
                    game.setStoredValue(key, "" + num);
                    if ((num == 5 || num == 10)) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getFactionEmoji() + " is sending whisper #" + num + " of this turn to "
                                        + player_.getFactionEmoji());
                    }
                }
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, game, feedbackChannel, message, fail, success);
        }
    }

    public static boolean handleWhispers(MessageReceivedEvent event, Message message, String gameName) {
        if (message.getContentRaw().contains("used /fow whisper")) {
            message.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }

        String messageText = message.getContentRaw();
        if (!messageText.toLowerCase().startsWith("to") || !messageText.contains(" ")) {
            return false;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        if (game == null) {
            return true;
        }

        if (game.isFowMode()
                && event.getChannel() instanceof ThreadChannel
                && event.getChannel().getName().contains("vs")
                && event.getChannel().getName().contains("private")) {
            return false;
        }

        Player sender = getPlayer(event, game);
        if (sender == null || !sender.isRealPlayer()) {
            return true;
        }

        String messageLowerCase = messageText.toLowerCase();
        String receivingColorOrFaction =
                PATTERN.matcher(StringUtils.substringBetween(messageLowerCase, "to", " "))
                        .replaceAll("");

        if ("futureme".equals(receivingColorOrFaction)) {
            String messageContent = StringUtils.substringAfter(messageText, " ");
            whisperToFutureMe(game, sender, messageContent, event.getChannel());
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            GameManager.save(game, "Whisper to future by " + sender.getUserName());
            return true;
        }

        boolean future = receivingColorOrFaction.startsWith("future");
        receivingColorOrFaction = FUTURE.matcher(receivingColorOrFaction).replaceFirst("");
        if (receivingColorOrFaction.isEmpty()) {
            return true;
        }

        Player receiver = getPlayerByColorOrFaction(game, receivingColorOrFaction);
        if (receiver == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found: " + receivingColorOrFaction);
            return true;
        }

        String messageContent = StringUtils.substringAfter(messageText, " ");
        if (messageContent.isEmpty()) {
            message.reply("No message content?").queue(Consumers.nop(), BotLogger::catchRestError);
            return true;
        }

        if (game.isWhispersDisabled()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Whispers are disabled in this game. To reenable them, use `/game setup whispers_enabled:true`.");
            message.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return true;
        }

        if (future) {
            whisperToFutureColorOrFaction(game, messageContent, sender, receiver, event.getChannel());
        } else {
            sendWhisper(game, sender, receiver, messageContent, "n", event.getChannel(), event.getGuild());
            message.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        GameManager.save(game, "Whisper");
        return true;
    }

    public static Player getPlayerByColorOrFaction(Game game, String receivingColorOrFaction) {
        String resolvedColorOrFaction = AliasHandler.resolveFaction(receivingColorOrFaction);
        if (!Mapper.isValidColor(resolvedColorOrFaction) && !Mapper.isValidFaction(resolvedColorOrFaction)) {
            return null;
        }
        for (Player player : game.getRealPlayers()) {
            if (Objects.equals(resolvedColorOrFaction, player.getFaction())
                    || Objects.equals(resolvedColorOrFaction, player.getColor())) {
                return player;
            }
        }
        return null;
    }

    public static void whisperToFutureColorOrFaction(
            Game game, String messageContent, Player sender, Player receiver, MessageChannel messageChannel) {
        String futureMsgKey = "futureMessageFor_" + receiver.getFaction() + "_" + sender.getFaction();
        game.setStoredValue(futureMsgKey, game.getStoredValue(futureMsgKey) + "\n\n" + messageContent);
        MessageHelper.sendMessageToChannel(messageChannel, sender.getFactionEmoji() + " sent someone else a future message");
        MessageHelper.sendMessageToPlayerCardsInfoThread(
                sender, "You sent a future message to " + receiver.getRepresentationNoPing() + ":\n>>> " + messageContent);
    }

    public static void whisperToFutureMe(Game game, Player player, String messageContent, MessageChannel messageChannel) {
        String previousThoughts = "";
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            previousThoughts = game.getStoredValue("futureMessageFor" + player.getFaction()) + "\n\n";
        }
        game.setStoredValue("futureMessageFor" + player.getFaction(), previousThoughts + messageContent);
        MessageHelper.sendMessageToChannel(messageChannel, player.getFactionEmoji() + " sent themselves a future message");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, "You sent yourself a future message:\n>>> " + messageContent);
    }

    private static Player getPlayer(MessageReceivedEvent event, Game game) {
        Player player = game.getPlayer(event.getAuthor().getId());
        if (!game.isCommunityMode()) {
            return player;
        }
        List<Role> roles = event.getMember().getRoles();
        for (Player player2 : game.getRealPlayers()) {
            if (roles.contains(player2.getRoleForCommunity())) {
                return player2;
            }
            if (player2.getTeamMateIDs().contains(event.getMember().getUser().getId())) {
                return player2;
            }
        }
        return player;
    }
}
