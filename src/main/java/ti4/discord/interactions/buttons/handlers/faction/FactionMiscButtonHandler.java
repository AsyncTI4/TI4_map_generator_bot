package ti4.discord.interactions.buttons.handlers.faction;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class FactionMiscButtonHandler {

    @ButtonHandler("declareUse_")
    public static void declareUse(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String msg = player.getFactionEmojiOrColor() + " is using " + buttonID.split("_")[1];
        if (msg.contains("Vaylerian")) {
            msg = player.getFactionEmojiOrColor()
                    + " is using Pyndil Gonsuul, the Vaylerian commander, to add +2 capacity to a ship with capacity.";
        }
        if (msg.contains("Tnelis")) {
            msg = player.getFactionEmojiOrColor() + " is using Fillipo Rois, the Tnelis commander,"
                    + " producing a hit against 1 of their __non-fighter__ ships in the system to give __one__ of their ships a +1 move boost."
                    + "\n-# This ability may only be used once per activation.";
            String pos = buttonID.split("_")[2];
            List<Button> buttons =
                    ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", use buttons to assign 1 hit.",
                    buttons);
            game.setStoredValue("tnelisCommanderTracker", player.getFaction());
        }
        if (msg.contains("Ghemina")) {
            msg = player.getFactionEmojiOrColor()
                    + " is using Jarl Vel & Jarl Jotrun, the Ghemina commanders, to gain 1 trade good after winning the space combat.";
            player.setTg(player.getTg() + 1);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
        if (msg.contains("Lightning")) {
            msg = player.getFactionEmojiOrColor()
                    + " is using _Lightning Drives_ to give each ship not transporting fighters or infantry a +1 move boost."
                    + "\n-# A ship transporting just mechs gets this boost.";
        }
        if (msg.contains("Impactor")) {
            msg = player.getFactionEmojiOrColor()
                    + " is using _Reality Field Impactor_ to nullify the effects of one anomaly for this tactical action.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("unlockCommander_")
    public static void unlockCommander(ButtonInteractionEvent event, Player player, String buttonID) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        CommanderUnlockCheckService.checkPlayer(player, buttonID.split("_")[1]);
    }

    @ButtonHandler("nightbloomBuild")
    public static void nightbloomBuild(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> flagButtons = new ArrayList<>();
        for (Tile tile1 : game.getTileMap().values()) {
            if (Helper.getProductionValue(player, game, tile1, false) > 0) {
                String pos1 = tile1.getPosition();
                flagButtons.add(Buttons.blue(player.finChecker() + "anarchy7Build_" + pos1, "Build in " + pos1));
            }
        }
        String flagMessage = player.getRepresentationUnfogged()
                + ", you may build in a system your flagship moved out of or through. The bot does not know which systems these are so it is offering you all systems, but not all are legal.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), flagMessage, flagButtons);
    }

    @ButtonHandler("fogAllianceAgentStep3_")
    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, buttonID);
    }

    @ButtonHandler("getAgentSelection_")
    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the target of your agent.",
                buttons);
    }

    @ButtonHandler("useTA_")
    public static void useTA(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String ta = buttonID.replace("useTA_", "") + "_ta";
        PromissoryNoteHelper.resolvePNPlay(ta, player, game, event);
        ButtonHelper.deleteMessage(event);
    }
}
