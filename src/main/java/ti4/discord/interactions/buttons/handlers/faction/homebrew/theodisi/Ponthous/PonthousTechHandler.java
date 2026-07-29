package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.UnitModelValueInjectionService;
import ti4.service.unit.UnitModelValueInjectionService.BooleanValueInjection;
import ti4.service.unit.UnitModelValueInjectionService.IntegerValueInjection;
import ti4.service.unit.UnitModelValueInjectionService.UnitValueInjection;

@UtilityClass
public class PonthousTechHandler {
    private static final String THUNDERBIRD_PROTOCOL = "thponthousr";
    private static final String USE_THUNDERBIRD_PROTOCOL = "useThunderbirdProtocol_";
    private static final String SELECT_THUNDERBIRD_PROTOCOL_GROUND_FORCE = "selectThunderbirdProtocolGroundForce_";
    private static final String THUNDERBIRD_PROTOCOL_STATE = "thunderbirdProtocol_";

    public static Button getThunderbirdProtocolButton(Game game, Player player, Tile tile) {
        if (!canUseThunderbirdProtocol(game, player, tile)) return null;
        return Buttons.green(
                player.factionButtonChecker() + USE_THUNDERBIRD_PROTOCOL + tile.getPosition(),
                "Use Thunderbird Protocol",
                FactionEmojis.ponthous);
    }

    @ButtonHandler(USE_THUNDERBIRD_PROTOCOL)
    public static void useThunderbirdProtocol(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_THUNDERBIRD_PROTOCOL.length()));
        if (!canUseThunderbirdProtocol(game, player, tile)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        sendThunderbirdProtocolGroundForceButtons(event.getMessageChannel(), game, player, tile);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_THUNDERBIRD_PROTOCOL_GROUND_FORCE)
    public static void selectThunderbirdProtocolGroundForce(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(SELECT_THUNDERBIRD_PROTOCOL_GROUND_FORCE.length());
        String[] values = payload.split("\\|", 3);
        if (values.length != 3) {
            List<Button> buttons = getEligibleThunderbirdProtocolGroundForceButtons(game, player, getCombatTile(game));
            if (NewStuffHelper.checkAndHandlePaginationChange(
                    event,
                    event.getMessageChannel(),
                    buttons,
                    getThunderbirdProtocolGroundForceMessage(player),
                    player.factionButtonChecker() + SELECT_THUNDERBIRD_PROTOCOL_GROUND_FORCE,
                    buttonID)) {
                return;
            }
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(values[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(values[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        int selectedCount = getThunderbirdProtocolSelectionCount(game, player);
        if (!canUseThunderbirdProtocol(game, player, tile)
                || selectedCount >= 2
                || unitKey == null
                || unit == null
                || !unit.getIsGroundForce()
                || holder.getUnitCount(unitKey)
                        <= getThunderbirdProtocolSelectedUnitCount(game, player, tile, holder, unitKey)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int galvanizedBefore = holder.getGalvanizedUnitCount(unitKey);
        int galvanizedAdded = holder.addGalvanizedUnit(unitKey, 1);
        game.setStoredValue(
                getThunderbirdProtocolStateKey(player, selectedCount + 1),
                String.join(
                        "|",
                        tile.getPosition(),
                        holder.getName(),
                        unitKey.asyncID(),
                        Integer.toString(galvanizedBefore),
                        Integer.toString(galvanizedAdded)));
        if (selectedCount == 0) {
            sendThunderbirdProtocolGroundForceButtons(event.getMessageChannel(), game, player, tile);
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " selected 2 ground forces to participate in this space combat as ships and made them Galvanized until the combat ends.");
        ButtonHelper.deleteMessage(event);
    }

    public static List<UnitModel> getThunderbirdProtocolGroundForces(Game game, Player player, Tile tile) {
        List<UnitModel> units = new ArrayList<>();
        for (int selection = 1; selection <= 2; selection++) {
            String[] values = game.getStoredValue(getThunderbirdProtocolStateKey(player, selection))
                    .split("\\|", 5);
            if (values.length != 5 || tile == null || !tile.getPosition().equals(values[0])) continue;

            UnitHolder holder = tile.getUnitHolders().get(values[1]);
            UnitKey unitKey = holder == null
                    ? null
                    : holder.getUnitKeysForPlayer(player).stream()
                            .filter(key -> key.asyncID().equals(values[2]))
                            .findFirst()
                            .orElse(null);
            UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
            if (unit == null || !unit.getIsGroundForce() || holder.getUnitCount(unitKey) < 1) continue;
            units.add(getThunderbirdCombatGroundForce(unit, holder));
        }
        return units;
    }

    public static void clearThunderbirdProtocol(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (int selection = 2; selection >= 1; selection--) {
                String stateKey = getThunderbirdProtocolStateKey(player, selection);
                String[] values = game.getStoredValue(stateKey).split("\\|", 5);
                if (values.length != 5) {
                    game.removeStoredValue(stateKey);
                    continue;
                }

                Tile tile = game.getTileByPosition(values[0]);
                UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
                UnitKey unitKey = holder == null
                        ? null
                        : holder.getUnitKeysForPlayer(player).stream()
                                .filter(key -> key.asyncID().equals(values[2]))
                                .findFirst()
                                .orElse(null);
                if (unitKey != null) {
                    int baseline = NumberUtils.toInt(values[3]);
                    int added = NumberUtils.toInt(values[4]);
                    int toRemove = Math.min(added, Math.max(0, holder.getGalvanizedUnitCount(unitKey) - baseline));
                    holder.removeGalvanizedUnit(unitKey, toRemove);
                }
                game.removeStoredValue(stateKey);
            }
        }
    }

    private static boolean canUseThunderbirdProtocol(Game game, Player player, Tile tile) {
        return game != null
                && player != null
                && tile != null
                && player.hasTech(THUNDERBIRD_PROTOCOL)
                && game.getStoredValue("factionsInCombat").contains(player.getFaction())
                && getThunderbirdProtocolSelectionCount(game, player) < 2
                && getThunderbirdProtocolAvailableGroundForceCount(game, player, tile)
                        >= 2 - getThunderbirdProtocolSelectionCount(game, player)
                && !getEligibleThunderbirdProtocolGroundForceButtons(game, player, tile)
                        .isEmpty();
    }

    private static void sendThunderbirdProtocolGroundForceButtons(
            MessageChannel channel, Game game, Player player, Tile tile) {
        List<Button> buttons = getEligibleThunderbirdProtocolGroundForceButtons(game, player, tile);
        String prefix = player.factionButtonChecker() + SELECT_THUNDERBIRD_PROTOCOL_GROUND_FORCE;
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                getThunderbirdProtocolGroundForceMessage(player),
                NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static List<Button> getEligibleThunderbirdProtocolGroundForceButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null) return buttons;
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                if (unit == null
                        || !unit.getIsGroundForce()
                        || holder.getUnitCount(unitKey)
                                <= getThunderbirdProtocolSelectedUnitCount(game, player, tile, holder, unitKey)) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_THUNDERBIRD_PROTOCOL_GROUND_FORCE + tile.getPosition()
                                + "|" + holder.getName() + "|" + unitKey.asyncID(),
                        "Select " + unitKey.humanReadableName() + " on " + holder.getRepresentation(game),
                        unitKey.unitEmoji()));
            }
        }
        return buttons;
    }

    private static String getThunderbirdProtocolGroundForceMessage(Player player) {
        int remaining = 2 - getThunderbirdProtocolSelectionCount(player.getGame(), player);
        return player.getRepresentationNoPing() + ", choose " + remaining
                + " ground force" + (remaining == 1 ? "" : "s")
                + " to participate in this space combat with _Thunderbird Protocol_.";
    }

    private static int getThunderbirdProtocolSelectionCount(Game game, Player player) {
        int count = 0;
        for (int selection = 1; selection <= 2; selection++) {
            if (!game.getStoredValue(getThunderbirdProtocolStateKey(player, selection))
                    .isBlank()) count++;
        }
        return count;
    }

    private static int getThunderbirdProtocolSelectedUnitCount(
            Game game, Player player, Tile tile, UnitHolder holder, UnitKey unitKey) {
        int count = 0;
        for (int selection = 1; selection <= 2; selection++) {
            String[] values = game.getStoredValue(getThunderbirdProtocolStateKey(player, selection))
                    .split("\\|", 5);
            if (values.length == 5
                    && tile.getPosition().equals(values[0])
                    && holder.getName().equals(values[1])
                    && unitKey.asyncID().equals(values[2])) {
                count += NumberUtils.toInt(values[4]);
            }
        }
        return count;
    }

    private static int getThunderbirdProtocolAvailableGroundForceCount(Game game, Player player, Tile tile) {
        int count = 0;
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                if (unit != null && unit.getIsGroundForce()) {
                    count += holder.getUnitCount(unitKey)
                            - getThunderbirdProtocolSelectedUnitCount(game, player, tile, holder, unitKey);
                }
            }
        }
        return count;
    }

    private static UnitModel getThunderbirdCombatGroundForce(UnitModel unit, UnitHolder holder) {
        return UnitModelValueInjectionService.injectTemporaryValues(
                unit,
                UnitValueInjection.of(
                        Constants.SPACE.equals(holder.getName())
                                ? null
                                : IntegerValueInjection.create().combatDieCount(1),
                        null,
                        BooleanValueInjection.create()
                                .isShip(true)
                                .isPlanetOnly(false)
                                .isSpaceOnly(false)));
    }

    private static Tile getCombatTile(Game game) {
        return game.getTileByPosition(game.getCurrentActiveSystem());
    }

    private static String getThunderbirdProtocolStateKey(Player player, int selection) {
        return THUNDERBIRD_PROTOCOL_STATE + player.getFaction() + "_" + selection;
    }
}
