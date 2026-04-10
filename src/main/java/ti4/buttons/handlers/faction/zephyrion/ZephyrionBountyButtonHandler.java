package ti4.buttons.handlers.faction.zephyrion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class ZephyrionBountyButtonHandler {

    public static void offerBountyButtons(Game game, Player player) {
        offerBountyButtons(game, player, true);
    }

    public static void offerBountyButtons(Game game, Player player, boolean showRemove) {
        List<String> currentBounties = getBountiesForPlayer(game);
        boolean atCap = currentBounties.size() >= 3;

        String msg;
        if (!showRemove && atCap) {
            msg = player.getRepresentationUnfogged() + " You currently have the following bounties: "
                    + String.join(", ", currentBounties) + ".";
        } else {
            msg = player.getRepresentationUnfogged()
                    + ", please choose which player's ships you wish to place a bounty on"
                    + (showRemove ? ", or which bounty you want to remove" : "")
                    + ". You may have up to 3 bounties at a time.";
            if (!currentBounties.isEmpty()) {
                msg += "\nYou currently have the following bounties: " + String.join(", ", currentBounties) + ".";
            } else {
                msg += " You currently have no bounties.";
            }
        }

        if (!showRemove && atCap) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        // Add buttons; bountyStep2 enforces the 3-token maximum
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            String id = player.getFinsFactionCheckerPrefix() + "bountyStep1_" + p2.getFaction();
            if (game.isFowMode()) {
                buttons.add(Buttons.green(id, p2.getColor(), p2.getFactionEmojiOrColor()));
            } else {
                buttons.add(Buttons.green(id, p2.getFactionModel().getShortName(), p2.getFactionEmoji()));
            }
        }
        if (showRemove) {
            for (String bounty : currentBounties) {
                String faction = bounty.split(" ")[0].toLowerCase();
                String ship = bounty.split(" ")[1].toLowerCase();
                buttons.add(Buttons.red(
                        player.getFinsFactionCheckerPrefix() + "removeBounty_" + faction + "_" + ship,
                        "Remove: " + bounty));
            }
        }
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete These Buttons"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    public static List<String> getBountiesForPlayer(Game game) {
        List<String> bounties = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            for (UnitType unitType : UnitType.values()) {
                String storedValue = game.getStoredValue("bounties" + player.getFaction()
                        + unitType.humanReadableName().toLowerCase());
                if (!storedValue.isEmpty()) {
                    bounties.add(StringUtils.capitalize(player.getFaction()) + " " + unitType.humanReadableName());
                }
            }
        }
        return bounties;
    }

    @ButtonHandler("bountyStep1_")
    static void bountyStep1(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("bountyStep1_", "");
        Player p2 = game.getPlayerFromColorOrFaction(buttonID);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        String msg = player.getRepresentationUnfogged()
                + ", please choose which type of ship you wish to place a bounty on for "
                + p2.getRepresentationNoPing() + ".";
        Set<UnitType> allowedUnits = Set.of(
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun);
        List<Button> buttons = new ArrayList<>();
        for (UnitType unitType : allowedUnits) {
            buttons.add(Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "bountyStep2_" + p2.getFaction() + "_"
                            + unitType.humanReadableName().toLowerCase(),
                    unitType.humanReadableName(),
                    unitType.getUnitTypeEmoji()));
        }
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete These Buttons"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("bountyStep2_")
    static void bountyStep2(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("bountyStep2_", "");
        String colorPlayer = buttonID.split("_")[0];
        String unitTypeString = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(colorPlayer);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }

        if (getBountiesForPlayer(game).size() >= 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You already have 3 bounties placed.");
            return;
        }
        game.setStoredValue("bounties" + p2.getFaction() + unitTypeString, unitTypeString);
        String msg = player.getRepresentationUnfogged() + " placed a bounty on a "
                + StringUtils.capitalize(unitTypeString) + " belonging to " + p2.getRepresentationNoPing() + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("claimBounty_")
    static void claimBounty(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("claimBounty_", "");
        String colorPlayer = buttonID.split("_")[0];
        String unitTypeString = buttonID.split("_")[1].toLowerCase();
        Player p2 = game.getPlayerFromColorOrFaction(colorPlayer);
        game.removeStoredValue("bounties" + p2.getFaction() + unitTypeString);
        player.gainTG(3, true);
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " claimed a bounty and so gained 3 trade goods. The bounty claimed was on a "
                        + StringUtils.capitalize(unitTypeString) + " belonging to "
                        + p2.getRepresentationNoPing() + ".");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("removeBounty_")
    static void removeBounty(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("removeBounty_", "");
        String faction = buttonID.split("_")[0];
        String unitTypeString = buttonID.split("_")[1].toLowerCase();
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        game.removeStoredValue("bounties" + p2.getFaction() + unitTypeString);
        String msg = player.getRepresentationUnfogged() + " removed the bounty on a "
                + StringUtils.capitalize(unitTypeString) + " belonging to " + p2.getRepresentationNoPing() + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteTheOneButton(event);
    }
}
