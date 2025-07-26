package ti4.buttons.handlers.objective;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.game.StartPhaseService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.objectives.ScorePublicObjectiveService;

import java.util.List;
import java.util.Objects;

@UtilityClass
class ObjectiveButtonHandler {

    @ButtonHandler("scoreAnObjective")
    public static void scoreAnObjective(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", choose which objective you wish to score.";
        List<Button> buttons = Helper.getScoreObjectiveButtons(game, player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reveal_stage_")
    public static void revealPOStage(ButtonInteractionEvent event, String buttonID, Game game) {
        String stage = buttonID.replace("reveal_stage_", "");
        if ("1".equals(stage)) {
            RevealPublicObjectiveService.revealS1(event, game);
        } else if ("2".equals(stage)) {
            RevealPublicObjectiveService.revealS2(event, game);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "get_so_discard_buttons", save = false)
    public static void getSODiscardButtons(ButtonInteractionEvent event, Player player) {
        List<Button> buttons = SecretObjectiveHelper.getSecretObjectiveDiscardButtons(player);
        String message = player.getRepresentationUnfogged() + ", choose a secret objective to discard.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    @ButtonHandler(value = "get_so_score_buttons", save = false)
    public static void getSoScoreButtons(ButtonInteractionEvent event, Player player) {
        List<Button> buttons = SecretObjectiveHelper.getSecretObjectiveScoreButtons(player);
        String message = player.getRepresentationUnfogged() + ", choose a secret objective to score.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    public static void soScoreFromHand(
        ButtonInteractionEvent event,
        String buttonID,
        Game game, Player player,
        MessageChannel privateChannel,
        MessageChannel mainGameChannel,
        MessageChannel actionsChannel
    ) {
        String soID = buttonID.replace("scoreFromHand_", "");
        boolean scored = SecretObjectiveHelper.scoreSecretObjective(player, game, soID, event);
        if (scored) {
            String msg = "Secret objective scored successfully!";
            MessageHelper.sendMessageToChannel(privateChannel, msg);
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(mainGameChannel, player.getRepresentation() + " scored a secret objective.");
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(Constants.SO_NO_SCORING)
    public static void soNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation()
            + " has opted not to score a secret objective at this point in time.";
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "None");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        String key2 = "queueToScoreSOs";
        String key3 = "potentialScoreSOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3,
                game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScorePOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
        ReactionService.addReaction(event, game, player);
    }

    @ButtonHandler(Constants.PO_NO_SCORING)
    public static void poNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation()
            + " has opted not to score a public objective at this point in time.";
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "None");
        String reply = game.isFowMode() ? "No public objective scored" : null;
        ReactionService.addReaction(event, game, player, reply);
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
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
            msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
    }

    public static void poScoring(
        ButtonInteractionEvent event, Player player, String buttonID, Game game,
        MessageChannel privateChannel
    ) {
        String poID = buttonID.replace("scorePublic_", "");
        boolean scored = ScorePublicObjectiveService.scorePO(event, player, game, poID);
        if (scored) {
            String msg = "Public objective scored successfully!";
            MessageHelper.sendMessageToChannel(privateChannel, msg);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("non_sc_draw_so")
    public static void nonSCDrawSO(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Drew A Secret Objective";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ". Drew a second secret objective due to **Plausible Deniability**.";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("startOfGameObjReveal")
    public static void startOfGameObjReveal(ButtonInteractionEvent event, Game game, Player player) {
        for (Player p : game.getRealPlayers()) {
            if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Please ensure everyone has discarded secret objectives before hitting this button. ");
                return;
            }
        }

        Player speaker = null;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        }
        if (speaker == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Please assign speaker before hitting this button.");
            ButtonHelper.offerSpeakerButtons(game, player);
            return;
        }
        if (game.hasAnyPriorityTrackMode() && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            PriorityTrackHelper.CreateDefaultPriorityTrack(game);
            if (PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Failed to fill the Priority Track with the default seating order. Use `/omegaphase assign_player_priority` to fill the track before proceeding.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up the Priority Track in the default seating order.");
            PriorityTrackHelper.PrintPriorityTrack(game);
        }
        if (!game.getStoredValue("revealedFlop" + game.getRound()).isEmpty()) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "The bot thinks that public objectives were already revealed. Try doing `/status reveal` if this was a mistake.");
            return;
        } else {
            game.setStoredValue("revealedFlop" + game.getRound(), "Yes");
        }
        RevealPublicObjectiveService.revealTwoStage1(game);
        StartPhaseService.startStrategyPhase(event, game);
        PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(game, null);
        ButtonHelper.deleteMessage(event);
    }
}