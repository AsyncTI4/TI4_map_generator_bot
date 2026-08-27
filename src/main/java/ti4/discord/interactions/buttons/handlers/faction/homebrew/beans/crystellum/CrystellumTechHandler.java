package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

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
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class CrystellumTechHandler {
    private static final String RESONANCE_DRIVE = "becrystrd";
    private static final String ATOMIZATION = "becrysta";
    private static final String USE_RESONANCE = "useCrystellumResonanceDrive";
    private static final String DESTROY_RESONANCE = "destroyCrystellumResonanceDrive_";
    private static final String USE_ATOMIZATION = "useCrystellumAtomization";
    private static final String DESTROY_ATOMIZATION = "destroyCrystellumAtomization_";
    private static final String RESONANCE_STATE = "crystellumResonanceDrive_";

    public static void addResonanceDriveButton(List<Button> buttons, Player player) {
        if (player.hasTechReady(RESONANCE_DRIVE)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + USE_RESONANCE, "Use Resonance Drive", FactionEmojis.crystellum));
        }
    }

    public static void addAtomizationButton(List<Button> buttons, Game game, Player player) {
        if (!player.hasTechReady(ATOMIZATION)) return;
        for (Tile tile : game.getTileMap().values())
            for (UnitHolder holder : tile.getUnitHolders().values())
                for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), holder);
                    if (unit != null
                            && unit.getIsShip()
                            && key.unitType() != ti4.helpers.Units.UnitType.Fighter
                            && unit.getCost() <= 3) {
                        buttons.add(Buttons.green(
                                player.factionButtonChecker() + USE_ATOMIZATION,
                                "Use Atomization",
                                FactionEmojis.crystellum));
                        return;
                    }
                }
    }

    @ButtonHandler(USE_RESONANCE)
    public static void useResonanceDrive(ButtonInteractionEvent event, Game game, Player player) {
        sendShipButtons(
                event,
                game,
                player,
                RESONANCE_DRIVE,
                DESTROY_RESONANCE,
                "destroy for _Resonance Drive_",
                Integer.MAX_VALUE);
    }

    @ButtonHandler(USE_ATOMIZATION)
    public static void useAtomization(ButtonInteractionEvent event, Game game, Player player) {
        sendShipButtons(event, game, player, ATOMIZATION, DESTROY_ATOMIZATION, "destroy for _Atomization_", 3);
    }

    @ButtonHandler(DESTROY_RESONANCE)
    public static void destroyForResonanceDrive(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] data = buttonID.substring(DESTROY_RESONANCE.length()).split("\\|", 3);
        if (!destroySelectedShip(event, game, player, data, RESONANCE_DRIVE, Integer.MAX_VALUE)) return;
        UnitModel unit = player.getUnitFromAsyncID(data[2]);
        int ships = unit == null ? 0 : (int) Math.ceil(unit.getCost());
        game.setStoredValue(RESONANCE_STATE + player.getFaction(), Integer.toString(ships));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Resonance Drive_.\n-# You may apply +1 MOVE to " + ships
                        + " ship" + (ships == 1 ? "" : "s") + " this tactical action.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DESTROY_ATOMIZATION)
    public static void destroyForAtomization(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] data = buttonID.substring(DESTROY_ATOMIZATION.length()).split("\\|", 3);
        UnitModel unit = data.length == 3 ? player.getUnitFromAsyncID(data[2]) : null;
        if (unit == null || !destroySelectedShip(event, game, player, data, ATOMIZATION, 3)) return;
        int fighters = (int) Math.ceil(unit.getCost());
        ti4.service.unit.AddUnitService.addUnits(
                event, player.getNomboxTile(), game, player.getColor(), fighters + " fighter");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Atomization_, destroyed a " + unit.getName()
                        + ", and captured " + fighters + " fighter" + (fighters == 1 ? "" : "s") + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendShipButtons(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String tech,
            String prefix,
            String action,
            int maxCost) {
        if (!player.hasTechReady(tech)) return;
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values())
            for (UnitHolder holder : tile.getUnitHolders().values())
                for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), holder);
                    if (unit == null
                            || !unit.getIsShip()
                            || key.unitType() == ti4.helpers.Units.UnitType.Fighter
                            || unit.getCost() > maxCost) continue;
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + prefix + tile.getPosition() + "|" + holder.getName() + "|"
                                    + key.asyncID(),
                            "Destroy " + unit.getName() + " in " + tile.getRepresentationForButtons(game, player),
                            key.unitEmoji()));
                }
        if (!buttons.isEmpty())
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", please choose a non-fighter ship to " + action + ".",
                    buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean destroySelectedShip(
            ButtonInteractionEvent event, Game game, Player player, String[] data, String tech, int maxCost) {
        Tile tile = data.length == 3 ? game.getTileByPosition(data[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(data[1]);
        UnitKey key = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(k -> k.asyncID().equals(data[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = key == null ? null : player.getPriorityUnitByAsyncID(key.asyncID(), holder);
        if (!player.hasTechReady(tech)
                || unit == null
                || !unit.getIsShip()
                || key.unitType() == ti4.helpers.Units.UnitType.Fighter
                || unit.getCost() > maxCost) return false;
        player.exhaustTech(tech);
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(key, 1, holder.getName()), false);
        return true;
    }

    public static String getResonanceDriveMoveNote(Game game, Player player) {
        String value = game.getStoredValue(RESONANCE_STATE + player.getFaction());
        return value.isBlank()
                ? ""
                : "-# _Resonance Drive_: You may apply +1 MOVE to " + value + " ship" + ("1".equals(value) ? "" : "s")
                        + " this tactical action.";
    }

    public static void clearResonanceDrive(Game game) {
        for (Player player : game.getRealPlayers()) game.removeStoredValue(RESONANCE_STATE + player.getFaction());
    }
}
