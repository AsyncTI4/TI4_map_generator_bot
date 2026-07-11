package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

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
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class CrystellumPromissoryHandler {
    private static final String PN_ID = "bepncryst";
    private static final String USE_FRACTURE = "crystellumUseFracture_";

    public static boolean canUseFracture(
            Game game, Player player, RemovedUnit unit, boolean combat, List<Player> killers) {
        if (game == null || player == null || unit == null || unit.uh() == null || unit.tile() == null) {
            return false;
        }
        if (!combat || unit.getTotalRemoved() <= 0) {
            return false;
        }
        UnitModel model = player.getUnitFromUnitKey(unit.unitKey());
        if (model == null || !model.getIsShip() || model.getUnitType() == UnitType.Fighter) {
            return false;
        }
        if (!unit.tile().getPosition().equals(game.getActiveSystem())) {
            return false;
        }
        if (!player.getPromissoryNotes().containsKey(PN_ID)) {
            return false;
        }
        if (player.getPromissoryNotesOwned().contains(PN_ID)) {
            return false;
        }
        Player owner = game.getPNOwner(PN_ID);
        if (owner == null || owner == player) {
            return false;
        }
        if (owner.equals(game.getActivePlayer())) {
            return false;
        }
        for (UnitKey key : unit.uh().getUnitKeys()) {
            if (owner.unitBelongsToPlayer(key)) {
                return false;
            }
        }
        return true;
    }

    public static void sendFractureButtons(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit) {
        if (event == null || game == null || player == null || unit == null) {
            return;
        }

        String tilePos = unit.tile().getPosition();
        String useId = player.factionButtonChecker() + USE_FRACTURE + tilePos;
        List<Button> buttons = List.of(Buttons.green(useId, "Use Fracture"), Buttons.red("deleteButtons", "Decline"));

        String message = player.getRepresentationUnfogged()
                + ", you may use _Fracture_ to place 1 fighter in "
                + unit.tile().toString() + ".";
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    @ButtonHandler(USE_FRACTURE)
    public static void resolveUseFracture(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePos = buttonID.replace(USE_FRACTURE, "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find tile to place fighter in.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.getPromissoryNotes().containsKey(PN_ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player receiver = game.getPNOwner(PN_ID);
        if (player.getPromissoryNotesOwned().contains(PN_ID) || receiver == null || receiver == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        SendPromissoryService.sendPromissoryToPlayer(event, game, player, receiver, PN_ID);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.toString() + " used _Fracture_ to place 1 fighter in " + tile.toString()
                        + " and returned the promissory note to " + receiver.getRepresentationNoPing() + ".");
        ButtonHelper.deleteMessage(event);
    }
}
