package ti4.buttons.handlers.strategycard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.game.StartPhaseService;
import ti4.service.objectives.RevealPublicObjectiveService;

@UtilityClass
public class StrategyCardMiscButtonHandler {

    @ButtonHandler("toldarPN")
    public static void toldarPN(ButtonInteractionEvent event, Player player) {
        player.setCommodities(player.getCommodities() + 3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " used _Concordat Allegiant_ (the Toldar promissory note)"
                        + " to gain 3 commodities after winning a combat against someone with more victory points than them. They can do this once per action. Their currently hold "
                        + player.getCommodities() + " commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ".");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("assignSpeaker_")
    @ButtonHandler(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)
    public static void sc3AssignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
        faction = faction.replace("assignSpeaker_", "");
        Player newSpeaker = game.getPlayerFromColorOrFaction(faction);
        if (newSpeaker.isSpeaker()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "That player is already speaker.");
            return;
        }
        game.setStoredValue("hasntSetSpeaker", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.getFaction().equals(faction)) {
                game.setSpeakerUserID(player_.getUserID());
                String message = MiscEmojis.SpeakerToken + " Speaker has been assigned to "
                        + player_.getRepresentation(false, true) + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (game.isFowMode() && player != player_) {
                    MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                }
                if (!game.isFowMode() && !game.isTwilightsFallMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                }
            }
        }
        ButtonHelper.deleteMessage(event);

        if (game.isTwilightsFallMode()) {
            String assignSpeakerMessage =
                    player.getRepresentation() + ", please choose a faction below to receive the Tyrant token.";
            List<Button> assignSpeakerActionRow = getTyrannusAssignTyrantButtons(game, player);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), assignSpeakerMessage, assignSpeakerActionRow);
        }
    }

    @ButtonHandler("assignTyrant_")
    public static void assignTyrant(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("assignTyrant_", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.getFaction().equals(faction)) {
                game.setTyrantUserID(player_.getUserID());
                String message = MiscEmojis.BenedictionToken + " Tyrant has been assigned to "
                        + player_.getRepresentation(false, true) + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (game.isFowMode() && player != player_) {
                    MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                }
                if (!game.isFowMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTyrannusAssignTyrantButtons(Game game, Player politicsHolder) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if ((!player.isSpeaker() || !politicsHolder.getSCs().contains(3)) && !player.isTyrant()) {
                String faction = player.getFaction();
                if (Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!game.isFowMode()) {
                        button = Buttons.gray(
                                politicsHolder.getFinsFactionCheckerPrefix() + "assignTyrant_" + faction,
                                " ",
                                player.getFactionEmoji());
                    } else {
                        button = Buttons.gray(
                                politicsHolder.getFinsFactionCheckerPrefix() + "assignTyrant_" + faction,
                                player.getColor(),
                                ColorEmojis.getColorEmoji(player.getColor()));
                    }
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }

    @ButtonHandler("diploSystem")
    public static void diploSystem(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the system you wish to Diplo.";
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
    }

    @ButtonHandler("primaryOfWarfare")
    public static void primaryOfWarfare(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "warfare");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel, "Please choose which system you wish to remove your command token from.", buttons);
    }

    @ButtonHandler("primaryOfTeWarfare")
    public static void primaryOfTeWarfare(Player player) {
        StrategyCardModel model = Mapper.getStrategyCard("te6warfare");
        if (model == null) return;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(
                player.finChecker() + "redistributeCCButtons_deleteThisButton", "Redistribute Command Tokens"));
        buttons.add(Buttons.red(player.finChecker() + "beginTacticalTeWarfare", "Perform Tactical Action"));
        String message =
                "Use the buttons to resolve the primary of **Warfare**. Redistributing your command tokens is optional.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("beginTacticalTeWarfare")
    public static void beginTacticalTeWarfare(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.setWarfareAction(true);
        ButtonHelperTacticalAction.beginTacticalAction(game, player);
    }

    @ButtonHandler("startOfGameObjReveal")
    public static void startOfGameObjReveal(ButtonInteractionEvent event, Game game) {
        for (Player p : game.getRealPlayers()) {
            if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Please ensure everyone has discarded secret objectives before hitting this button. ");
                return;
            }
        }

        Player speaker = null;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        }
        if (speaker == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Please assign speaker before hitting this button.");
            ButtonHelper.offerSpeakerButtons(game);
            return;
        }
        if (game.hasAnyPriorityTrackMode()
                && PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            PriorityTrackHelper.CreateDefaultPriorityTrack(game);
            if (PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Failed to fill the Priority Track with the default seating order. Use `/omegaphase assign_player_priority` to fill the track before proceeding.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Set up the Priority Track in the default seating order.");
            PriorityTrackHelper.PrintPriorityTrack(game);
        }
        if (!game.getStoredValue("revealedFlop" + game.getRound()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "The bot thinks that public objectives were already revealed. Try doing `/status reveal` if this was a mistake.");
            return;
        } else {
            game.setStoredValue("revealedFlop" + game.getRound(), "Yes");
        }

        if (game.isCivilizedSocietyMode()) {
            RevealPublicObjectiveService.revealAllObjectives(game);
        } else {
            RevealPublicObjectiveService.revealTwoStage1(game);
        }

        if (game.isTwilightsFallMode()
                && !game.getStoredValue("needsInauguralSplice").isEmpty()) {
            game.removeStoredValue("needsInauguralSplice");
            ButtonHelperTwilightsFall.startInauguralSplice(game);
        } else {
            startOfGameStrategyPhase(event, game);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOfGameStrategyPhase")
    public static void startOfGameStrategyPhase(ButtonInteractionEvent event, Game game) {
        StartPhaseService.startStrategyPhase(event, game);
        PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(game, null);
        ButtonHelper.deleteMessage(event);
        // Reduce file size by clearing draft info
        game.clearAllDraftInfo();
    }
}
