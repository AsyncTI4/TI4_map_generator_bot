package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

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
import ti4.service.combat.StartCombatService;

@UtilityClass
public class PrecisionTargetingLLButtonHandler {
    private static final String RESOLVE = "resolvePrecisionTargeting";
    private static final String SELECT = "selectPrecisionTarget_";
    private static final String STATE = "precisionTargeting_";

    @ButtonHandler(RESOLVE)
    public static void resolvePrecisionTargeting(ButtonInteractionEvent event, Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile tile = combat == null ? null : game.getTileByPosition(combat.tilePosition());
        Player target = combat == null
                ? null
                : combat.factions().stream()
                        .map(game::getPlayerFromColorOrFaction)
                        .filter(other -> other != null && other != player)
                        .findFirst()
                        .orElse(null);
        if (tile == null || target == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No current space combat was found for _Precision Targeting_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getTargetButtons(game, player, target, tile);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    target.getRepresentationNoPing() + " has no non-fighter ships to target.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(STATE + target.getFaction());
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", select up to 2 of " + target.getRepresentationNoPing()
                        + "'s non-fighter ships for _Precision Targeting_.",
                buttons);
    }

    @ButtonHandler(SELECT)
    public static void selectPrecisionTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile tile = combat == null ? null : game.getTileByPosition(combat.tilePosition());
        UnitKey key = tile == null || target == null
                ? null
                : tile.getSpaceUnitHolder().getUnitKeysForPlayer(target).stream()
                        .filter(unit -> unit.asyncID().equals(payload.length == 2 ? payload[1] : ""))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = key == null ? null : target.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
        if (unit == null || !unit.getIsShip() || key.unitType() == UnitType.Fighter) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That ship is no longer eligible.");
            return;
        }
        String stateKey = STATE + target.getFaction();
        String existingState = game.getStoredValue(stateKey);
        List<String> selected = existingState.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(List.of(existingState.split("\\|", 3)[2].split(",")));
        if (!selected.contains(key.asyncID()) && selected.size() < 2) selected.add(key.asyncID());
        game.setStoredValue(stateKey, tile.getPosition() + "|" + combat.round() + "|" + String.join(",", selected));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " targeted 1 " + unit.getName() + " with _Precision Targeting_.");
        if (selected.size() == 2) {
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentationNoPing()
                            + ", you may select 1 more non-fighter ship for _Precision Targeting_, or decline.",
                    getTargetButtons(game, player, target, tile));
        }
    }

    public static boolean requiresManualAssignment(Game game, Player defender, Tile tile) {
        return !getTargetDescription(game, defender, tile).isEmpty();
    }

    public static String getTargetDescription(Game game, Player defender, Tile tile) {
        String[] state = game.getStoredValue(STATE + defender.getFaction()).split("\\|", 3);
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (state.length != 3
                || tile == null
                || combat == null
                || !tile.getPosition().equals(state[0])) return "";
        try {
            if (combat.round() != Integer.parseInt(state[1])) return "";
        } catch (NumberFormatException e) {
            return "";
        }
        return List.of(state[2].split(",")).stream()
                .map(asyncId -> defender.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(unit -> unit != null)
                .map(UnitModel::getName)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    public static void clearPrecisionTargeting(Game game) {
        for (Player player : game.getRealPlayers()) game.removeStoredValue(STATE + player.getFaction());
    }

    private static List<Button> getTargetButtons(Game game, Player player, Player target, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey key : tile.getSpaceUnitHolder().getUnitKeysForPlayer(target)) {
            UnitModel unit = target.getPriorityUnitByAsyncID(key.asyncID(), tile.getSpaceUnitHolder());
            if (unit != null && unit.getIsShip() && key.unitType() != UnitType.Fighter) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT + target.getFaction() + "|" + key.asyncID(),
                        "Target " + unit.getName(),
                        key.unitEmoji()));
            }
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done"));
        return buttons;
    }
}
