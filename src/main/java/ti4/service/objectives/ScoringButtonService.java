package ti4.service.objectives;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StatusHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionCheckService;
import ti4.service.button.ReactionService;

@UtilityClass
public class ScoringButtonService {

    public static void poScoring(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel privateChannel) {
        if (!"true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (!game.getStoredValue("newStatusScoringMode").isEmpty() && event != null) {
                    String msg = "Please score objectives.";
                    msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
                    event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
                }
            } catch (Exception e) {
                if (event != null) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel()
                            .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                } else {
                    BotLogger.error("Hm", e);
                }
            }
            return;
        }
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key3b = "potentialScoreSOBlockers";
        String message;
        for (Player player2 : StatusHelper.getPlayersInScoringOrder(game)) {
            if (player2 == player) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }

                String poID = buttonID.replace(Constants.PO_SCORING, "");
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                    if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                        Helper.resolvePOScoringQueue(game, event);
                    }
                }
                break;
            }
            if (game.getStoredValue(key3).contains(player2.getFaction() + "*")
                    || game.getStoredValue(key3b).contains(player2.getFaction() + "*")) {
                message = " has been queued to score a public objective. ";
                if (!game.isFowMode()) {
                    message += player2.getRepresentationUnfogged() + " is the one the game is currently waiting on.";
                }
                String poID = buttonID.replace(Constants.PO_SCORING, "");
                try {
                    int poIndex = Integer.parseInt(poID);
                    if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "Queued");
                    }
                    game.setStoredValue(player.getFaction() + "queuedPOScore", "" + poIndex);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel()
                            .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                }
                game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                ReactionService.addReaction(event, game, player, message);
                break;
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()
                && !"action".equalsIgnoreCase(game.getPhaseOfGame())
                && event != null
                && !game.isFowMode()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        if ("action".equalsIgnoreCase(game.getPhaseOfGame()) && event != null) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void soScoreFromHand(
            ButtonInteractionEvent event,
            String buttonID,
            Game game,
            Player player,
            MessageChannel privateChannel,
            MessageChannel mainGameChannel,
            MessageChannel actionsChannel) {
        String soID = buttonID.replace(Constants.SO_SCORE_FROM_HAND, "");
        MessageChannel channel;
        if (game.isFowMode()) {
            channel = privateChannel;
        } else if (game.isCommunityMode() && game.getMainGameChannel() != null) {
            channel = mainGameChannel;
        } else {
            channel = actionsChannel;
        }
        if (channel != null) {
            int soIndex2 = Integer.parseInt(soID);
            // String phase = "action";
            if (player.getSecret(soIndex2) != null
                    && "status".equalsIgnoreCase(player.getSecret(soIndex2).getPhase())
                    && "true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScoreSOs";
                String key3 = "potentialScoreSOBlockers";
                String key3b = "potentialScorePOBlockers";
                String message;
                for (Player player2 : StatusHelper.getPlayersInScoringOrder(game)) {
                    if (player2 == player) {
                        int soIndex = Integer.parseInt(soID);
                        SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                        }
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                            }
                        }

                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")
                            || game.getStoredValue(key3b).contains(player2.getFaction() + "*")) {
                        message = player.getRepresentation() + " has been queued to score a secret objective. ";
                        if (!game.isFowMode()) {
                            message += player2.getRepresentationUnfogged()
                                    + " is the one the game is currently waiting on.";
                        }
                        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                            game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "Queued");
                        }
                        MessageHelper.sendMessageToChannel(channel, message);
                        int soIndex = Integer.parseInt(soID);
                        game.setStoredValue(player.getFaction() + "queuedSOScore", "" + soIndex);
                        game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                        break;
                    }
                }
            } else {
                try {
                    int soIndex = Integer.parseInt(soID);
                    SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse SO ID: " + soID, e);
                    event.getChannel()
                            .sendMessage("Could not parse secret objective ID: " + soID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                    return;
                }
            }
        } else {
            if (event != null) {
                event.getChannel()
                        .sendMessage("Could not find channel to play card. Please ping Bothelper.")
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
        ButtonHelper.deleteMessage(event);
        ReactionCheckService.checkForAllReactions(event, game);
    }
}
