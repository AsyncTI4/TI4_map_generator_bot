package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.explore.ExploreService;

@UtilityClass
public class OblivionBreakthroughHandler {
    private static final String CALL_OF_THE_VOID = "oblivionbt";

    private static final String REMAINING_TILES = "oblivionBtRemainingTiles_";
    private static final String PLAYER_QUEUE = "oblivionBtPlayerQueue_";
    private static final String SELECTED_TILE = "oblivionBtSelectedTile_";

    private static final String CHOOSE_TILE = "chooseOblivionBtTile_";
    private static final String PLACE_TILE = "placeOblivionBtTile_";
    private static final String RESOLVE_FRONTIER = "resolveOblivionBtFrontier_";
    private static final String RESOLVE_COMBINED_FRONTIER = "obvBtR3_";
    private static final String DISCARD_COMBINED_FRONTIER = "obvBtD3_";

    public static void startCallOfTheVoid(Game game, Player owner) {
        if (game == null || owner == null || !owner.hasUnlockedBreakthrough(CALL_OF_THE_VOID)) {
            return;
        }

        int playerCount = game.getRealPlayers().size();
        List<String> drawnTiles = OblivionTileHelper.drawUnusedTiles(game, playerCount, null);

        if (drawnTiles.size() != playerCount) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    owner.getRepresentation()
                            + ", there are not enough unused red-backed tiles to resolve _Call of the Void_.");
            return;
        }

        List<Player> playerOrder = Helper.getSpeakerOrderFromThisPlayer(owner, game);
        if (playerOrder.size() != playerCount) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(), "Could not determine the clockwise player order for _Call of the Void_.");
            return;
        }

        game.setStoredValue(REMAINING_TILES + owner.getFaction(), String.join(",", drawnTiles));
        game.setStoredValue(
                PLAYER_QUEUE + owner.getFaction(),
                String.join(",", playerOrder.stream().map(Player::getFaction).toList()));
        game.removeStoredValue(SELECTED_TILE + owner.getFaction());

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                owner.getRepresentation() + " drew " + playerCount
                        + " unused red-backed tiles for _Call of the Void_.");

        sendTileChoiceButtons(game, owner);
    }

    public static void offerFrontierExplores(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, int cardCount) {
        if (!player.hasUnlockedBreakthrough(CALL_OF_THE_VOID)) {
            return;
        }

        List<String> cardIds = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            String cardId = game.drawExplore(Constants.FRONTIER);
            if (cardId != null && Mapper.getExplore(cardId) != null) {
                cardIds.add(cardId);
            }
        }

        if (cardIds.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The frontier exploration deck has no cards to draw.");
            return;
        }

        if (cardIds.size() == 1) {
            ExploreService.expFrontAlreadyDone(event, tile, game, player, cardIds.getFirst());
            return;
        }

        boolean combinedWithVoidsailors = cardCount > 2 && cardIds.size() == 3;
        List<Button> buttons = new ArrayList<>();
        for (String cardId : cardIds) {
            String buttonId = player.factionButtonChecker() + RESOLVE_FRONTIER + cardId + "|" + tile.getPosition();
            if (combinedWithVoidsailors) {
                List<String> otherCardIds =
                        cardIds.stream().filter(id -> !id.equals(cardId)).toList();
                buttonId = player.factionButtonChecker() + RESOLVE_COMBINED_FRONTIER + cardId + "|"
                        + otherCardIds.getFirst() + "|" + otherCardIds.get(1) + "|" + tile.getPosition();
            }
            buttons.add(Buttons.green(
                    buttonId, "Resolve " + Mapper.getExplore(cardId).getName()));
        }
        List<MessageEmbed> embeds = cardIds.stream()
                .map(Mapper::getExplore)
                .map(ExploreModel::getRepresentationEmbed)
                .toList();

        String abilities = combinedWithVoidsailors ? "_Call of the Void_ and **Voidsailors**" : "_Call of the Void_";
        String message = player.getRepresentation() + ", please choose 1 frontier exploration card to resolve using "
                + abilities + ".";
        if (combinedWithVoidsailors) {
            message += " You will then choose 1 of the remaining cards to discard; the other will be returned to the"
                    + " frontier exploration deck.";
        } else {
            message += " The remaining card will be discarded.";
        }
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
    }

    @ButtonHandler(RESOLVE_FRONTIER)
    public static void resolveFrontierExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE_FRONTIER.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(payload[1]);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The system for this frontier exploration could not be found.");
            return;
        }

        ExploreService.expFrontAlreadyDone(event, tile, game, player, payload[0]);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_COMBINED_FRONTIER)
    public static void chooseCombinedFrontierExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(RESOLVE_COMBINED_FRONTIER.length()).split("\\|", 4);
        if (payload.length != 4
                || payload[0].equals(payload[1])
                || payload[0].equals(payload[2])
                || payload[1].equals(payload[2])
                || Mapper.getExplore(payload[0]) == null
                || Mapper.getExplore(payload[1]) == null
                || Mapper.getExplore(payload[2]) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String chosenCardId = payload[0];
        List<String> remainingCardIds = List.of(payload[1], payload[2]);
        String position = payload[3];

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(
                player.factionButtonChecker() + DISCARD_COMBINED_FRONTIER + chosenCardId + "|"
                        + remainingCardIds.getFirst() + "|" + remainingCardIds.get(1) + "|" + position,
                "Discard " + Mapper.getExplore(remainingCardIds.getFirst()).getName()));
        buttons.add(Buttons.red(
                player.factionButtonChecker() + DISCARD_COMBINED_FRONTIER + chosenCardId + "|" + remainingCardIds.get(1)
                        + "|" + remainingCardIds.getFirst() + "|" + position,
                "Discard " + Mapper.getExplore(remainingCardIds.get(1)).getName()));
        List<MessageEmbed> embeds = remainingCardIds.stream()
                .map(Mapper::getExplore)
                .map(ExploreModel::getRepresentationEmbed)
                .toList();

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose 1 frontier exploration card to discard. The other card will be returned to its"
                        + " deck.",
                embeds,
                buttons);
    }

    @ButtonHandler(DISCARD_COMBINED_FRONTIER)
    public static void discardCombinedFrontierExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(DISCARD_COMBINED_FRONTIER.length()).split("\\|", 4);
        if (payload.length != 4
                || payload[0].equals(payload[1])
                || payload[0].equals(payload[2])
                || payload[1].equals(payload[2])
                || Mapper.getExplore(payload[0]) == null
                || Mapper.getExplore(payload[1]) == null
                || Mapper.getExplore(payload[2]) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String chosenCardId = payload[0];
        String discardedCardId = payload[1];
        String returnedCardId = payload[2];
        Tile tile = game.getTileByPosition(payload[3]);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The frontier exploration could not be completed.");
            return;
        }

        game.addExplore(returnedCardId);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " discarded _"
                        + Mapper.getExplore(discardedCardId).getName()
                        + "_ and returned _" + Mapper.getExplore(returnedCardId).getName()
                        + "_ to the frontier exploration deck.");
        ExploreService.expFrontAlreadyDone(event, tile, game, player, chosenCardId);
    }

    private static void sendTileChoiceButtons(Game game, Player owner) {
        List<String> remainingTiles = getStoredList(game, REMAINING_TILES, owner);
        Player currentPlayer = getCurrentPlayer(game, owner);

        if (remainingTiles.isEmpty() || currentPlayer == null) {
            clearCallOfTheVoidState(game, owner);
            return;
        }

        List<String> legalTiles = remainingTiles.stream()
                .filter(tileId -> OblivionTileHelper.hasLegalPlacement(game, tileId))
                .toList();
        if (legalTiles.isEmpty()) {
            clearCallOfTheVoidState(game, owner);
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    owner.getRepresentation()
                            + ", _Call of the Void_ cannot continue automatically because there are no legal edge positions. Please resolve the remaining tile placements manually.");
            return;
        }

        List<MessageEmbed> embeds = legalTiles.stream()
                .map(TileHelper::getTileById)
                .map(tile -> tile.getRepresentationEmbed(false))
                .toList();

        List<Button> buttons = legalTiles.stream()
                .map(tileId -> Buttons.green(
                        currentPlayer.factionButtonChecker() + CHOOSE_TILE + owner.getFaction() + "|" + tileId,
                        TileHelper.getTileById(tileId).getName()))
                .toList();

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                game.getActionsChannel(),
                currentPlayer.getRepresentation()
                        + ", please choose 1 red-backed tile to place for _Call of the Void_.",
                embeds,
                buttons);
    }

    @ButtonHandler(CHOOSE_TILE)
    public static void chooseCallOfTheVoidTile(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CHOOSE_TILE.length()).split("\\|", 2);

        Player owner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String tileId = payload.length == 2 ? payload[1] : null;

        if (owner == null
                || tileId == null
                || player != getCurrentPlayer(game, owner)
                || !getStoredList(game, REMAINING_TILES, owner).contains(tileId)
                || !game.getStoredValue(SELECTED_TILE + owner.getFaction()).isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!OblivionTileHelper.hasLegalPlacement(game, tileId)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", that system tile no longer has a legal edge position. Please choose another tile.");
            return;
        }

        game.setStoredValue(SELECTED_TILE + owner.getFaction(), tileId);
        ButtonHelper.deleteMessage(event);
        sendPlacementButtons(game, owner, player, tileId);
    }

    private static void sendPlacementButtons(Game game, Player owner, Player currentPlayer, String tileId) {
        String buttonPrefix = currentPlayer.factionButtonChecker() + PLACE_TILE + owner.getFaction() + "|";

        List<Button> buttons = OblivionTileHelper.getPlacementButtons(game, currentPlayer, tileId, buttonPrefix);

        if (buttons.isEmpty()) {
            game.removeStoredValue(SELECTED_TILE + owner.getFaction());
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    currentPlayer.getRepresentation()
                            + ", that system tile no longer has a legal edge position. Please choose another tile.");
            sendTileChoiceButtons(game, owner);
            return;
        }

        String message = currentPlayer.getRepresentation()
                + ", please choose an edge position for "
                + TileHelper.getTileById(tileId).getName()
                + ".";

        String paginationPrefix = buttonPrefix + tileId + "_";

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(), message, NewStuffHelper.buttonPagination(buttons, paginationPrefix, 0));
    }

    @ButtonHandler(PLACE_TILE)
    public static void placeCallOfTheVoidTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String placementData = buttonID.substring(PLACE_TILE.length());
        int ownerEnd = placementData.indexOf('|');
        int tileIdEnd = placementData.lastIndexOf('_');

        if (ownerEnd <= 0 || tileIdEnd <= ownerEnd) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String ownerFaction = placementData.substring(0, ownerEnd);
        String tileId = placementData.substring(ownerEnd + 1, tileIdEnd);

        Player owner = game.getPlayerFromColorOrFaction(ownerFaction);
        List<String> remainingTiles = owner == null ? List.of() : getStoredList(game, REMAINING_TILES, owner);

        if (owner == null
                || player != getCurrentPlayer(game, owner)
                || !remainingTiles.contains(tileId)
                || !tileId.equals(game.getStoredValue(SELECTED_TILE + owner.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentation()
                + ", please choose an edge position for "
                + TileHelper.getTileById(tileId).getName()
                + ".";

        String buttonPrefix = player.factionButtonChecker() + PLACE_TILE + owner.getFaction() + "|";
        String paginationPrefix = buttonPrefix + tileId + "_";

        List<Button> buttons = OblivionTileHelper.getPlacementButtons(game, player, tileId, buttonPrefix);

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, paginationPrefix, buttonID)) {
            return;
        }

        String position = placementData.substring(tileIdEnd + 1);
        Tile placedTile = OblivionTileHelper.placeTile(game, tileId, position);
        if (placedTile == null) {
            game.removeStoredValue(SELECTED_TILE + owner.getFaction());
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", that placement is no longer legal. Please choose a system tile and edge position again.");
            sendTileChoiceButtons(game, owner);
            return;
        }

        List<String> updatedTiles = new ArrayList<>(remainingTiles);
        updatedTiles.remove(tileId);

        List<String> updatedQueue = new ArrayList<>(getStoredList(game, PLAYER_QUEUE, owner));
        if (!updatedQueue.isEmpty() && updatedQueue.getFirst().equals(player.getFaction())) {
            updatedQueue.removeFirst();
        }

        game.removeStoredValue(SELECTED_TILE + owner.getFaction());
        ButtonHelper.deleteMessage(event);

        String result =
                player.getRepresentation() + " placed " + placedTile.getRepresentationForButtons(game, player) + ".";
        if (placedTile.getPlanetUnitHolders().isEmpty()) {
            result += " A frontier token was placed in that system.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), result);

        if (updatedTiles.isEmpty()) {
            clearCallOfTheVoidState(game, owner);
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(), owner.getRepresentation() + " finished resolving _Call of the Void_.");
            return;
        }

        if (updatedQueue.isEmpty()) {
            clearCallOfTheVoidState(game, owner);
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    "The player queue ended before every _Call of the Void_ tile was placed.");
            return;
        }

        game.setStoredValue(REMAINING_TILES + owner.getFaction(), String.join(",", updatedTiles));
        game.setStoredValue(PLAYER_QUEUE + owner.getFaction(), String.join(",", updatedQueue));

        sendTileChoiceButtons(game, owner);
    }

    private static Player getCurrentPlayer(Game game, Player owner) {
        List<String> playerQueue = getStoredList(game, PLAYER_QUEUE, owner);
        if (playerQueue.isEmpty()) {
            return null;
        }
        return game.getPlayerFromColorOrFaction(playerQueue.getFirst());
    }

    private static List<String> getStoredList(Game game, String key, Player owner) {
        return Arrays.stream(game.getStoredValue(key + owner.getFaction()).split(","))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static void clearCallOfTheVoidState(Game game, Player owner) {
        game.removeStoredValue(REMAINING_TILES + owner.getFaction());
        game.removeStoredValue(PLAYER_QUEUE + owner.getFaction());
        game.removeStoredValue(SELECTED_TILE + owner.getFaction());
    }
}
