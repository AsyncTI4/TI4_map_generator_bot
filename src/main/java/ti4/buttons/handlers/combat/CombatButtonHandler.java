package ti4.buttons.handlers.combat;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.button.ReactionService;

import java.util.List;

@UtilityClass
class CombatButtonHandler {

    @ButtonHandler("automateGroundCombat_")
    public static void automateGroundCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(faction1);
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        String planet = buttonID.split("_")[3];
        String confirmed = buttonID.split("_")[4];
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This button is only for combat participants");
            return;
        }
        if ("true".equals(confirmed)) {
            ButtonHelperModifyUnits.automate_groundCombat(p1, p2, planet, game);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Finished automated ground combat on " + planet);
            ButtonHelper.deleteMessage(event);
        } else {
            Button yesButton = Buttons.green("automateGroundCombat_" + faction1 + "_" + faction2 + "_" + planet + "_true", "Yes");
            Button noButton = Buttons.red("deleteButtons", "No");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "This will automatically result in the winner being determined by odds and remove appropriate units. Proceed?",
                List.of(yesButton, noButton));
        }
    }

    @ButtonHandler("declinePDS_")
    public static void declinePDS(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTile(buttonID.split("_")[1]);
        String msg = player.getRepresentationNoPing() + " officially declines to fire SPACE CANNON" + (tile != null ? " at " + tile.getRepresentation() : "") + ".";
        if (game.isFowMode()) {
            String targetFaction = buttonID.split("_")[2];
            Player target = game.getPlayerFromColorOrFaction(targetFaction);
            if (target != null) {
                MessageHelper.sendMessageToChannel(target.getCorrectChannel(), target.getRepresentationUnfogged() + " " + msg);
            }
        }
        MessageHelper.sendMessageToChannel(game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(), msg);
    }

    @ButtonHandler("applytempcombatmod__" + Constants.AC + "__")
    static void applyTemporaryCombatMod(ButtonInteractionEvent event, Player player, String buttonID) {
        String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
        TemporaryCombatModifierModel combatModAC = CombatTempModHelper.getPossibleTempModifier(Constants.AC,
            acAlias,
            player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Combat modifier will be applied next time you push the combat roll button.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, String buttonID) {
        String pos = buttonID.split("_")[1];
        String planet = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " bombarded " + planet);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("autoAssignAFBHits_")
    public static void autoAssignAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(player, game, game.getTileByPosition(pos), Integer.parseInt(buttonID.split("_")[2]));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cancelAFBHits_")
    public static void cancelAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        int hits = Integer.parseInt(buttonID.split("_")[2]);
        Tile tile = game.getTileByPosition(pos);
        String msg = player.getFactionEmoji() + " cancelled " + hits + " Anti-Fighter Barrage hit" + (hits == 1 ? "" : "s") + " in tile " + tile.getRepresentationForButtons(game, player);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (!systemButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to assign hits", systemButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cancelPdsOffenseHits_")
    public static void cancelPDSOffenseHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        int hits = Integer.parseInt(buttonID.split("_")[2]);
        Tile tile = game.getTileByPosition(pos);
        String msg = player.getFactionEmoji() + " cancelled " + hits + " PDS hit" + (hits == 1 ? "" : "s") + " in tile " + tile.getRepresentationForButtons(game, player);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (!systemButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to assign hits", systemButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cancelGroundHits_")
    public static void cancelGroundHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String planet = buttonID.split("_")[2];
        int hits = Integer.parseInt(buttonID.split("_")[3]);
        String msg = player.getFactionEmoji() + " cancelled " + hits + " ground combat hit" + (hits == 1 ? "" : "s") + " on " + planet;
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (!systemButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to assign hits", systemButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cancelSpaceHits_")
    public static void cancelSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        int hits = Integer.parseInt(buttonID.split("_")[2]);
        Tile tile = game.getTileByPosition(pos);
        String msg = player.getFactionEmoji() + " cancelled " + hits + " space combat hit" + (hits == 1 ? "" : "s") + " in tile " + tile.getRepresentationForButtons(game, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (!systemButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to assign hits", systemButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("autoAssignSpaceCannonOffenceHits_")
    public static void autoAssignSpaceCannonOffenceHits(
        ButtonInteractionEvent event, Player player, String buttonID,
        Game game
    ) {
        String pos = buttonID.split("_")[1];
        int hits = Integer.parseInt(buttonID.split("_")[2]);
        ButtonHelperModifyUnits.autoAssignSpaceCannonOffenceHits(player, game, game.getTileByPosition(pos), hits);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("autoAssignSpaceHits_")
    public static void autoAssignSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        int hits = Integer.parseInt(buttonID.split("_")[2]);
        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, game.getTileByPosition(pos), hits);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("retreat_")
    public static void retreat(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons = ButtonHelper.getRetreatingUnitsButtons(player, game, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + ", please choose the units you wish to retreat.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("retreatUnitsFrom_")
    public static void retreatUnitsFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unittype = buttonID.split("_")[2];
        String amount = buttonID.split("_")[3];
        String retreatTilePos = buttonID.split("_")[4];
        Tile retreatTile = game.getTileByPosition(retreatTilePos);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " retreated " + amount + " " + unittype +
            " from " + game.getTileByPosition(pos).getRepresentationForButtons(game, player) +
            " to " + retreatTile.getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pay1tgToAnnounceARetreat")
    public static void pay1tgToAnnounceARetreat(ButtonInteractionEvent event, Player player) {
        player.setTg(player.getTg() - 1);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentation() + " paid 1 trade good to announce a retreat.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("announceReadyForDice_")
    public static void announceReadyForDice(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String pos = buttonID.split("_")[1];
        String combattype = buttonID.split("_")[2];
        String readyPlayer = buttonID.split("_")[3];

        if (!readyPlayer.equals(player.getFaction())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This button is not for you.");
            return;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " is ready for " + combattype + " combat dice rolls in " +
            game.getTileByPosition(pos).getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("announceARetreat")
    public static void announceARetreat(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " has announced a retreat. They have declared they will " +
            "retreat if possible. Use the buttons below if you are the opponent and wish to respond to this.";
        List<Button> buttons = ButtonHelper.getOppRetreatButtons(game, player);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("applytempcombatmod__" + "tech" + "__")
    public static void applytempcombatmodtech(ButtonInteractionEvent event, Player player) {
        String acAlias = "sc";
        TemporaryCombatModifierModel combatModAC = CombatTempModHelper.getPossibleTempModifier("tech", acAlias,
            player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji()
                    + ", +1 modifier will be applied the next time you push the combat roll button due to _Supercharge_.");
        }
        player.exhaustTech("sc");
        ButtonHelper.deleteTheOneButton(event);
    }
}
