package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

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
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class NatauPromissoryHandler {
    private static final String RUPTURE = "bepnnatau";
    private static final String PLACE_RUPTURE = "natauPlaceRupture_";
    private static final String OFFER_RUPTURE_SOURCE = "natauMoveRupture_";
    private static final String SELECT_RUPTURE_SOURCE = "natauRuptureSource_";
    private static final String SELECT_RUPTURE_DESTINATION = "natauRuptureDestination_";

    public static void offerRuptureSystems(Game game, Player player) {
        List<Button> placeButtons = getRuptureDestinationButtons(game, player, PLACE_RUPTURE, null);
        List<Button> moveButtons = getRuptureSourceButtons(game, player);
        if (placeButtons.isEmpty() && moveButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible system for _Rupture_.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose where to place or move an ingress token with _Rupture_:",
                placeButtons,
                moveButtons.isEmpty()
                        ? List.of()
                        : List.of(Buttons.blue(
                                player.factionButtonChecker() + OFFER_RUPTURE_SOURCE, "Move an Ingress Token")),
                player.factionButtonChecker() + PLACE_RUPTURE);
    }

    @ButtonHandler(PLACE_RUPTURE)
    public static void placeRupture(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getRuptureDestinationButtons(game, player, PLACE_RUPTURE, null);
        List<Button> extraButtons = getRuptureSourceButtons(game, player).isEmpty()
                ? List.of()
                : List.of(Buttons.blue(player.factionButtonChecker() + OFFER_RUPTURE_SOURCE, "Move an Ingress Token"));
        String message =
                player.getRepresentation() + ", choose where to place or move an ingress token with _Rupture_:";
        String prefix = player.factionButtonChecker() + PLACE_RUPTURE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, extraButtons, message, prefix, buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(buttonID.substring(PLACE_RUPTURE.length()));
        if (tile == null
                || !FoWHelper.playerHasShipsInSystem(player, tile)
                || tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)) {
            return;
        }

        tile.addToken(Constants.TOKEN_INGRESS, Constants.SPACE);
        purgeRupture(
                event,
                game,
                player,
                player.getRepresentationNoPing() + " placed an ingress token in "
                        + tile.getRepresentationForButtons(game, player) + " and purged _Rupture_.");
    }

    @ButtonHandler(OFFER_RUPTURE_SOURCE)
    public static void selectRuptureSource(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> sourceButtons = getRuptureSourceButtons(game, player);
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the ingress token to move with _Rupture_:",
                sourceButtons,
                List.of(),
                player.factionButtonChecker() + SELECT_RUPTURE_SOURCE);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_RUPTURE_SOURCE)
    public static void selectRuptureDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> sourceButtons = getRuptureSourceButtons(game, player);
        String sourceMessage = player.getRepresentation() + ", choose the ingress token to move with _Rupture_:";
        String sourcePrefix = player.factionButtonChecker() + SELECT_RUPTURE_SOURCE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), sourceButtons, sourceMessage, sourcePrefix, buttonID)) {
            return;
        }
        String sourcePosition = buttonID.substring(SELECT_RUPTURE_SOURCE.length());
        Tile source = game.getTileByPosition(sourcePosition);
        if (source == null || !source.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)) {
            return;
        }

        List<Button> destinationButtons =
                getRuptureDestinationButtons(game, player, SELECT_RUPTURE_DESTINATION, sourcePosition);
        if (destinationButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible destination for that ingress token.");
            return;
        }
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose where to move the ingress token from "
                        + source.getRepresentationForButtons(game, player) + ":",
                destinationButtons,
                List.of(),
                player.factionButtonChecker() + SELECT_RUPTURE_DESTINATION + sourcePosition + "|");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_RUPTURE_DESTINATION)
    public static void moveRupture(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions =
                buttonID.substring(SELECT_RUPTURE_DESTINATION.length()).split("\\|", 2);
        if (positions.length != 2) {
            return;
        }
        List<Button> destinationButtons =
                getRuptureDestinationButtons(game, player, SELECT_RUPTURE_DESTINATION, positions[0]);
        String message = player.getRepresentation() + ", choose where to move the ingress token:";
        String prefix = player.factionButtonChecker() + SELECT_RUPTURE_DESTINATION + positions[0] + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), destinationButtons, message, prefix, buttonID)) {
            return;
        }
        Tile source = game.getTileByPosition(positions[0]);
        Tile destination = game.getTileByPosition(positions[1]);
        if (source == null
                || destination == null
                || source.getPosition().equals(destination.getPosition())
                || !source.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)
                || destination.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)
                || !FoWHelper.playerHasShipsInSystem(player, destination)) {
            return;
        }

        source.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        destination.addToken(Constants.TOKEN_INGRESS, Constants.SPACE);
        purgeRupture(
                event,
                game,
                player,
                player.getRepresentationNoPing() + " moved an ingress token from "
                        + source.getRepresentationForButtons(game, player) + " to "
                        + destination.getRepresentationForButtons(game, player) + " and purged _Rupture_.");
    }

    private static void purgeRupture(ButtonInteractionEvent event, Game game, Player player, String message) {
        game.setPurgedPN(RUPTURE);
        player.removePromissoryNote(RUPTURE);
        Player owner = game.getPNOwner(RUPTURE);
        if (owner != null && owner != player) {
            owner.removePromissoryNote(RUPTURE);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        }
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRuptureDestinationButtons(
            Game game, Player player, String buttonPrefix, String sourcePosition) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(tile -> !tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS))
                .filter(tile -> !tile.getPosition().equals(sourcePosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker()
                                + buttonPrefix
                                + (sourcePosition == null ? "" : sourcePosition + "|")
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Button> getRuptureSourceButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_RUPTURE_SOURCE + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }
}
