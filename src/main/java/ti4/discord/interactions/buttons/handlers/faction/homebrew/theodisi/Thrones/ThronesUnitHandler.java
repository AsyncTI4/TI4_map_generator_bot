package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.commodities.CommodityConversionService;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ThronesUnitHandler {
    private static final String PLACE_AURELION = "placeAurelion_";
    private static final String CONVERT_COMMODITIES_WITH_AURELION = "convertCommoditiesWithAurelion";
    private static final String AURELION = "aurelion";
    private static final String AURELION_STATION = "aurelionstation";
    private static final String THRONES_AURELION = "thrones_aurelion";

    public static Button getAurelionCommodityConversionButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + CONVERT_COMMODITIES_WITH_AURELION,
                "Convert Commodities With Aurelion",
                UnitEmojis.flagship);
    }

    @ButtonHandler(CONVERT_COMMODITIES_WITH_AURELION)
    public static void convertCommoditiesWithAurelion(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet(AURELION_STATION) || player.getExhaustedPlanets().contains(AURELION_STATION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int commodities = player.getCommodities();
        player.exhaustPlanet(AURELION_STATION);
        CommodityConversionService.convertAllComm(event, player, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted " + Helper.getPlanetRepresentation(AURELION_STATION, game)
                        + ", washing their commodit"
                        + (commodities == 1 ? "y" : "ies")
                        + ".");
    }

    public static void syncAurelionStation(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        if (isAurelionOnBoard(game, player)) {
            player.addPlanet(AURELION_STATION);
        } else {
            player.removePlanet(AURELION_STATION);
        }
    }

    public static void offerAurelionPlacement(Game game, Player player) {
        List<Button> buttons = getAurelionPlacementButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no system containing a planet they control to place _Aurelion_.");
            return;
        }

        String message =
                player.getRepresentation() + ", choose a system containing a planet you control to place _Aurelion_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_AURELION;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(PLACE_AURELION)
    public static void placeAurelion(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.ownsUnit(THRONES_AURELION) || isAurelionOnBoard(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getAurelionPlacementButtons(game, player);
        String message =
                player.getRepresentation() + ", choose a system containing a planet you control to place _Aurelion_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_AURELION;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(PLACE_AURELION.length());
        Tile tile = game.getTileByPosition(position);
        boolean controlsPlanetInTile =
                player.getPlanets().stream().anyMatch(planet -> tile == game.getTileFromPlanet(planet));
        if (tile == null || !controlsPlanetInTile) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), AURELION);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean isAurelionOnBoard(Game game, Player player) {
        UnitKey aurelion = Units.getUnitKey(UnitType.Aurelion, player.getColorID());
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .anyMatch(unitHolder -> unitHolder.getUnitCount(aurelion) > 0);
    }

    private static List<Button> getAurelionPlacementButtons(Game game, Player player) {
        List<Tile> eligibleTiles = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null && !eligibleTiles.contains(tile)) {
                eligibleTiles.add(tile);
            }
        }

        return eligibleTiles.stream()
                .map(tile -> Buttons.red(
                        player.factionButtonChecker() + PLACE_AURELION + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        UnitEmojis.flagship))
                .toList();
    }
}
