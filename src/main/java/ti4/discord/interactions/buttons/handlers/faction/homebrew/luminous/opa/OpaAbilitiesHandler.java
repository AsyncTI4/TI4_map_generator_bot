package ti4.discord.interactions.buttons.handlers.faction.homebrew.luminous.opa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.tokens.AddTokenCommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.service.map.AddTileService;
import ti4.service.planet.AddPlanetService;

@UtilityClass
public class OpaAbilitiesHandler {
    private static final String OCCUPATIONAL_HAZARD = "occupational_hazard";
    private static final String THE_BELTER_WAY = "the_belter_way";
    private static final String OCCUPATIONAL_HAZARD_PREFIX = "opaOccupationalHazard";
    private static final String OCCUPATIONAL_HAZARD_DECLINE = "opaOccupationalHazardDecline";
    private static final String BELTER_WAY_RESOLVED = "opaBelterWayResolved";
    private static final List<String> BELTER_WAY_PLANETS =
            List.of(Constants.BROKENPLANET3, Constants.BROKENPLANET4, Constants.BROKENPLANET5, Constants.BROKENPLANET6);

    public static void offerOccupationalHazardButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(OCCUPATIONAL_HAZARD)) {
            return;
        }

        List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(
                player,
                game,
                OCCUPATIONAL_HAZARD_PREFIX,
                tile -> isOccupationalHazardTarget(game, player, tile),
                false);
        if (buttons.isEmpty()) {
            resolveBelterWay(game, player);
            return;
        }
        buttons.add(
                Buttons.red(player.factionButtonChecker() + OCCUPATIONAL_HAZARD_DECLINE, "Skip Occupational Hazard"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString()
                        + ", you may replace 1 anomaly system without planets with an asteroid field system tile using **Occupational Hazard**.",
                buttons);
    }

    @ButtonHandler(OCCUPATIONAL_HAZARD_PREFIX + "_")
    public static void resolveOccupationalHazard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasAbility(OCCUPATIONAL_HAZARD)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String position = buttonID.substring((OCCUPATIONAL_HAZARD_PREFIX + "_").length());
        Tile oldTile = game.getTileByPosition(position);
        if (!isOccupationalHazardTarget(game, player, oldTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<TileModel> asteroidCandidates = getOccupationalHazardCandidates(game);
        if (asteroidCandidates.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "No eligible asteroid field anomaly tiles were available for **Occupational Hazard**.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        TileModel replacementModel = RandomHelper.pickRandomFromList(asteroidCandidates);
        String oldTileRepresentation = oldTile.getRepresentationForButtons(game, player);
        UnitHolder space = oldTile.getSpaceUnitHolder();
        Tile newTile = new Tile(replacementModel.getId(), oldTile.getPosition(), space);
        AddTileService.addTile(game, newTile);
        game.clearPlanetsCache();

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " replaced " + oldTileRepresentation + " with "
                        + newTile.getRepresentationForButtons(game, player) + " using **Occupational Hazard**.");
        resolveBelterWay(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(OCCUPATIONAL_HAZARD_DECLINE)
    public static void resolveOccupationalHazardDecline(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null || game == null || player == null) {
            return;
        }

        resolveBelterWay(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean isOccupationalHazardTarget(Game game, Player player, Tile tile) {
        return tile != null && tile.isAnomaly(game, player) && !tile.isAsteroidField() && !tile.hasPlanets();
    }

    private static List<TileModel> getOccupationalHazardCandidates(Game game) {
        boolean includeEronous = Boolean.parseBoolean(game.getStoredValue(Constants.INCLUDE_ERONOUS_TILES));
        Set<ComponentSource> sources = AddTileService.getSources(game, includeEronous);
        Set<TileModel> existingTileModels = game.getTiles().stream()
                .map(Tile::getTileModel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> existingTileIds =
                existingTileModels.stream().map(TileModel::getId).collect(Collectors.toSet());

        return AddTileService.availableTiles(
                        sources, AddTileService.RandomOption.R, existingTileModels, new ArrayList<>())
                .stream()
                .filter(Objects::nonNull)
                .filter(TileModel::isAsteroidField)
                .filter(TileModel::isEmpty)
                .filter(tileModel -> !existingTileIds.contains(tileModel.getId()))
                .toList();
    }

    private static void resolveBelterWay(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAbility(THE_BELTER_WAY)
                || !game.getStoredValue(BELTER_WAY_RESOLVED).isEmpty()) {
            return;
        }
        game.setStoredValue(BELTER_WAY_RESOLVED, player.getFaction());

        StringBuilder message = new StringBuilder(player.toString()).append(" resolved **The Belter Way**.");

        Tile homeTile = player.getHomeSystemTile();
        if (homeTile != null) {
            placeBrokenPlanet(game, homeTile, Constants.BROKENPLANET1);
            if (!player.containsPlanet(Constants.BROKENPLANET1)) {
                AddPlanetService.addPlanet(player, Constants.BROKENPLANET1, game, null, true);
                player.refreshPlanet(Constants.BROKENPLANET1);
            }
            message.append("\n- Placed **Ceres Station** in ")
                    .append(homeTile.getRepresentationForButtons(game, player))
                    .append(".");
        }

        List<Tile> eligibleAsteroidTiles = game.getTiles().stream()
                .filter(Objects::nonNull)
                .filter(Tile::isAsteroidField)
                .filter(tile -> homeTile == null || !tile.getPosition().equals(homeTile.getPosition()))
                .filter(tile -> !tileHasAnyBrokenPlanet(tile))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(eligibleAsteroidTiles);

        int placements = Math.min(BELTER_WAY_PLANETS.size(), eligibleAsteroidTiles.size());
        for (int i = 0; i < placements; i++) {
            String planetName = BELTER_WAY_PLANETS.get(i);
            Tile tile = eligibleAsteroidTiles.get(i);
            placeBrokenPlanet(game, tile, planetName);
            message.append("\n- Placed **")
                    .append(Mapper.getPlanet(planetName).getName())
                    .append("** in ")
                    .append(tile.getRepresentationForButtons(game, player))
                    .append(".");
        }

        if (placements < BELTER_WAY_PLANETS.size()) {
            message.append("\n- Only ")
                    .append(placements)
                    .append(" asteroid field")
                    .append(placements == 1 ? " was" : "s were")
                    .append(" available for the remaining broken planets.");
        }

        game.clearPlanetsCache();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
    }

    private static void placeBrokenPlanet(Game game, Tile tile, String planetName) {
        if (tile == null || tile.getPlanet(planetName) != null) {
            return;
        }
        AddTokenCommand.addToken(null, tile, planetName, game);
    }

    private static boolean tileHasAnyBrokenPlanet(Tile tile) {
        return tile != null
                && List.of(
                                Constants.BROKENPLANET1,
                                Constants.BROKENPLANET2,
                                Constants.BROKENPLANET3,
                                Constants.BROKENPLANET4,
                                Constants.BROKENPLANET5,
                                Constants.BROKENPLANET6)
                        .stream()
                        .anyMatch(planetName -> tile.getPlanet(planetName) != null);
    }
}
