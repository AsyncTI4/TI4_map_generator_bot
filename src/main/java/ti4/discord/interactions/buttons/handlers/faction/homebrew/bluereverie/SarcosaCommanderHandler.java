package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class SarcosaCommanderHandler {
    private static final String SELECT_FIRST_SYSTEM = "selectSarcosaCommanderFirst_";
    private static final String SELECT_SECOND_SYSTEM = "selectSarcosaCommanderSecond_";
    private static final String ADJACENCY = "sarcosaCommanderAdjacency_";

    public static void offerAdjacencySelection(Game game, Player player) {
        clearAdjacency(game, player);
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "sarcosacommander")) {
            return;
        }
        List<Button> buttons = getEligibleSystemButtons(game, player, SELECT_FIRST_SYSTEM);
        if (buttons.size() < 2) {
            return;
        }
        buttons = new ArrayList<>(buttons);
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the first system for _Yuri Hargon_ to treat as adjacent during this tactical action.",
                buttons);
    }

    @ButtonHandler(SELECT_FIRST_SYSTEM)
    public static void selectFirstSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String firstPosition = buttonID.substring(SELECT_FIRST_SYSTEM.length());
        Tile firstTile = game.getTileByPosition(firstPosition);
        if (!isEligibleSystem(game, player, firstTile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system is no longer eligible.");
            return;
        }
        List<Button> buttons =
                getEligibleSystemButtons(game, player, SELECT_SECOND_SYSTEM + firstPosition + "|").stream()
                        .filter(button -> !button.getCustomId().endsWith("|" + firstPosition))
                        .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Choose a different eligible system first.");
            return;
        }
        buttons = new ArrayList<>(buttons);
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the second system for _Yuri Hargon_ to treat as adjacent during this tactical action.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SECOND_SYSTEM)
    public static void selectSecondSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions = buttonID.substring(SELECT_SECOND_SYSTEM.length()).split("\\|", 2);
        if (positions.length != 2) {
            return;
        }
        Tile firstTile = game.getTileByPosition(positions[0]);
        Tile secondTile = game.getTileByPosition(positions[1]);
        if (firstTile == null
                || firstTile == secondTile
                || !isEligibleSystem(game, player, firstTile)
                || !isEligibleSystem(game, player, secondTile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Those systems are no longer eligible.");
            return;
        }
        game.setStoredValue(adjacencyKey(player), firstTile.getPosition() + "|" + secondTile.getPosition());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " treats " + firstTile.getRepresentationForButtons(game, player)
                        + " and " + secondTile.getRepresentationForButtons(game, player)
                        + " as adjacent for this tactical action with _Yuri Hargon_.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean treatsAsAdjacent(Game game, Player player, String position, String otherPosition) {
        if (player == null || player != game.getActivePlayer()) {
            return false;
        }
        String[] positions = game.getStoredValue(adjacencyKey(player)).split("\\|", 2);
        return positions.length == 2
                && ((positions[0].equals(position) && positions[1].equals(otherPosition))
                        || (positions[1].equals(position) && positions[0].equals(otherPosition)));
    }

    public static void clearAdjacency(Game game, Player player) {
        game.removeStoredValue(adjacencyKey(player));
    }

    private static String adjacencyKey(Player player) {
        return ADJACENCY + player.getFaction();
    }

    private static List<Button> getEligibleSystemButtons(Game game, Player player, String prefix) {
        return game.getTileMap().values().stream()
                .filter(tile -> isEligibleSystem(game, player, tile))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + prefix + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        FactionEmojis.sarcosa))
                .toList();
    }

    private static boolean isEligibleSystem(Game game, Player player, Tile tile) {
        return tile != null
                && !tile.isFracture()
                && FoWHelper.knowsTile(game, player, tile.getPosition())
                && (FoWHelper.playerHasUnitsInSystem(player, tile)
                        || FoWHelper.playerHasUnitsInSystem(game.getNeutral(), tile));
    }
}
