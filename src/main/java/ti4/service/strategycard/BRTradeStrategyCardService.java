package ti4.service.strategycard;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.io.ImageResources;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

@UtilityClass
public class BRTradeStrategyCardService {
    private static final String TRADE_STATION_TOKEN = "token_br_tradestation.png";
    private static final List<String> TRADE_STATIONS = List.of("tradestation1", "tradestation2");
    private static final String PLACE_OR_MOVE_STATION = "tradePrimary_placeOrMoveStation";
    private static final String OFFER_PLACEMENT = "tradePrimary_offerStationPlacement";
    private static final String OFFER_MOVEMENT = "tradePrimary_offerStationMovement";
    private static final String SELECT_PLACEMENT_SYSTEM = "tradePrimary_selectStationPlacementSystem_";
    private static final String SELECT_MOVEMENT_SYSTEM = "tradePrimary_selectStationMovementSystem_";
    private static final String PLACE_STATION = "tradePrimary_placeStation_";

    public static Button getPlaceOrMoveStationButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + PLACE_OR_MOVE_STATION, "Place or Move Trade Station");
    }

    @ButtonHandler(PLACE_OR_MOVE_STATION)
    public static void offerTradeStationChoices(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (!getUnplacedTradeStations(game).isEmpty()) {
            buttons.add(Buttons.green(player.factionButtonChecker() + OFFER_PLACEMENT, "Place Trade Station"));
        }
        if (!getPlacedTradeStations(game).isEmpty()) {
            buttons.add(Buttons.blue(player.factionButtonChecker() + OFFER_MOVEMENT, "Move Trade Station"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a trade station to place or move.",
                buttons);
        event.deferEdit().queue();
    }

    @ButtonHandler(OFFER_PLACEMENT)
    public static void offerPlacementSystems(ButtonInteractionEvent event, Game game, Player player) {
        offerTradeStationSystems(event, game, player, SELECT_PLACEMENT_SYSTEM, "place");
    }

    @ButtonHandler(OFFER_MOVEMENT)
    public static void offerMovementSystems(ButtonInteractionEvent event, Game game, Player player) {
        offerTradeStationSystems(event, game, player, SELECT_MOVEMENT_SYSTEM, "move");
    }

    @ButtonHandler(SELECT_PLACEMENT_SYSTEM)
    public static void offerTradeStationToPlace(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position =
                getSelectedTradeStationSystem(event, game, player, buttonID, SELECT_PLACEMENT_SYSTEM, "place");
        if (position != null) {
            offerTradeStationSelection(event, game, player, position, getUnplacedTradeStations(game), "place");
        }
    }

    @ButtonHandler(SELECT_MOVEMENT_SYSTEM)
    public static void offerTradeStationToMove(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = getSelectedTradeStationSystem(event, game, player, buttonID, SELECT_MOVEMENT_SYSTEM, "move");
        if (position != null) {
            offerTradeStationSelection(event, game, player, position, getPlacedTradeStations(game), "move");
        }
    }

    @ButtonHandler(PLACE_STATION)
    public static void placeTradeStation(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_STATION.length()).split("\\|", 2);
        if (payload.length != 2 || !TRADE_STATIONS.contains(payload[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String station = payload[0];
        List<Button> buttons = getEligibleTradeStationTiles(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_STATION + station + "|" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        String prefix = player.factionButtonChecker() + PLACE_STATION + station + "|";
        String message = player.getRepresentationNoPing() + ", choose a system for Trade Station "
                + station.charAt(station.length() - 1) + ".";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[1]);
        if (tile == null || !getEligibleTradeStationTiles(game, player).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile existingTile = getTradeStationLocation(game, station);
        Planet stationHolder = existingTile == null ? null : existingTile.getUnitHolderFromPlanet(station);
        if (existingTile != null) {
            existingTile.getUnitHolders().remove(station);
            existingTile.removeToken(TRADE_STATION_TOKEN, Constants.SPACE);
        }
        if (stationHolder == null) {
            stationHolder = new Planet(station, Constants.SPACE_CENTER_POSITION);
        }
        tile.getUnitHolders().put(station, stationHolder);
        tile.addToken(TRADE_STATION_TOKEN, Constants.SPACE);
        game.clearPlanetsCache();
        transferTradeStationControl(game, player, station);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed Trade Station " + station.charAt(station.length() - 1)
                        + " in " + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Tile> getEligibleTradeStationTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .filter(tile -> !hasSpaceStation(tile, game))
                .filter(tile -> !tile.getSpaceUnitHolder().getTokenList().contains(TRADE_STATION_TOKEN))
                .filter(tile -> !tile.getSpaceUnitHolder().getTokenList().contains("token_gedustation.png"))
                .filter(tile -> !game.isFowMode()
                        || FoWHelper.getTilePositionsToShow(game, player).contains(tile.getPosition()))
                .toList();
    }

    private static Tile getTradeStationLocation(Game game, String station) {
        Tile stationHolderTile = game.getTileMap().values().stream()
                .filter(tile -> tile.getUnitHolderFromPlanet(station) != null)
                .findFirst()
                .orElse(null);
        if (stationHolderTile != null) {
            return stationHolderTile;
        }
        List<String> stationsWithHolders = TRADE_STATIONS.stream()
                .filter(tradeStation -> game.getTileMap().values().stream()
                        .anyMatch(tile -> tile.getUnitHolderFromPlanet(tradeStation) != null))
                .toList();
        List<String> stationsWithoutHolders = TRADE_STATIONS.stream()
                .filter(tradeStation -> !stationsWithHolders.contains(tradeStation))
                .toList();
        int stationIndex = stationsWithoutHolders.indexOf(station);
        if (stationIndex < 0) {
            return null;
        }
        List<Tile> unassociatedStationTokens = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().contains(TRADE_STATION_TOKEN))
                .filter(tile -> TRADE_STATIONS.stream()
                        .noneMatch(tradeStation -> tile.getUnitHolderFromPlanet(tradeStation) != null))
                .sorted((tile1, tile2) -> tile1.getPosition().compareTo(tile2.getPosition()))
                .toList();
        return stationIndex < unassociatedStationTokens.size() ? unassociatedStationTokens.get(stationIndex) : null;
    }

    private static void offerTradeStationSystems(
            ButtonInteractionEvent event, Game game, Player player, String buttonPrefix, String action) {
        List<Button> buttons = getEligibleTradeStationTiles(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + buttonPrefix + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentationNoPing() + " has no eligible system for a trade station.",
                    List.of());
            return;
        }
        String prefix = player.factionButtonChecker() + buttonPrefix;
        String message = player.getRepresentationNoPing() + ", choose a system to " + action + " a Trade Station.";
        MessageHelper.editMessageWithButtons(event, message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static String getSelectedTradeStationSystem(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String buttonID,
            String buttonPrefix,
            String action) {
        List<Button> buttons = getEligibleTradeStationTiles(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + buttonPrefix + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        String prefix = player.factionButtonChecker() + buttonPrefix;
        String message = player.getRepresentationNoPing() + ", choose a system to " + action + " a Trade Station.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return null;
        }
        String position = buttonID.substring(buttonPrefix.length());
        if (game.getTileByPosition(position) == null
                || getEligibleTradeStationTiles(game, player).stream()
                        .noneMatch(tile -> tile.getPosition().equals(position))) {
            ButtonHelper.deleteMessage(event);
            return null;
        }
        return position;
    }

    private static void offerTradeStationSelection(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String position,
            List<String> stations,
            String action) {
        List<Button> buttons = stations.stream()
                .map(station -> Buttons.green(
                        player.factionButtonChecker() + PLACE_STATION + station + "|" + position,
                        getTradeStationSelectionLabel(game, player, station, action)))
                .toList();
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose which Trade Station to " + action + ".", buttons);
    }

    private static String getTradeStationSelectionLabel(Game game, Player player, String station, String action) {
        String label = action.substring(0, 1).toUpperCase() + action.substring(1) + " Trade Station "
                + station.charAt(station.length() - 1);
        Tile location = getTradeStationLocation(game, station);
        if (location == null || !"move".equals(action)) {
            return label;
        }
        return label + " from " + location.getRepresentationForButtons(game, player);
    }

    private static List<String> getPlacedTradeStations(Game game) {
        return TRADE_STATIONS.stream()
                .filter(station -> getTradeStationLocation(game, station) != null)
                .toList();
    }

    private static List<String> getUnplacedTradeStations(Game game) {
        return TRADE_STATIONS.stream()
                .filter(station -> getTradeStationLocation(game, station) == null)
                .toList();
    }

    private static boolean hasSpaceStation(Tile tile, Game game) {
        return tile.getUnitHolders().values().stream()
                .filter(Planet.class::isInstance)
                .map(Planet.class::cast)
                .anyMatch(planet -> planet.getPlanetModel() != null && planet.isSpaceStation(game));
    }

    private static void transferTradeStationControl(Game game, Player player, String station) {
        for (Player otherPlayer : game.getRealPlayersNDummies()) {
            if (otherPlayer != player) {
                otherPlayer.removePlanet(station);
            }
        }
        player.addPlanet(station);
        player.exhaustPlanet(station);
        Planet stationHolder = game.getTileFromPlanet(station).getUnitHolderFromPlanet(station);
        new ArrayList<>(stationHolder.getControlList()).forEach(stationHolder::removeControl);
        String color = player.getColor();
        if (color != null && !"null".equals(color)) {
            String controlID = Mapper.getControlID(color);
            if (ImageResources.commandToken(controlID) != null) {
                stationHolder.addControl(controlID);
            }
        }
    }
}
