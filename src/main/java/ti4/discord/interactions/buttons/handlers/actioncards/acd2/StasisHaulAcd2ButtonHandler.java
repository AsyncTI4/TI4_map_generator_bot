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
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
class StasisHaulAcd2ButtonHandler {

    private static final UnitType[] SHIP_UNITS = {
        UnitType.Carrier,
        UnitType.Dreadnought,
        UnitType.Cruiser,
        UnitType.Flagship,
        UnitType.Warsun,
        UnitType.Destroyer,
        UnitType.Fighter
    };

    @ButtonHandler("resolveStasisHaul")
    public static void resolveStasisHaul(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            for (UnitType type : SHIP_UNITS) {
                if (tile.getSpaceUnitHolder().getUnitCount(type, player) > 0 && capacity(player, type) > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "stasisHaulShip_" + tile.getPosition() + "_" + type.name(),
                            type.humanReadableName() + " (capacity " + capacity(player, type) + ") in "
                                    + tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no ships with a capacity value for _Stasis Haul_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose 1 of your ships with a capacity value for _Stasis Haul_.",
                buttons);
    }

    @ButtonHandler("stasisHaulShip_")
    public static void resolveStasisHaulShip(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("stasisHaulShip_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        UnitType type;
        try {
            type = UnitType.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        String where = tile == null ? "the active system" : tile.getRepresentationForButtons(game, player);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("stasisHaulComm", "Gain 1 Commodity (per system)", MiscEmojis.comm));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + "'s " + type.humanReadableName() + " in " + where
                        + " has **+2 capacity** during this tactical action (_Stasis Haul_). Click below to gain 1"
                        + " commodity for each different system it moves into or through.",
                buttons);
    }

    @ButtonHandler("stasisHaulComm")
    public static void resolveStasisHaulComm(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelperStats.gainComms(event, game, player, 1, false);
    }

    private static int capacity(Player player, UnitType type) {
        UnitModel model = player.getUnitByType(type);
        return model == null ? 0 : model.getCapacityValue();
    }
}
