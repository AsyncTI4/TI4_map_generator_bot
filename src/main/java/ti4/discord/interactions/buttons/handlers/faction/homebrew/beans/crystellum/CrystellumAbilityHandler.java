package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class CrystellumAbilityHandler {
    private static final String PIECES_OF_A_WHOLE = "crystellumPiecesOfAWhole_";

    public static void addPiecesOfAWholeButton(List<Button> buttons, Player player, Tile tile) {
        if (!player.hasAbility("pieces_of_a_whole")) {
            return;
        }

        int capturedFighters = player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player);
        if (capturedFighters < 1) {
            return;
        }

        buttons.add(Buttons.green(
                player.factionButtonChecker() + PIECES_OF_A_WHOLE + tile.getPosition(),
                "Use Pieces of a Whole",
                FactionEmojis.crystellum));
    }

    @ButtonHandler(PIECES_OF_A_WHOLE)
    public static void usePiecesOfAWhole(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility("pieces_of_a_whole")) {
            return;
        }

        String tilePosition = buttonID.replace(PIECES_OF_A_WHOLE, "");
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) {
            return;
        }

        int capturedFighters = player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player);
        int maximum = Math.min(2, capturedFighters);
        if (maximum < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "crystellumPlacePiecesOfAWhole_" + tilePosition + "_1",
                "Place 1 Fighter"));

        if (capturedFighters >= 2) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "crystellumPlacePiecesOfAWhole_" + tilePosition + "_2",
                    "Place 2 Fighters"));
        }

        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", return up to 2 captured fighters to place that many fighters in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with **Pieces of a Whole**.",
                buttons);
    }

    @ButtonHandler("crystellumPlacePiecesOfAWhole_")
    public static void placePiecesOfAWhole(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.replace("crystellumPlacePiecesOfAWhole_", "");
        int separator = payload.lastIndexOf('_');
        if (separator < 1) {
            return;
        }

        Tile tile = game.getTileByPosition(payload.substring(0, separator));
        if (tile == null || !player.hasAbility("pieces_of_a_whole")) {
            return;
        }

        int requested;
        try {
            requested = Integer.parseInt(payload.substring(separator + 1));
        } catch (NumberFormatException e) {
            return;
        }

        int capturedFighters = player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player);
        int amount = Math.min(Math.min(requested, 2), capturedFighters);
        if (amount < 1) {
            return;
        }

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), amount + " fighter");
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " fighter");

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " returned "
                        + amount
                        + " captured fighter"
                        + (amount == 1 ? "" : "s")
                        + " and placed "
                        + amount
                        + " fighter"
                        + (amount == 1 ? "" : "s")
                        + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with **Pieces of a Whole**.");
    }

    public static void resolveFragmentation(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit destroyedUnit) {
        if (player == null || !player.hasAbility("fragmentation")) {
            return;
        }

        UnitModel unit = player.getUnitFromUnitKey(destroyedUnit.unitKey());
        if (unit == null || !unit.getIsShip() || destroyedUnit.unitKey().unitType() == UnitType.Fighter) {
            return;
        }

        int fightersToCapture = destroyedUnit.getTotalRemoved();
        AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), fightersToCapture + " fighter");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " captured "
                        + fightersToCapture
                        + " fighter"
                        + (fightersToCapture == 1 ? "" : "s")
                        + " from the supply with **Fragmentation**.");
    }
}
