package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.zephyrion;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class ZephyrionAgentButtonHandler {

    public static void postInitialButtons(Game game, Player player) {
        List<String> bounties = ZephyrionBountyButtonHandler.getBountiesForPlayer(game);
        List<Button> buttons = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayersExcludingThis(player)) {
            for (String bounty : bounties) {
                String faction = bounty.split(" ")[0];
                String ship = bounty.split(" ")[1];
                if ("flagship".equalsIgnoreCase(ship) || "warsun".equalsIgnoreCase(ship)) {
                    continue;
                }
                if (otherPlayer.getFaction().equalsIgnoreCase(faction)) {
                    buttons.add(Buttons.gray(
                            "zephAgentRes_" + faction + "_" + ship,
                            StringUtils.capitalize(ship),
                            otherPlayer.getFactionEmojiOrColor()));
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you may use the buttons to select the ship you want to kill.",
                buttons);
    }

    @ButtonHandler("zephAgentRes_")
    public static void zephAgentRes(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephAgentRes_", "");
        String faction = buttonID.split("_")[0];
        String unitTypeString = buttonID.split("_")[1].toLowerCase();
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        UnitModel unit = p2.getUnitByBaseType(unitTypeString);
        if (unit != null) {
            p2.gainTG((int) unit.getCost(), true);
            ButtonHelperAgents.resolveArtunoCheck(p2, (int) unit.getCost());
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationNoPing() + " received " + (int) unit.getCost()
                            + " trade good(s) equal to the ship's cost.");
            ZephyrionBountyButtonHandler.claimBounty(game, player, p2, unit.getUnitType(), false);
        }

        UnitType type = Mapper.getUnitKey(AliasHandler.resolveUnit(unitTypeString), p2.getColorID())
                .unitType();
        List<Tile> validTiles = new ArrayList<>();
        for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(game, p2, type)) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space.getUnitCount(type, p2.getColor()) > 0 && CommandCounterHelper.hasCC(p2, tile)) {
                validTiles.add(tile);
            }
        }
        if (validTiles.size() == 1) {
            zephShipDestroy(
                    "zephShipDestroy_" + faction + "_" + unitTypeString + "_"
                            + validTiles.getFirst().getPosition(),
                    event,
                    game,
                    player);
        } else {
            List<Button> destroyButtons = new ArrayList<>();
            for (Tile tile : validTiles) {
                destroyButtons.add(Buttons.red(
                        p2.factionButtonChecker() + "zephShipDestroy_" + faction + "_" + unitTypeString + "_"
                                + tile.getPosition(),
                        StringUtils.capitalize(unitTypeString) + " in " + tile.getRepresentationForButtons(game, p2)));
            }
            destroyButtons.add(Buttons.gray("deleteButtons", "Done"));
            MessageHelper.sendMessageToChannelWithButtons(
                    game.getMainGameChannel(),
                    p2.getRepresentation() + ", choose which ship to destroy.",
                    destroyButtons);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("zephShipDestroy_")
    public static void zephShipDestroy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephShipDestroy_", "");
        String[] parts = buttonID.split("_");
        String faction = parts[0];
        String unitTypeString = parts[1];
        String pos = parts[2];

        Player p2 = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileByPosition(pos);
        if (p2 == null || tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve ship destruction, please resolve manually.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitTypeString), p2.getColorID());
        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, tile.getSpaceUnitHolder(), false);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                p2.getRepresentationNoPing() + " destroyed 1 " + StringUtils.capitalize(unitTypeString) + " in "
                        + tile.getRepresentationForButtons(game, p2) + ".");

        offerCapacityRemovalButtons(event, game, p2, tile);
        ButtonHelper.deleteMessage(event);
    }

    static void offerCapacityRemovalButtons(ButtonInteractionEvent event, Game game, Player p2, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        List<Button> buttons = new ArrayList<>();

        int fighters = space.getUnitCount(UnitType.Fighter, p2.getColor());
        if (fighters > 0 && !p2.hasFF2Tech()) {
            buttons.add(Buttons.red(
                    p2.factionButtonChecker() + "zephCapRemove_fighter_" + tile.getPosition(),
                    "Remove Fighter (" + fighters + " available)"));
        }

        int infantry = space.getUnitCount(UnitType.Infantry, p2.getColor());
        if (infantry > 0) {
            buttons.add(Buttons.red(
                    p2.factionButtonChecker() + "zephCapRemove_infantry_" + tile.getPosition(),
                    "Remove Infantry (" + infantry + " available)"));
        }

        int mechs = space.getUnitCount(UnitType.Mech, p2.getColor());
        if (mechs > 0) {
            buttons.add(Buttons.red(
                    p2.factionButtonChecker() + "zephCapRemove_mech_" + tile.getPosition(),
                    "Remove Mech (" + mechs + " available)"));
        }

        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getMainGameChannel(),
                p2.getRepresentation() + ", remove units that exceed capacity and gain 1 trade good each.",
                buttons);
    }

    @ButtonHandler("zephCapRemove_")
    public static void zephCapRemove(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephCapRemove_", "");
        String unitTypeString = buttonID.split("_")[0];
        String pos = buttonID.split("_")[1];

        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitTypeString), player.getColorID());
        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, tile.getSpaceUnitHolder(), false);
        player.gainTG(1, true);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                player.getRepresentationNoPing() + " removed 1 " + StringUtils.capitalize(unitTypeString)
                        + " and gained 1 trade good.");

        UnitHolder space = tile.getSpaceUnitHolder();
        List<Button> buttons = new ArrayList<>();

        int fighters = space.getUnitCount(UnitType.Fighter, player.getColor());
        if (fighters > 0 && !player.hasFF2Tech()) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "zephCapRemove_fighter_" + pos,
                    "Remove Fighter (" + fighters + " available)"));
        }

        int infantry = space.getUnitCount(UnitType.Infantry, player.getColor());
        if (infantry > 0) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "zephCapRemove_infantry_" + pos,
                    "Remove Infantry (" + infantry + " available)"));
        }

        int mechs = space.getUnitCount(UnitType.Mech, player.getColor());
        if (mechs > 0) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "zephCapRemove_mech_" + pos,
                    "Remove Mech (" + mechs + " available)"));
        }

        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
        } else {
            buttons.add(Buttons.gray("deleteButtons", "Done"));
            MessageHelper.editMessageButtons(event, buttons);
        }
    }
}
