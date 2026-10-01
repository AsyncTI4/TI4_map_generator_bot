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
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class CrystellumPromissoryHandler {
    private static final String RESOLVE_FRACTURE = "resolveCrystellumFracture_";

    public static void resolveFracture(Game game, Player player, GenericInteractionCreateEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", there is no active system for _Fracture_.");
            return;
        }

        List<Button> buttons = player.getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(unit -> !"fighter".equalsIgnoreCase(unit.getBaseType()))
                .map(UnitModel::getAsyncId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(UnitModel::getName))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + RESOLVE_FRACTURE + tile.getPosition() + "|" + unit.getAsyncId(),
                        "Destroyed " + unit.getName() + " (Cost " + (int) Math.ceil(unit.getCost()) + ")",
                        unit.getUnitEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", you have no non-fighter ships to select for _Fracture_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the non-fighter ship destroyed for _Fracture_.",
                buttons);
    }

    @ButtonHandler(RESOLVE_FRACTURE)
    public static void resolveFractureShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE_FRACTURE.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitModel unit = tile == null || payload.length != 2
                ? null
                : player.getPriorityUnitByAsyncID(payload[1], tile.getSpaceUnitHolder());
        if (unit == null || !unit.getIsShip() || "fighter".equalsIgnoreCase(unit.getBaseType())) return;

        int fighters = (int) Math.ceil(unit.getCost());
        AddUnitService.addUnits(event, tile, game, player.getColor(), fighters + " fighter " + Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " resolved _Fracture_, placed " + fighters + " fighter"
                        + (fighters == 1 ? "" : "s") + " in " + tile.getRepresentationForButtons(game, player)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }
}
