package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

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
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class RepurposeBrActionCardHandler {
    private static final String RESOLVE = "resolveRepurposeBr";
    private static final String SELECT_SHIP = "repurposeShip_";
    private static final String REPLACE = "repurposeReplace_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Repurpose"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getShipButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no non-fighter ships to replace.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose a ship to replace.", buttons);
    }

    @ButtonHandler(SELECT_SHIP)
    public static void selectShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(SELECT_SHIP.length()).split("\\|", 2);
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        UnitKey key = tile == null ? null : getEligibleShip(player, tile, parts[1]);
        if (key == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + REPLACE + parts[0] + "|" + parts[1] + "|fighter",
                        "Carrier and Fighter"),
                Buttons.green(
                        player.factionButtonChecker() + REPLACE + parts[0] + "|" + parts[1] + "|infantry",
                        "Carrier and Infantry"));
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose the replacement units.", buttons);
    }

    @ButtonHandler(REPLACE)
    public static void replaceShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(REPLACE.length()).split("\\|", 3);
        Tile tile = parts.length == 3 ? game.getTileByPosition(parts[0]) : null;
        UnitKey key = tile == null ? null : getEligibleShip(player, tile, parts[1]);
        if (key == null || (!"fighter".equals(parts.length == 3 ? parts[2] : "") && !"infantry".equals(parts[2]))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), key.unitType(), 1);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 carrier, 1 " + parts[2]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " replaced 1 "
                        + key.unitType().humanReadableName() + " with 1 carrier and 1 " + parts[2]
                        + " using _Repurpose_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitKey key : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
                if (unit != null && unit.getIsShip() && key.unitType() != UnitType.Fighter) {
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + SELECT_SHIP + tile.getPosition() + "|" + key.asyncID(),
                            "Replace " + unit.getName() + " in " + tile.getRepresentationForButtons(game, player),
                            key.unitEmoji()));
                }
            }
        }
        return buttons;
    }

    private static UnitKey getEligibleShip(Player player, Tile tile, String asyncId) {
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(key -> key.asyncID().equals(asyncId))
                .filter(key -> {
                    UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
                    return unit != null && unit.getIsShip() && key.unitType() != UnitType.Fighter;
                })
                .findFirst()
                .orElse(null);
    }
}
