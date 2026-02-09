package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.tech.ListTechService;
import ti4.service.turn.EndTurnService;
import ti4.service.turn.StartTurnService;

@UtilityClass
public class VisionariaSelectService {

    private String visionariaRep() {
        return Mapper.getBreakthrough("deepwroughtbt").getNameRepresentation();
    }

    private String visionariaName() {
        return Mapper.getBreakthrough("deepwroughtbt").getName();
    }

    public void postInitialButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        String message = game.getPing() + " - " + visionariaName() + " breakthrough was exhausted"
                + (!game.isFowMode() ? " by " + player.getRepresentationNoPing() : "") + ".";
        message += "\n> Use the buttons to research non-faction, non-unit upgrade technology, or decline.";
        message +=
                "\n-# > Reminder: This research costs 3 trade goods, and you must give the Deepwrought player a promissory note of your choice.";

        game.removeStoredValue("VisionariaResponded");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("acquireATechWithDwsBt", "Research For 3 Trade Goods", MiscEmojis.tg));
        buttons.add(Buttons.green("giveVisionariaPN", "Give Promissory Note", CardEmojis.PN));
        buttons.add(Buttons.red("declineVisionaria", "Decline"));
        buttons.add(Buttons.gray(
                player.finChecker() + "fleetLogAfterVisionaria",
                "Wait Until All Have Reacted",
                (!game.isFowMode() ? player.getFactionEmoji() : null)));
        buttons.add(Buttons.gray(
                player.finChecker() + "endTurnAfterVisionaria",
                "End Turn After All Have Reacted",
                (!game.isFowMode() ? player.getFactionEmoji() : null)));
        MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(), message, game, player, buttons);
    }

    @ButtonHandler("giveVisionariaPN")
    private void giveVisionariaPN(ButtonInteractionEvent event, Game game, Player player) {
        Player deepwrought = Helper.getPlayerFromUnlockedBreakthrough(game, "deepwroughtbt");
        String message = player.getRepresentationUnfogged()
                + ", please choose which promissory note you wish to send for _" + visionariaName() + "_.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), message, ButtonHelper.getForcedPNSendButtons(game, deepwrought, player));
    }

    @ButtonHandler("fleetLogAfterVisionaria")
    @ButtonHandler("endTurnAfterVisionaria")
    public static void presetMoveAlongAfterVisionaria(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String msg = game.getPing()
                + ", the active player has elected to move the game along after everyone has finished resolving "
                + visionariaRep() + ".";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        game.setTemporaryPingDisable(true);
        game.setStoredValue(buttonID, player.getFaction());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, "endTurnAfterVisionaria", true);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, "fleetLogAfterVisionaria", true);
    }

    @ButtonHandler("declineVisionaria")
    private void declineVisionaria(ButtonInteractionEvent event, Game game, Player player) {
        String msg = "declined to use _Visionaria Select_.";
        respondToVisionaria(event, game, player);
        ReactionService.addReaction(event, player.getGame(), player, msg);
    }

    @ButtonHandler("acquireATechWithDwsBt")
    private void acquireATechWithDwsBt(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean sc = false;
        boolean dws = true;
        boolean firstTime = !buttonID.endsWith("_second");
        game.setComponentAction(true);
        if (player.hasAbility("propagation")) {

            Button getTactic = Buttons.green("increase_tactic_cc", "Gain 1 Tactic Token");
            Button getFleet = Buttons.green("increase_fleet_cc", "Gain 1 Fleet Token");
            Button getStrat = Buttons.green("increase_strategy_cc", "Gain 1 Strategy Token");
            Button doneGainingCC = Buttons.red("deleteButtons_spitItOut", "Done Gaining Command Tokens");
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            String message = player.getRepresentationUnfogged() + ", your current command tokens are "
                    + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
            Button resetCC = Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetCCs", "Reset Command Tokens");
            List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            if (player.getTg() > 2) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), player.getRepresentation() + " automatically spent 3 trade goods.");
                player.setTg(player.getTg() - 3);
            }
            Player deepwrought = Helper.getPlayerFromUnlockedBreakthrough(game, "deepwroughtbt");
            if (deepwrought != null) {

                // Send PN to DWS
                List<Button> sendPNbuttons = ButtonHelper.getForcedPNSendButtons(game, deepwrought, player);
                String dwsPromMsg = player.getRepresentation() + ", please choose a promissory note to send to "
                        + deepwrought.getRepresentation(false, false) + " as part of _" + visionariaName() + "_.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), dwsPromMsg, sendPNbuttons);
            }
        } else {
            ListTechService.acquireATech(event, game, player, sc, dws, TechnologyType.mainFour, firstTime);
        }
        if (firstTime) {
            respondToVisionaria(event, game, player);
            String msg = player.getRepresentationNoPing() + " is using _Visionaria Select_.";
            ReactionService.addReaction(event, player.getGame(), player, msg);
        }
    }

    private void respondToVisionaria(ButtonInteractionEvent event, Game game, Player player) {
        String value = game.getStoredValue("VisionariaResponded");
        game.setStoredValue("VisionariaResponded", value + "|" + player.getFaction());
        moveOnWhenDone(event, game);
    }

    private boolean readyToMoveOn(Game game) {
        String value = game.getStoredValue("VisionariaResponded");
        for (Player p : game.getRealPlayers()) {
            if (p.hasBreakthrough("deepwroughtbt")) continue;
            if (value.contains("|" + p.getFaction())) continue;
            return false;
        }
        return true;
    }

    private void moveOnWhenDone(ButtonInteractionEvent event, Game game) {
        if (!readyToMoveOn(game)) return;

        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "Could not find active player when trying to move on after _Visionaria Select_.");
            game.removeStoredValue("endTurnAfterVisionaria");
            game.removeStoredValue("fleetLogAfterVisionaria");
            game.removeStoredValue("VisionariaResponded");
            return;
        }
        if (game.getStoredValue("endTurnAfterVisionaria").equals(activePlayer.getFaction())) {
            EndTurnService.endTurnAndUpdateMap(null, game, activePlayer);

        } else if (game.getStoredValue("fleetLogAfterVisionaria").equals(activePlayer.getFaction())) {
            String message =
                    activePlayer.getRepresentation() + ", please use these buttons to end turn or do another action.";
            List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(activePlayer, game, true, event);
            MessageHelper.sendMessageToChannelWithButtons(activePlayer.getCorrectChannel(), message, systemButtons);
        }
        game.removeStoredValue("endTurnAfterVisionaria");
        game.removeStoredValue("fleetLogAfterVisionaria");
        game.removeStoredValue("VisionariaResponded");
        ButtonHelper.deleteMessageDelay(event, 5);
    }

    public void resolveTechResearch(Game game, Player player, String techID) {
        TechnologyModel techM = Mapper.getTech(techID);
        Player deepwrought = Helper.getPlayerFromUnlockedBreakthrough(game, "deepwroughtbt");
        if (deepwrought != null) {
            // DWS Copy Tech
            if (!deepwrought.hasTech(techID)) {
                deepwrought.addTech(techID);
                ButtonHelperCommanders.resolveNekroCommanderCheck(deepwrought, techID, game);
                String dwsMsg = deepwrought.getRepresentationUnfogged() + " also acquired "
                        + techM.getRepresentation(false) + " due to _" + visionariaName() + "_.";
                MessageHelper.sendMessageToChannel(deepwrought.getCorrectChannel(), dwsMsg);
            }

            // Send PN to DWS
            List<Button> sendPNbuttons = ButtonHelper.getForcedPNSendButtons(game, deepwrought, player);
            String dwsPromMsg = player.getRepresentation() + ", please choose a promissory note to send to "
                    + deepwrought.getColorIfCanSeeStats(player) + " as part of _" + visionariaName() + "_.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), dwsPromMsg, sendPNbuttons);
        }
    }
}
