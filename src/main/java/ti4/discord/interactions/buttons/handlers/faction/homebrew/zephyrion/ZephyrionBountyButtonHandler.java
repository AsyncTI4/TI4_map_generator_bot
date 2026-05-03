package ti4.discord.interactions.buttons.handlers.faction.homebrew.zephyrion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ZephyrionBountyButtonHandler {

    public static void offerBountyButtons(Game game, Player player) {
        List<String> currentBounties = getBountiesForPlayer(game);
        boolean atCap = currentBounties.size() >= 3;

        String msg = player.getRepresentationUnfogged()
                + ", please choose which player's ships you wish to place a bounty on."
                + " You may have up to 3 bounties at a time.";
        if (!currentBounties.isEmpty()) {
            msg += "\nYou currently have the following bounties: " + String.join(", ", currentBounties) + ".";
        } else {
            msg += " You currently have no bounties.";
        }

        if (atCap) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            String id = player.factionButtonChecker() + "bountyStep1_" + p2.getFaction();
            if (game.isFowMode()) {
                buttons.add(Buttons.green(id, p2.getColor(), p2.getFactionEmojiOrColor()));
            } else {
                buttons.add(Buttons.green(id, p2.getFactionModel().getShortName(), p2.getFactionEmoji()));
            }
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Delete These Buttons"));
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
                    player.factionButtonChecker() + "bountyStep2_" + p2.getFaction() + "_"
                            + unitType.humanReadableName().toLowerCase(),
                    unitType.humanReadableName(),
                    unitType.getUnitTypeEmoji()));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Delete These Buttons"));
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

    public static void claimBounty(Game game, Player bountyHolder, Player victim, UnitType unitType, boolean combat) {
        String unitTypeString = unitType.humanReadableName().toLowerCase();
        String faction = victim.getFaction();
        String ship = unitType.humanReadableName();
        game.removeStoredValue("bounties" + faction + unitTypeString);
        bountyHolder.gainTG(3, true);
        ButtonHelperAgents.resolveArtunoCheck(bountyHolder, 3);
        MessageHelper.sendMessageToChannel(
                bountyHolder.getCorrectChannel(),
                bountyHolder.getRepresentation() + " claimed a bounty and so gained 3 trade goods."
                        + " The bounty claimed was on a " + StringUtils.capitalize(ship)
                        + " belonging to " + victim.getRepresentationNoPing() + ".");
        if (combat && bountyHolder.hasLeaderUnlocked("zephyrionhero")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray(
                    bountyHolder.factionButtonChecker() + "zephHeroRes_" + faction + "_" + unitTypeString,
                    StringUtils.capitalize(ship),
                    FactionEmojis.zephyrion));
            buttons.add(Buttons.red("deleteButtons", "Delete These"));
            MessageHelper.sendMessageToChannelWithButtons(
                    bountyHolder.getCardsInfoThread(),
                    bountyHolder.getRepresentation() + ", you may use Monturak Homotol, the Zephyrion hero.",
                    buttons);
        }
    }
}
