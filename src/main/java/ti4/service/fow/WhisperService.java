package ti4.service.fow;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import software.amazon.awssdk.utils.StringUtils;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

@UtilityClass
public class WhisperService {

    public static void sendWhisper(Game game, Player player, Player player_, String msg, String anonY, MessageChannel feedbackChannel, Guild guild) {
        String message;
        String realIdentity = player_.getRepresentationUnfogged();
        String player1 = ColorEmojis.getColorEmojiWithName(player.getColor());
        if (!game.isFowMode() && !(feedbackChannel instanceof ThreadChannel)) {
            feedbackChannel = player.getCardsInfoThread();
            MessageHelper.sendMessageToChannel(feedbackChannel, player.getRepresentation()
                + " Reminder you should start all whispers from your `#cards-info` thread, and do not need to use the `/fow whisper` command,"
                + " you can just start a message with `toblue message...` or something.");
        }
        if (!game.isFowMode()) {
            player1 = player.getFactionEmoji() + "(" + StringUtils.capitalize(player.getFaction()) + ") " + player1;
        }
        for (Player player2 : game.getRealPlayers()) {
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
            if (!player.getNeighbouringPlayers().contains(player_)) {
                MessageHelper.sendMessageToChannel(feedbackChannel, "In FoW, communicate only to your neighbours, which " + player2 + " isn't.");
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
            if (!game.getName().equalsIgnoreCase("pbd1000")) {
                if (whisperHistory.isEmpty()) {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " is whispering for the first time this turn to " + player_.getFactionEmoji());
                    game.setStoredValue(key, "1");
                } else {
                    int num = Integer.parseInt(whisperHistory);
                    num = num + 1;
                    game.setStoredValue(key, "" + num);
                    if ((num == 5 || num == 10)) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " is sending whisper #" + num + " of this turn to " + player_.getFactionEmoji());
                    }
                }
            }
            MessageHelper.sendPrivateMessageToPlayer(player_, game, feedbackChannel, message, fail, success);
        }
    }
}
