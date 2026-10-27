package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class XinCommanderHandler {
    private static final String USE_COMMANDER = "useXinCommander_";
    private static final String REPAIR_UNIT = "repairXinCommanderUnit_";
    private static final String COMBAT = "xinCommanderCombat";
    private static final String USED = "xinCommanderUsed_";

    public static void beginCombat(Game game, Tile tile, String unitHolderName) {
        game.setStoredValue(COMBAT, combatId(tile, unitHolderName));
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(usedKey(player));
        }
    }

    public static void clearCombat(Game game) {
        game.removeStoredValue(COMBAT);
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(usedKey(player));
        }
    }

    public static void addCommanderButton(
            List<Button> buttons, Game game, Player player, Tile tile, String unitHolderName) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "xincommander")
                || !game.getStoredValue(usedKey(player)).isEmpty()
                || !combatId(tile, unitHolderName).equals(game.getStoredValue(COMBAT))) {
            return;
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + USE_COMMANDER + combatId(tile, unitHolderName),
                "Use Sun, the Xin Commander",
                FactionEmojis.xin));
    }

    @ButtonHandler(USE_COMMANDER)
    public static void useCommander(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String combatId = buttonID.substring(USE_COMMANDER.length());
        if (!combatId.equals(game.getStoredValue(COMBAT))
                || !game.playerHasLeaderUnlockedOrAlliance(player, "xincommander")
                || !game.getStoredValue(usedKey(player)).isEmpty()) {
            return;
        }
        String[] values = combatId.split("\\|", 2);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        UnitHolder unitHolder = tile == null || values.length != 2
                ? null
                : tile.getUnitHolders().get(values[1]);
        List<Button> repairButtons = getRepairButtons(player, tile, unitHolder, combatId);
        if (repairButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You have no damaged participating units to repair.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose 1 participating unit for Sun to repair.",
                repairButtons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(REPAIR_UNIT)
    public static void repairUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(REPAIR_UNIT.length()).split("\\|", 3);
        if (values.length != 3 || !game.getStoredValue(usedKey(player)).isEmpty()) {
            return;
        }
        Tile tile = game.getTileByPosition(values[0]);
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        if (tile == null
                || !combatId(tile, unitHolder == null ? "" : unitHolder.getName())
                        .equals(game.getStoredValue(COMBAT))) {
            return;
        }
        UnitType unitType = Units.findUnitType(values[2]);
        UnitKey unitKey = Units.getUnitKey(unitType, player.getColorID());
        if (unitHolder == null || !player.unitBelongsToPlayer(unitKey) || unitHolder.getDamagedUnitCount(unitKey) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is no longer available to repair.");
            return;
        }
        unitHolder.removeDamagedUnit(unitKey, 1);
        game.setStoredValue(usedKey(player), "yes");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " repaired 1 " + unitKey.humanReadableName()
                        + " with Sun, the Xin Commander.");
        ButtonHelper.deleteMessage(event);
    }

    public static void warnAboutCoexistingUnitsBombardment(
            Game game, Player bombardmentPlayer, Tile tile, String planetName, GenericInteractionCreateEvent event) {
        for (Player xinPlayer : game.getRealPlayers()) {
            if (!game.playerHasLeaderUnlockedOrAlliance(xinPlayer, "xincommander")
                    || !game.getPlanetsPlayerIsCoexistingOn(xinPlayer).contains(planetName)
                    || !FoWHelper.playerHasUnitsOnPlanet(xinPlayer, tile.getUnitHolderFromPlanet(planetName))) {
                continue;
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    bombardmentPlayer.getRepresentationNoPing() + ", Sun, the Xin Commander protects "
                            + xinPlayer.getRepresentationNoPing()
                            + "'s coexisting units on this planet. Do not use BOMBARDMENT against them.");
        }
    }

    private static List<Button> getRepairButtons(Player player, Tile tile, UnitHolder unitHolder, String combatId) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null || unitHolder == null) {
            return buttons;
        }
        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            if (!player.unitBelongsToPlayer(unitKey) || unitHolder.getDamagedUnitCount(unitKey) < 1) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + REPAIR_UNIT + combatId + "|" + unitKey.unitTypeVal(),
                    "Repair " + unitKey.humanReadableName(),
                    unitKey.unitEmoji()));
        }
        return buttons;
    }

    private static String combatId(Tile tile, String unitHolderName) {
        return tile.getPosition() + "|" + unitHolderName;
    }

    private static String usedKey(Player player) {
        return USED + player.getFaction();
    }
}
