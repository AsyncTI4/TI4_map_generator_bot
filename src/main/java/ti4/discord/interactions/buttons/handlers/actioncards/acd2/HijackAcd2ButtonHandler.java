package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class HijackAcd2ButtonHandler {

    private static final UnitType[] NON_FIGHTER_SHIPS = {
        UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun
    };

    @ButtonHandler("hijackResolve")
    public static void resolveHijack(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            UnitHolder space = tile.getSpaceUnitHolder();
            for (UnitType type : NON_FIGHTER_SHIPS) {
                if (space.getUnitCount(type, player) > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "hijackShip_" + tile.getPosition() + "_" + type.name(),
                            type.humanReadableName() + " in " + tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have no non-fighter ships for _Hijack_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the non-fighter ship to replace with 1 carrier and 1 infantry for _Hijack_.",
                buttons);
    }

    @ButtonHandler("hijackShip_")
    public static void resolveHijackShip(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring("hijackShip_".length()).split("_", 2);
        if (parts.length != 2) return;
        Tile tile = game.getTileByPosition(parts[0]);
        UnitType type;
        try {
            type = UnitType.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (tile == null || tile.getSpaceUnitHolder().getUnitCount(type, player) < 1) return;
        ButtonHelper.deleteMessage(event);

        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), type, 1);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 cv, 1 gf");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " replaced 1 " + type.humanReadableName() + " in "
                        + tile.getRepresentation() + " with 1 carrier and 1 infantry using _Hijack_.");

        List<Button> buttons = new ArrayList<>();
        for (Tile destination : game.getTileMap().values()) {
            if (isNeutralPlacementEligible(game, destination)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "hijackNeutral_" + destination.getPosition() + "_"
                                + type.name(),
                        destination.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "There is no system without non-neutral units to place the neutral " + type.humanReadableName()
                            + " for _Hijack_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a system that contains no non-neutral units to place"
                        + " 1 neutral " + type.humanReadableName() + " for _Hijack_.",
                buttons);
    }

    @ButtonHandler("hijackNeutral_")
    public static void resolveHijackNeutral(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring("hijackNeutral_".length()).split("_", 2);
        if (parts.length != 2) return;
        Tile tile = game.getTileByPosition(parts[0]);
        UnitType type;
        try {
            type = UnitType.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!isNeutralPlacementEligible(game, tile)) return;
        ButtonHelper.deleteMessage(event);
        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "1 " + type.value);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 neutral " + type.humanReadableName() + " in "
                        + tile.getRepresentation() + " using _Hijack_.");
    }

    private static boolean isNeutralPlacementEligible(Game game, Tile tile) {
        if (tile == null || (tile.getTileModel() != null && tile.getTileModel().isHyperlane())) {
            return false;
        }
        Player neutral = game.findNeutral().orElse(null);
        for (Player p : game.getRealPlayersNDummies()) {
            if (p == neutral) continue;
            if (FoWHelper.playerHasUnitsInSystem(p, tile)) return false;
        }
        return true;
    }
}
