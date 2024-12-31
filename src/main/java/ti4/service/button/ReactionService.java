package ti4.service.button;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@UtilityClass
public class ReactionService {

    private static final Pattern CARDS_PATTERN = Pattern.compile("Card\\s(.*?):");

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
            GameMessageManager.addReaction(game.getName(), player.getFaction(), messageId);

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

    public static void addReaction(Player player, boolean sendPublic, String message, String additionalMessage, String messageID, Game game) {
        try {
            game.getMainGameChannel().retrieveMessageById(messageID).queue(mainMessage -> {
                Emoji emojiToUse = Helper.getPlayerReactionEmoji(game, player, mainMessage);
                String messageId = mainMessage.getId();

                game.getMainGameChannel().addReactionById(messageId, emojiToUse).queue();
                GameMessageManager.addReaction(game.getName(), player.getFaction(), messageId);
                progressGameIfAllPlayersHaveReacted(messageId, game);

                if (message == null || message.isEmpty()) {
                    return;
                }

                String text = player.getRepresentation() + " " + message;
                if (game.isFowMode() && sendPublic) {
                    text = message;
                } else if (game.isFowMode()) {
                    text = "(You) " + emojiToUse.getFormatted() + " " + message;
                }

                if (additionalMessage != null && !additionalMessage.isEmpty()) {
                    text += game.getPing() + " " + additionalMessage;
                }

                if (game.isFowMode() && !sendPublic) {
                    MessageHelper.sendPrivateMessageToPlayer(player, game, text);
                }
            }, BotLogger::catchRestError);
        } catch (Throwable e) {
            game.removeMessageIDForSabo(messageID);
        }
    }

    private static void progressGameIfAllPlayersHaveReacted(String messageId, Game game) {
        GameMessageManager.getOne(game.getName(), messageId).ifPresent(gameMessage -> progressGameIfAllPlayersHaveReacted(gameMessage, game));
    }

    private static void progressGameIfAllPlayersHaveReacted(GameMessageManager.GameMessage gameMessage, Game game) {
        int matchingFactionReactions = 0;
        for (Player player : game.getRealPlayers()) {
            if (gameMessage.factionsThatReacted().contains(player.getFaction())) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = game.getRealPlayers().size();
        if (matchingFactionReactions < numberOfPlayers) {
            return;
        }

        game.getMainGameChannel().retrieveMessageById(gameMessage.messageId()).queue(message -> {
            if (gameMessage.type() == GameMessageType.AGENDA_AFTER) {
                message.reply("All players have indicated 'No Afters'").queueAfter(1000, TimeUnit.MILLISECONDS);
                AgendaHelper.startTheVoting(game);
            } else if (gameMessage.type() == GameMessageType.AGENDA_WHEN) {
                message.reply("All players have indicated 'No Whens'").queueAfter(10, TimeUnit.MILLISECONDS);
            } else {
                Matcher acToReact = CARDS_PATTERN.matcher(message.getContentRaw());
                String msg2 = "All players have indicated 'No Sabotage'" + (acToReact.find() ? " to " + acToReact.group(1) : "");
                if (!game.isFowMode()) {
                    String faction = gameMessage.factionsThatReacted().getFirst();
                    Player playerToPing = game.getPlayerFromColorOrFaction(faction);
                    if (playerToPing != null) {
                        msg2 = playerToPing.getRepresentation() + " " + msg2;
                    }
                }
                message.reply(msg2).queueAfter(1, TimeUnit.SECONDS);
            }
            GameMessageManager.remove(game.getName(), gameMessage);
        });
    }

    public static boolean checkForSpecificPlayerReact(Player player, GameMessageManager.GameMessage gameMessage) {
        return gameMessage.factionsThatReacted().contains(player.getFaction());
    }

    public static boolean checkForSpecificPlayerReact(String messageId, Player player, Game game) {
        return  GameMessageManager.getOne(game.getName(), messageId)
            .filter(message -> checkForSpecificPlayerReact(player, message))
            .isPresent();
    }
}
