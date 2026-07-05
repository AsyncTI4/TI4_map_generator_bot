package ti4.discord.interactions.buttons.handlers.phases;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.ObjectiveHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StatusHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.StatusCleanupService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.game.EndGameService;
import ti4.service.game.StartPhaseService;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.objectives.RevealPublicObjectiveService;

@UtilityClass
class StatusPhaseButtonHandler {

    @ButtonHandler("reveal_stage_")
    public static void revealPOStage(ButtonInteractionEvent event, String buttonID, Game game) {
        String stage = buttonID.replace("reveal_stage_", "");
        if ("true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            if ("statusScoring".equalsIgnoreCase(game.getPhaseOfGame())) {
                StringBuilder missingPeople = new StringBuilder();
                for (Player player : game.getRealPlayers()) {
                    String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
                    if (so.isEmpty()) {
                        if (!missingPeople.isEmpty()) {
                            missingPeople.append(", ");
                        }
                        missingPeople.append(player.getRepresentation(false, true));
                    }
                }
                if (!missingPeople.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            game.getActionsChannel(),
                            missingPeople
                                    + " need to indicate if they are scoring a secret objective before the next public objective can be flipped.");
                    return;
                }
            }
        }
        if (!game.getStoredValue("revealedPOInRound" + game.getRound()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "The bot thinks that a public objective was already revealed this round. Try doing `/status reveal` if this was a mistake.");
            return;
        } else {
            game.setStoredValue("revealedPOInRound" + game.getRound(), "Yes");
        }
        String revealedObjective = null;
        if (!game.isRedTapeMode() && !game.isCivilizedSocietyMode()) {
            if ("2".equalsIgnoreCase(stage)) {
                RevealPublicObjectiveService.revealS2(game, event);
            } else if (stage.contains("2position_")) {
                int location = Integer.parseInt(stage.replace("2position_", ""));
                game.swapStage2(1, location);
                RevealPublicObjectiveService.revealS2(game, event);
            } else if (stage.contains("1position_")) {
                int location = Integer.parseInt(stage.replace("1position_", ""));
                game.swapStage1(1, location);
                RevealPublicObjectiveService.revealS1(game, event);
            } else if ("2x2".equalsIgnoreCase(stage)) {
                RevealPublicObjectiveService.revealTwoStage2(game, event.getChannel());
            } else if ("none".equalsIgnoreCase(stage)) {
                // continue without revealing anything
            } else {
                revealedObjective = RevealPublicObjectiveService.revealS1(game, event);
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "No objective is revealed at this stage in this mode.");
            int playersWithSCs = 0;
            for (Player player2 : game.getRealPlayers()) {
                if (player2.getSCs() != null
                        && !player2.getSCs().isEmpty()
                        && !player2.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }
            if (playersWithSCs > 0) {
                StatusCleanupService.runStatusCleanup(game);
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), "### " + game.getPing() + " **Status Cleanup Run!**");
            }
        }

        if (!game.isOmegaPhaseMode()) {
            StartPhaseService.startStatusHomework(event, game);
        } else {
            StatusHelper.commitStatusScoringEvent(game);
            if (Constants.IMPERIUM_REX_ID.equalsIgnoreCase(revealedObjective)) {
                EndGameService.secondHalfOfGameEnd(event, game, true, true, false);
            } else {
                var speakerPlayer = game.getSpeaker();
                ObjectiveHelper.secondHalfOfPeakStage1(game, speakerPlayer, 1);
                TextChannel tableTalkChannel = game.getTableTalkChannel();
                if (!game.isFowMode() && tableTalkChannel != null) {
                    MessageHelper.sendMessageToChannel(
                            tableTalkChannel, "## End of Round #" + game.getRound() + " Scoring Info");
                    ListPlayerInfoService.displayerScoringProgression(game, true, tableTalkChannel, "both");
                }
                String message = "When ready, proceed to the Strategy Phase.";
                Button proceedToStrategyPhase = Buttons.green(
                        "proceed_to_strategy",
                        "Proceed to Strategy Phase (will refresh all cards and ping the priority player)");
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The next objective has been revealed to " + MiscEmojis.SpeakerToken
                                + speakerPlayer.getRepresentationNoPing() + ".");
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), message, proceedToStrategyPhase);
            }
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("scoreAnObjective")
    public static void scoreAnObjective(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> poButtons = StatusHelper.getScoreObjectiveButtons(game, player.factionButtonChecker());
        poButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        MessageChannel channel = event.getMessageChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective", poButtons);
    }

    @ButtonHandler(Constants.SO_NO_SCORING)
    public static void soNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        String message =
                player.getRepresentation() + " has opted not to score a secret objective at this point in time.";
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "None");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        String key2 = "queueToScoreSOs";
        String key3 = "potentialScoreSOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScorePOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        ReactionService.addReaction(event, game, player);
    }

    @ButtonHandler(Constants.PO_NO_SCORING)
    public static void poNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
        String message =
                player.getRepresentation() + " has opted not to score a public objective at this point in time.";
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "None");
        String reply = game.isFowMode() ? "No public objective scored" : null;
        ReactionService.addReaction(event, game, player, reply);
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScoreSOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ButtonHandler(value = "get_so_score_buttons", save = false)
    public static void getSoScoreButtons(ButtonInteractionEvent event, Player player) {
        String secretScoreMsg = "Please choose the secret objective you wish to score.";
        List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveButtons(player);
        if (!soButtons.isEmpty()) {
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no secret objectives you can score.");
        }
    }

    @ButtonHandler("preScoreObbie_")
    public static void preScoreObbie(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        if (game.getPhaseOfGame().contains("action")) {
            String poOrSO = buttonID.split("_")[1];
            String num = buttonID.split("_")[2];
            game.setStoredValue(player.getFaction() + "Round" + game.getRound() + "PreScored" + poOrSO, num);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Successfully queued an objective to score (it won't be scored if you later stop meeting the requirements).");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray("reverse" + buttonID, "Unqueue it"));
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You can use this to unqueue it and queue something else.", buttons);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The game is not currently in the action phase, and so no scoring was queued. Go score normally.");
        }
    }

    @ButtonHandler("reversepreScoreObbie_")
    public static void reversepreScoreObbie(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        if (game.getPhaseOfGame().contains("action")) {
            String poOrSO = buttonID.split("_")[1];
            game.setStoredValue(player.getFaction() + "Round" + game.getRound() + "PreScored" + poOrSO, "");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully unqueued an objective.");
            StatusHelper.offerPreScoringButtons(game, player);
        }
    }

    @ButtonHandler(value = "refreshStatusSummary", save = false)
    public static void refreshStatusSummary(ButtonInteractionEvent event, Game game) {
        String msg = "Please score objectives.";
        msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
        event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("turnOffForcedScoring")
    public static void turnOffForcedScoring(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                game.getPing() + ", forced scoring order has been turned off. Any queues will not be resolved.");
        game.setStoredValue("forcedScoringOrder", "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forceACertainScoringOrder")
    public static void forceACertainScoringOrder(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                game.getPing()
                        + ", players will be forced to score in order. Players will not be prevented from declaring they don't score, and are in fact encouraged to do so without delay if that is the case."
                        + " This forced scoring order also does not yet affect secret objectives, it only restrains public objectives.");
        game.setStoredValue("forcedScoringOrder", "true");
        ButtonHelper.deleteMessage(event);
    }
}
