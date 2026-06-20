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
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class PrivateersAcd2ButtonHandler {

    private static final UnitType[] SHIP_UNITS = {
        UnitType.Fighter,
        UnitType.Destroyer,
        UnitType.Cruiser,
        UnitType.Carrier,
        UnitType.Dreadnought,
        UnitType.Flagship,
        UnitType.Warsun
    };
    private static final UnitType[] NON_FIGHTER_SHIPS = {
        UnitType.Destroyer,
        UnitType.Cruiser,
        UnitType.Carrier,
        UnitType.Dreadnought,
        UnitType.Flagship,
        UnitType.Warsun
    };

    @ButtonHandler("resolvePrivateers")
    public static void resolvePrivateers(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            if (countNonFighterShips(tile, player) > 0) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "privateersChoose_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no systems containing your non-fighter ships for _Privateers_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the system whose ships you will replace with neutral ships for _Privateers_.",
                buttons);
    }

    @ButtonHandler("privateersChoose_")
    public static void resolvePrivateersChoose(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("privateersChoose_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Privateers_.");
            return;
        }
        UnitHolder space = tile.getSpaceUnitHolder();
        List<Button> buttons = new ArrayList<>();
        for (UnitType type : NON_FIGHTER_SHIPS) {
            if (space.getUnitCount(type, player) > 0) {
                int cost = shipCost(player, type);
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "privateersResolve_" + pos + "_" + type.name(),
                        "Gain " + cost + " TG (cost of 1 " + type.humanReadableName() + ")"));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "That system has no non-fighter ships for _Privateers_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which non-fighter ship's cost to gain as trade goods"
                        + " for _Privateers_. All of your ships in that system will be replaced with neutral ships.",
                buttons);
    }

    @ButtonHandler("privateersResolve_")
    public static void resolvePrivateersResolve(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("privateersResolve_", "").split("_", 2);
        if (parts.length < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String pos = parts[0];
        UnitType chosen = UnitType.valueOf(parts[1]);
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Privateers_.");
            return;
        }
        UnitHolder space = tile.getSpaceUnitHolder();

        StringBuilder neutralUnits = new StringBuilder();
        for (UnitType type : SHIP_UNITS) {
            int count = space.getUnitCount(type, player);
            if (count > 0) {
                RemoveUnitService.removeUnit(event, tile, game, player, space, type, count);
                if (neutralUnits.length() > 0) neutralUnits.append(", ");
                neutralUnits.append(count).append(" ").append(type.value);
            }
        }
        if (neutralUnits.length() > 0) {
            AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), neutralUnits.toString());
        }

        int cost = shipCost(player, chosen);
        player.setTg(player.getTg() + cost);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " replaced their ships in " + tile.getRepresentation()
                        + " with neutral ships and gained " + cost + " trade goods for _Privateers_ (cost of 1 "
                        + chosen.humanReadableName() + ").");
    }

    private static int countNonFighterShips(Tile tile, Player player) {
        UnitHolder space = tile.getSpaceUnitHolder();
        int total = 0;
        for (UnitType type : NON_FIGHTER_SHIPS) {
            total += space.getUnitCount(type, player);
        }
        return total;
    }

    private static int shipCost(Player player, UnitType type) {
        UnitModel model = player.getUnitByType(type);
        return model == null ? 0 : (int) model.getCost();
    }
}
