package ti4.service.button;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@UtilityClass
public class ReactionService {

    public static void addReaction(ButtonInteractionEvent event, Game game, Player player, boolean skipReaction, boolean sendPublic, String message, String additionalMessage) {
        if (event == null) return;
        Message mainMessage = event.getInteraction().getMessage();
        Emoji emojiToUse = Helper.getPlayerReactionEmoji(game, player, mainMessage);
        String messageId = mainMessage.getId();

        if (!skipReaction) {
            if (event.getMessageChannel() instanceof ThreadChannel) {
                game.getActionsChannel().addReactionById(event.getChannel().getId(), emojiToUse).queue();
            }
            event.getChannel().addReactionById(messageId, emojiToUse).queue(Consumers.nop(), BotLogger::catchRestError);
            if (game.getStoredValue(messageId) != null) {
                if (!game.getStoredValue(messageId).contains(player.getFaction())) {
                    game.setStoredValue(messageId, game.getStoredValue(messageId) + "_" + player.getFaction());
                }
            } else {
                game.setStoredValue(messageId, player.getFaction());
            }

            UnfiledButtonHandlers.checkForAllReactions(event, game);
            if (isBlank(message)) {
                return;
            }
        }

        String text;
        if (game.isFowMode() && sendPublic) {
            text = message;
        } else if (game.isFowMode()) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        } else if ("Not Following".equalsIgnoreCase(message)) {
            text = player.getRepresentation(false, false) + " " + message;
        } else {
            text = player.getRepresentation() + " " + message;
        }

        if (isNotBlank(additionalMessage)) {
            text += " " + game.getPing() + " " + additionalMessage;
        }

        if (game.isFowMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, game, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    public static void addReaction(ButtonInteractionEvent event, Game game, Player player, boolean skipReaction, boolean sendPublic, String message) {
        addReaction(event, game, player, skipReaction, sendPublic, message, null);
    }

    public static void addReaction(ButtonInteractionEvent event, Game game, Player player, boolean skipReaction, boolean sendPublic) {
        addReaction(event, game, player, skipReaction, sendPublic, "", null);
    }

    public static void addReaction(ButtonInteractionEvent event, Game game, Player player) {
        addReaction(event, game, player, false, false, "", null);
    }

    public static void addReaction(ButtonInteractionEvent event, Game game, Player player, String message) {
        addReaction(event, game, player, false, false, message, null);
    }
}
