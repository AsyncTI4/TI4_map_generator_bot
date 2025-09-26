package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.button.ReactionService;
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
        String message = game.getPing() + " - " + player.getRepresentationNoPing() + " exhausted their breakthrough, "
                + visionariaRep() + ":";
        message += "\n> Use the buttons to get a technology, or decline.";
        message +=
                "\n-# > Reminder: This tech costs 3 trade goods, and you must give the Deepwrought player a Promissory Note of your choice.";

        game.removeStoredValue("VisionariaResponded");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("acquireATechWithDwsBt", "Get a tech for 3 TGs", MiscEmojis.tg));
        buttons.add(Buttons.red("declineVisionaria", "Decline"));
        buttons.add(Buttons.gray(
                player.finChecker() + "fleetLogAfterVisionaria",
                "Wait until all have reacted",
                player.getFactionEmoji()));
        buttons.add(Buttons.gray(
                player.finChecker() + "endTurnAfterVisionaria",
                "End turn after all have reacted",
                player.getFactionEmoji()));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("fleetLogAfterVisionaria")
    @ButtonHandler("endTurnAfterVisionaria")
    public static void presetMoveAlongAfterVisionaria(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String msg = game.getPing()
                + " the active player has elected to move the game along after everyone has finshed resolving "
                + visionariaRep() + ".";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        game.setTemporaryPingDisable(true);
        game.setStoredValue(buttonID, player.getFaction());
        ButtonHelper.deleteTheOneButton(event, "endTurnAfterVisionaria", true);
        ButtonHelper.deleteTheOneButton(event, "fleetLogAfterVisionaria", true);
    }

    @ButtonHandler("declineVisionaria")
    private void declineVisionaria(ButtonInteractionEvent event, Game game, Player player) {
        String msg = "-# " + player.getRepresentationNoPing() + " declined to use Visionaria Select.";
        respondToVisionaria(event, game, player);
        ReactionService.addReaction(event, player.getGame(), player, msg);
    }

    @ButtonHandler("acquireATechWithDwsBt")
    private void acquireATechWithDwsBt(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean sc = false;
        boolean dws = true;
        boolean firstTime = !buttonID.endsWith("_second");
        ListTechService.acquireATech(event, game, player, sc, dws, TechnologyType.mainFour, firstTime);
        if (firstTime) {
            respondToVisionaria(event, game, player);
            String msg = player.getRepresentationNoPing() + " is using Visionaria Select.";
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
        if (game.getStoredValue("endTurnAfterVisionaria").equals(activePlayer.getFaction())) {
            EndTurnService.endTurnAndUpdateMap(null, game, activePlayer);

        } else if (game.getStoredValue("fleetLogAfterVisionaria").equals(activePlayer.getFaction())) {
            String message = activePlayer.getRepresentation() + " Use buttons to end turn or do another action.";
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
                String dwsMsg = deepwrought.getRepresentation(false, false) + " also acquired the technology due to "
                        + visionariaName() + ": " + techM.getRepresentation(false);
                MessageHelper.sendMessageToChannel(deepwrought.getCorrectChannel(), dwsMsg);
            }

            // Send PN to DWS
            List<Button> sendPNbuttons = ButtonHelper.getForcedPNSendButtons(game, deepwrought, player);
            String dwsPromMsg = player.getRepresentation() + " choose a promissory note to send to "
                    + deepwrought.getRepresentation(false, false) + " as part of" + visionariaName() + ":";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), dwsPromMsg, sendPNbuttons);
        }
    }
}
