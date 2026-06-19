package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class TaUnitHandler {

    private static final String MECH_ID = "ta_mech";
    private static final String DEPLOY_MECH_PREFIX = "taMechDeploy_";
    private static final String FLAGSHIP_ID = "ta_flagship";
    private static final String POSITIVE_ATTACHMENT = "worldshaperpositive";
    private static final String NEGATIVE_ATTACHMENT = "worldshapernegative";
    private static final String SELECT_POSITIVE_PREFIX = "taWorldshaperPositive_";
    private static final String SELECT_NEGATIVE_PREFIX = "taWorldshaperNegative_";
    private static final String WORLD_SHAPER_TILE_PREFIX = "taWorldshaperTile_";

    public static void offerTaMechDeploy(
            GenericInteractionCreateEvent event, Player player, Game game, Tile tile, String planetName) {
        if (player == null
                || game == null
                || tile == null
                || !player.hasUnit(MECH_ID)
                || planetName.isBlank()
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4)
                || !player.getPlanetsAllianceMode().contains(planetName)) {
            return;
        }

        String message = player.getRepresentationUnfogged()
                + ", you attached a design to "
                + Helper.getPlanetRepresentation(planetName, game)
                + ". You may spend 1 resource to place a mech on that planet.";

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + DEPLOY_MECH_PREFIX + tile.getPosition() + "|" + planetName,
                        "Pay 1r to Deploy Mech",
                        FactionEmojis.ta),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler(DEPLOY_MECH_PREFIX)
    public static void resolveDeployTaMech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (game == null || player == null || event == null || !buttonID.startsWith(DEPLOY_MECH_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(DEPLOY_MECH_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }
        String tilePosition = parts[0];
        String planetName = parts[1];
        Tile tile = game.getTileByPosition(tilePosition);

        if (tilePosition == null
                || planetName == null
                || tile == null
                || !player.hasUnit(MECH_ID)
                || !player.getPlanetsAllianceMode().contains(planetName)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4)) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planetName);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " deployed 1 mech on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with **Valuator**.");

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        buttons.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " please pay 1 resource for **Valuator**.",
                buttons);
    }

    public static void resolveWorldshaperOnMove(
            GenericInteractionCreateEvent event, Game game, Player player, Tile destinationTile) {
        if (game == null || player == null || destinationTile == null || !player.hasUnit(FLAGSHIP_ID)) {
            return;
        }

        if (!ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP_ID, player, destinationTile)) {
            return;
        }

        offerWorldshaperButtonsForTile(game, player, destinationTile);
    }

    public static void offerWorldshaperOnFlagshipPlacement(
            GenericInteractionCreateEvent event, Game game, UnitKey unitKey, String location, Tile tile) {
        if (game == null
                || unitKey == null
                || tile == null
                || unitKey.unitType() != UnitType.Flagship
                || !Constants.SPACE.equals(location)) {
            return;
        }

        Player player = game.getPlayerFromColorOrFaction(unitKey.colorID());
        if (player == null || !player.hasUnit(FLAGSHIP_ID)) {
            return;
        }

        UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
        if (unitModel == null
                || !FLAGSHIP_ID.equals(unitModel.getId())
                || !ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP_ID, player, tile)) {
            return;
        }

        offerWorldshaperButtonsForTile(game, player, tile);
    }

    public static void clearWorldshaperOnFlagshipDestroy(Player player, RemovedUnit unit) {
        if (player == null || unit == null || unit.unitKey().unitType() != UnitType.Flagship) {
            return;
        }

        UnitModel unitModel = player.getUnitFromUnitKey(unit.unitKey());
        if (unitModel == null || !FLAGSHIP_ID.equals(unitModel.getId())) {
            return;
        }

        clearWorldshaperModifiers(unit.tile());
        player.getGame().removeStoredValue(getWorldshaperTileKey(player));
    }

    private static String getWorldshaperTileKey(Player player) {
        return WORLD_SHAPER_TILE_PREFIX + player.getFaction();
    }

    private static void offerWorldshaperButtonsForTile(Game game, Player player, Tile tile) {
        String currentTilePosition = tile.getPosition();
        String storedTilePosition = game.getStoredValue(getWorldshaperTileKey(player));
        if (currentTilePosition.equals(storedTilePosition)) {
            return;
        }

        clearWorldshaperModifiersOnOtherTiles(game, tile);
        game.setStoredValue(getWorldshaperTileKey(player), currentTilePosition);
        offerWorldshaperButtons(game, player, tile);
    }

    private static void clearWorldshaperModifiersOnOtherTiles(Game game, Tile currentTile) {
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null || tile == currentTile) {
                continue;
            }
            clearWorldshaperModifiers(tile);
        }
    }

    public static void clearWorldshaperModifiers(Tile tile) {
        if (tile == null) {
            return;
        }

        String positivePath = Mapper.getAttachmentImagePath(POSITIVE_ATTACHMENT);
        String negativePath = Mapper.getAttachmentImagePath(NEGATIVE_ATTACHMENT);
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (positivePath != null) {
                tile.removeToken(positivePath, planet.getName());
            }
            if (negativePath != null) {
                tile.removeToken(negativePath, planet.getName());
            }
        }
    }

    private static void offerWorldshaperButtons(Game game, Player player, Tile tile) {
        List<String> friendlyPlanets = getFriendlyWorldshaperPlanets(player, tile);
        if (!friendlyPlanets.isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            for (String planetName : friendlyPlanets) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_POSITIVE_PREFIX + tile.getPosition() + "|" + planetName,
                        "Positive: " + Helper.getPlanetRepresentation(planetName, game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", choose a friendly planet in "
                            + tile.getRepresentationForButtons(game, player)
                            + " for **Worldshaper**'s positive attachment.",
                    buttons);
        }

        List<String> enemyPlanets = getEnemyWorldshaperPlanets(game, player, tile);
        if (!enemyPlanets.isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            for (String planetName : enemyPlanets) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_NEGATIVE_PREFIX + tile.getPosition() + "|" + planetName,
                        "Negative: " + Helper.getPlanetRepresentation(planetName, game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", choose a planet controlled by another player in "
                            + tile.getRepresentationForButtons(game, player)
                            + " for **Worldshaper**'s negative attachment.",
                    buttons);
        }
    }

    private static List<String> getFriendlyWorldshaperPlanets(Player player, Tile tile) {
        List<String> friendlyPlanets = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(planet.getName())) {
                friendlyPlanets.add(planet.getName());
            }
        }
        return friendlyPlanets;
    }

    private static List<String> getEnemyWorldshaperPlanets(Game game, Player player, Tile tile) {
        List<String> enemyPlanets = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            for (Player otherPlayer : game.getRealPlayers()) {
                if (otherPlayer != player && otherPlayer.hasPlanet(planetName)) {
                    enemyPlanets.add(planetName);
                    break;
                }
            }
        }
        return enemyPlanets;
    }

    @ButtonHandler(SELECT_POSITIVE_PREFIX)
    public static void resolveWorldshaperPositive(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(SELECT_POSITIVE_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(SELECT_POSITIVE_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        String planetName = parts[1];
        if (tile == null
                || !ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP_ID, player, tile)
                || !getFriendlyWorldshaperPlanets(player, tile).contains(planetName)) {
            return;
        }

        String positivePath = Mapper.getAttachmentImagePath(POSITIVE_ATTACHMENT);
        if (positivePath != null) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                tile.removeToken(positivePath, planet.getName());
            }
            tile.addToken(positivePath, planetName);
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                Helper.getPlanetRepresentation(planetName, game) + " gets the positive **Worldshaper** attachment.");
    }

    @ButtonHandler(SELECT_NEGATIVE_PREFIX)
    public static void resolveWorldshaperNegative(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(SELECT_NEGATIVE_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(SELECT_NEGATIVE_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        String planetName = parts[1];
        if (tile == null
                || !ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP_ID, player, tile)
                || !getEnemyWorldshaperPlanets(game, player, tile).contains(planetName)) {
            return;
        }

        String negativePath = Mapper.getAttachmentImagePath(NEGATIVE_ATTACHMENT);
        if (negativePath != null) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                tile.removeToken(negativePath, planet.getName());
            }
            tile.addToken(negativePath, planetName);
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                Helper.getPlanetRepresentation(planetName, game) + " gets the negative **Worldshaper** attachment.");
    }
}
