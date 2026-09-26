package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream;

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
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class DreamBreakthroughHandler {
    private static final String DREAM_SPACE_CONVERGENCE = "dreambt";

    public static boolean hasDreamBtNexusMove(Game game, Player player) {
        return player != null
                && player.hasUnlockedBreakthrough(DREAM_SPACE_CONVERGENCE)
                && DreamUnitsHandler.getNexusTokenTiles(game).stream()
                        .anyMatch(tile ->
                                !getDreamBtNexusDestinations(game, player, tile).isEmpty());
    }

    public static void postDreamBtMoveNexusButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (player == null || !player.hasUnlockedBreakthrough(DREAM_SPACE_CONVERGENCE)) return;
        List<Tile> sourceTiles = DreamUnitsHandler.getNexusTokenTiles(game);
        if (sourceTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + ", has no nexus tokens to move.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : sourceTiles) {
            if (!getDreamBtNexusDestinations(game, player, tile).isEmpty()) {
                buttons.add(Buttons.blue(
                        "dream_bt_move_nexus_from_" + tile.getPosition(),
                        "Move Nexus From " + tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", has no valid destination for _Dream-Space Convergence_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a nexus token to move with _Dream-Space Convergence_.",
                buttons);
    }

    @ButtonHandler("dream_bt_move_nexus_from_")
    public static void offerDreamBtMoveNexusDestinations(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnlockedBreakthrough(DREAM_SPACE_CONVERGENCE)) return;
        String fromPosition = buttonID.replace("dream_bt_move_nexus_from_", "");
        Tile fromTile = game.getTileByPosition(fromPosition);
        if (fromTile == null || !DreamAbilitiesHandler.hasNexusToken(fromTile)) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a movable nexus token.");
            return;
        }
        String message = player.getRepresentation() + ", choose where to move that nexus token.";
        List<Button> destinationButtons = getDreamBtNexusDestinations(game, player, fromTile).stream()
                .map(toTile -> Buttons.green(
                        "dream_bt_move_nexus_" + fromPosition + "_to_" + toTile.getPosition(),
                        "Move to " + toTile.getRepresentationForButtons(game, player)))
                .toList();
        String pagePrefix = "dream_bt_move_nexus_destination_" + fromPosition + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                destinationButtons,
                List.of(Buttons.red("deleteButtons", "Decline")),
                message,
                pagePrefix,
                buttonID)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        if (destinationButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", has no valid nexus token destinations.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, getDreamBtDestinationButtons(destinationButtons, pagePrefix, 0));
    }

    @ButtonHandler("dream_bt_move_nexus_destination_")
    public static void changeDreamBtMoveNexusDestinationPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnlockedBreakthrough(DREAM_SPACE_CONVERGENCE)) return;
        String sourceAndPage = buttonID.replace("dream_bt_move_nexus_destination_", "");
        int pageSeparator = sourceAndPage.lastIndexOf("_page");
        if (pageSeparator < 1) return;
        String fromPosition = sourceAndPage.substring(0, pageSeparator);
        Tile fromTile = game.getTileByPosition(fromPosition);
        if (fromTile == null || !DreamAbilitiesHandler.hasNexusToken(fromTile)) return;

        String message = player.getRepresentation() + ", choose where to move that nexus token.";
        List<Button> destinationButtons = getDreamBtNexusDestinations(game, player, fromTile).stream()
                .map(toTile -> Buttons.green(
                        "dream_bt_move_nexus_" + fromPosition + "_to_" + toTile.getPosition(),
                        "Move to " + toTile.getRepresentationForButtons(game, player)))
                .toList();
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                destinationButtons,
                List.of(Buttons.red("deleteButtons", "Decline")),
                message,
                "dream_bt_move_nexus_destination_" + fromPosition + "_",
                buttonID);
    }

    @ButtonHandler("dream_bt_move_nexus_")
    public static void resolveDreamBtMoveNexus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnlockedBreakthrough(DREAM_SPACE_CONVERGENCE)) return;
        String[] parts = buttonID.replace("dream_bt_move_nexus_", "").split("_to_", 2);
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that nexus move.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (fromTile == null
                || toTile == null
                || !getDreamBtNexusDestinations(game, player, fromTile).contains(toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid _Dream-Space Convergence_ move.");
            return;
        }
        if (!DreamUnitsHandler.moveNexusTokenBetweenTiles(player, fromTile, toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "The source system does not contain a nexus token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
    }

    public static void offerDreamBtRemoveCommandTokenButton(Game game, Player player, Tile tile, String msg) {
        Button button = Buttons.gray(
                "dream_bt_remove_cc_" + tile.getPosition(), "Use Dream-Space Convergence", FactionEmojis.dream);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                msg
                        + ", a reminder that if you win this combat as the defender, you may remove your command token from the active system.",
                button);
    }

    @ButtonHandler("dream_bt_remove_cc_")
    public static void resolveDreamBtRemoveCommandToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnlockedBreakthrough(DREAM_SPACE_CONVERGENCE)) return;
        Tile tile = game.getTileByPosition(buttonID.replace("dream_bt_remove_cc_", ""));
        if (tile == null
                || !tile.getPosition().equals(game.getActiveSystem())
                || !CommandCounterHelper.hasCC(player, tile)
                || !DreamAbilitiesHandler.hasNexusTokenOrDreamFlagship(game, tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid Dream-Space Convergence use.");
            return;
        }
        RemoveCommandCounterService.fromTile(player.getColor(), tile, game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation() + ", removed their command token from "
                        + tile.getRepresentationForButtons(game, player) + " with _Dream-Space Convergence_.");
    }

    private static List<Tile> getDreamBtNexusDestinations(Game game, Player player, Tile fromTile) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getPosition().equals(fromTile.getPosition()))
                .filter(tile -> !DreamAbilitiesHandler.hasNexusToken(tile))
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> player.getPlanets().contains(planet.getName())))
                .toList();
    }

    private static List<Button> getDreamBtDestinationButtons(List<Button> buttons, String pagePrefix, int page) {
        List<Button> decline = List.of(Buttons.red("deleteButtons", "Decline"));
        if (buttons.size() <= 25 - decline.size()) {
            List<Button> allButtons = new ArrayList<>(buttons);
            allButtons.addAll(decline);
            return allButtons;
        }
        return NewStuffHelper.buttonPagination(buttons, decline, pagePrefix, 25, page, false);
    }
}
