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
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class FracturedRealityAcd2ButtonHandler {

    @ButtonHandler("resolveFracturedReality")
    public static void resolveFracturedReality(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (var key :
                    tile.getSpaceUnitHolder().getUnitsByStateForPlayer(player).keySet()) {
                UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
                if (unit != null && unit.getIsShip()) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "fracturedRealityShip_" + tile.getPosition() + "_"
                                    + key.unitType().name(),
                            unit.getName() + " in " + tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have no ships for _Fractured Reality_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the ship that moved using an ingress, egress, or"
                        + " wormhole to replace with _Fractured Reality_.",
                buttons);
    }

    @ButtonHandler("fracturedRealityShip_")
    public static void resolveFracturedRealityShip(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring("fracturedRealityShip_".length()).split("_", 2);
        if (parts.length != 2) return;
        Tile tile = game.getTileByPosition(parts[0]);
        UnitType type;
        try {
            type = UnitType.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (tile == null || tile.getSpaceUnitHolder().getUnitCount(type, player) < 1) return;
        UnitModel unit = player.getPriorityUnitByAsyncID(type.getValue(), tile.getSpaceUnitHolder());
        if (unit == null || !unit.getIsShip()) return;
        if (!game.getStoredValue(pendingReplacementKey(player)).isEmpty()) return;
        List<DiceHelper.Die> dice = DiceHelper.rollDice(10, 1);
        int result = dice.getFirst().getResult();
        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), type, 1);
        game.setStoredValue(pendingReplacementKey(player), tile.getPosition() + "_" + result);
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = Acd2UnitHelper.getUnitsWithCostAtMost(player, tile, result).stream()
                .map(replacement -> Buttons.green(
                        player.factionButtonChecker() + "fracturedRealityPlace_" + replacement.getAsyncId(),
                        replacement.getName()))
                .toList();
        StringBuilder message = new StringBuilder(player.getRepresentationUnfogged())
                .append(" rolled ")
                .append(DiceHelper.formatDiceOutput(dice))
                .append(" for _Fractured Reality_ and removed their ")
                .append(unit.getName())
                .append(" in ")
                .append(tile.getRepresentationForButtons(game, player))
                .append(". Choose 1 replacement unit with a cost value of ")
                .append(result)
                .append(" or less.");
        String loreQuip = getFracturedRealityLoreQuip(result);
        if (loreQuip != null) {
            message.append("\n").append(loreQuip);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message.toString(), buttons);
    }

    @ButtonHandler("fracturedRealityPlace_")
    public static void resolveFracturedRealityPlace(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pending = game.getStoredValue(pendingReplacementKey(player));
        if (pending.isEmpty()) return;
        String[] parts = pending.split("_", 2);
        Tile tile = game.getTileByPosition(parts[0]);
        if (tile == null) return;
        int result = Integer.parseInt(parts[1]);
        String asyncId = buttonID.substring("fracturedRealityPlace_".length());
        UnitModel unit = Acd2UnitHelper.getUnitsWithCostAtMost(player, tile, result).stream()
                .filter(model -> model.getAsyncId().equals(asyncId))
                .findFirst()
                .orElse(null);
        if (unit == null) return;
        game.removeStoredValue(pendingReplacementKey(player));
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + asyncId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 " + unit.getName() + " for _Fractured Reality_.");
    }

    private static String getFracturedRealityLoreQuip(int result) {
        return switch (result) {
            case 1 -> "\"I'm melting! Melting!\" — Wicked Witch of the West, _The Wizard of Oz_";
            case 2, 3 -> "\"It's alive! It's alive!\" — Henry Frankenstein, _Frankenstein_";
            case 4, 5, 6, 7 -> "\"Be afraid. Be very afraid.\" — Veronica Quaife, _The Fly_";
            case 8, 9, 10 -> "\"That's no moon.\" — Obi-Wan Kenobi, _Star Wars_";
            default -> null;
        };
    }

    private static String pendingReplacementKey(Player player) {
        return "fracturedRealityPending_" + player.getUserID();
    }
}
