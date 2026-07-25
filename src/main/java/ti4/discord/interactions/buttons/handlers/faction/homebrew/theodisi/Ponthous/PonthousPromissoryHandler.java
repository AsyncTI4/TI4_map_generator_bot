package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class PonthousPromissoryHandler {
    private static final String THUNDERBIRD_PROTOTYPE = "thpnponthous";
    private static final String USE_THUNDERBIRD_PROTOTYPE = "useThunderbirdPrototype_";
    private static final String SELECT_THUNDERBIRD_GROUND_FORCE = "selectThunderbirdGroundForce_";
    private static final String THUNDERBIRD_PROTOTYPE_STATE = "thunderbirdPrototype_";

    public static Button getThunderbirdPrototypeButton(Game game, Player player, Tile tile) {
        if (!canPlayThunderbirdPrototype(game, player, tile)) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + USE_THUNDERBIRD_PROTOTYPE + tile.getPosition(),
                "Use Thunderbird Prototype",
                FactionEmojis.ponthous);
    }

    public static boolean offerThunderbirdPrototypeFromPromissoryPlay(
            GenericInteractionCreateEvent event, Game game, Player player) {
        Tile tile = getThunderbirdCombatTile(game);
        if (!canPlayThunderbirdPrototype(game, player, tile)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", _Thunderbird Prototype_ requires a space combat and one of your ground forces in that system.");
            return true;
        }
        sendThunderbirdGroundForceButtons(event.getMessageChannel(), game, player, tile);
        return true;
    }

    @ButtonHandler(USE_THUNDERBIRD_PROTOTYPE)
    public static void useThunderbirdPrototype(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_THUNDERBIRD_PROTOTYPE.length()));
        if (!canPlayThunderbirdPrototype(game, player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendThunderbirdGroundForceButtons(event.getMessageChannel(), game, player, tile);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_THUNDERBIRD_GROUND_FORCE)
    public static void selectThunderbirdGroundForce(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile activeTile = getThunderbirdCombatTile(game);
        List<Button> buttons = getEligibleThunderbirdGroundForceButtons(game, player, activeTile);
        String message = getThunderbirdGroundForceMessage(player);
        String prefix = player.factionButtonChecker() + SELECT_THUNDERBIRD_GROUND_FORCE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String payload = buttonID.substring(SELECT_THUNDERBIRD_GROUND_FORCE.length());
        String[] values = payload.split("\\|", 3);
        Tile tile = values.length == 3 ? getThunderbirdCombatTile(game) : null;
        if (tile == null || !tile.getPosition().equals(values[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(values[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (!canPlayThunderbirdPrototype(game, player, tile)
                || unitKey == null
                || unit == null
                || !unit.getIsGroundForce()
                || holder.getUnitCount(unitKey) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int galvanizedBefore = holder.getGalvanizedUnitCount(unitKey);
        int galvanizedAdded = holder.addGalvanizedUnit(unitKey, 1);
        player.addPromissoryNoteToPlayArea(THUNDERBIRD_PROTOTYPE);
        game.setStoredValue(
                getStateKey(player),
                String.join(
                        "|",
                        tile.getPosition(),
                        holder.getName(),
                        unitKey.asyncID(),
                        Integer.toString(galvanizedBefore),
                        Integer.toString(galvanizedAdded)));
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);

        Player owner = game.getPNOwner(THUNDERBIRD_PROTOTYPE);
        if (owner != null && owner != player) {
            MessageHelper.sendMessageToChannel(
                    owner.getCardsInfoThread(),
                    owner.getRepresentationNoPing() + ", _Thunderbird Prototype_ was played by "
                            + player.getRepresentationNoPing() + ".");
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " selected 1 " + unitKey.humanReadableName() + " on "
                        + holder.getRepresentation(game)
                        + " to participate in this space combat as a ship and made it Galvanized until the combat ends.");
        ButtonHelper.deleteMessage(event);
    }

    public static UnitModel getThunderbirdPrototypeGroundForce(Game game, Player player, Tile tile) {
        String[] values = game.getStoredValue(getStateKey(player)).split("\\|", 5);
        if (values.length != 5 || tile == null || !tile.getPosition().equals(values[0])) return null;

        UnitHolder holder = tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(values[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        return unit != null && unit.getIsGroundForce() && holder.getUnitCount(unitKey) > 0 ? unit : null;
    }

    public static void clearThunderbirdPrototype(Game game) {
        for (Player player : game.getRealPlayers()) {
            String state = game.getStoredValue(getStateKey(player));
            if (state.isBlank()) continue;

            String[] values = state.split("\\|", 5);
            Tile tile = values.length == 5 ? game.getTileByPosition(values[0]) : null;
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

            Player owner = game.getPNOwner(THUNDERBIRD_PROTOTYPE);
            if (owner != null) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(
                        null, game, player, owner, THUNDERBIRD_PROTOTYPE);
            }
            game.removeStoredValue(getStateKey(player));
        }
    }

    private static boolean canPlayThunderbirdPrototype(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !player.hasPlayablePromissoryInHand(THUNDERBIRD_PROTOTYPE)
                || player.getPromissoryNotesInPlayArea().contains(THUNDERBIRD_PROTOTYPE)
                || getEligibleThunderbirdGroundForceButtons(game, player, tile).isEmpty()) {
            return false;
        }
        return !game.getStoredValue("factionsInCombat").isEmpty();
    }

    private static Tile getThunderbirdCombatTile(Game game) {
        return game == null ? null : game.getTileByPosition(game.getCurrentActiveSystem());
    }

    private static void sendThunderbirdGroundForceButtons(MessageChannel channel, Game game, Player player, Tile tile) {
        List<Button> buttons = getEligibleThunderbirdGroundForceButtons(game, player, tile);
        String message = getThunderbirdGroundForceMessage(player);
        String prefix = player.factionButtonChecker() + SELECT_THUNDERBIRD_GROUND_FORCE;
        MessageHelper.sendMessageToChannelWithButtons(
                channel, message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static List<Button> getEligibleThunderbirdGroundForceButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null) return buttons;
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                if (unit == null || !unit.getIsGroundForce()) continue;
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_THUNDERBIRD_GROUND_FORCE + tile.getPosition() + "|"
                                + holder.getName() + "|" + unitKey.asyncID(),
                        "Use " + unitKey.humanReadableName() + " on " + holder.getRepresentation(game),
                        unitKey.unitEmoji()));
            }
        }
        return buttons;
    }

    private static String getThunderbirdGroundForceMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", choose the ground force that will participate in this space combat with _Thunderbird Prototype_.";
    }

    private static String getStateKey(Player player) {
        return THUNDERBIRD_PROTOTYPE_STATE + player.getFaction();
    }
}
