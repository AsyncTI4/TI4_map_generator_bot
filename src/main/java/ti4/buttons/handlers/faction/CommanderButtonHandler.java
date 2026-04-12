package ti4.buttons.handlers.faction;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperHeroes;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class CommanderButtonHandler {

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

    @ButtonHandler("fogAllianceAgentStep3_")
    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, buttonID);
    }
}
