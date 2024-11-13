package ti4.commands.special;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands2.CommandHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class CheckDistance extends SpecialSubcommandData {
    public CheckDistance() {
        super(Constants.CHECK_DISTANCE, "Check Distance");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MAX_DISTANCE, "Max distance to check"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        int maxDistance = event.getOption(Constants.MAX_DISTANCE, 8, OptionMapping::getAsInt);
        Map<String, Integer> distances = getTileDistances(game, player, tile.getPosition(), maxDistance, true);

        MessageHelper.sendMessageToEventChannel(event, distances.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .sorted()
            .reduce("Distances: \n", (a, b) -> a + "\n" + b));
    }

    public static int getDistanceBetweenTwoTiles(Game game, Player player, String tilePosition1, String tilePosition2, boolean countsRiftsAsNormal) {
        Map<String, Integer> distances = getTileDistances(game, player, tilePosition1, 8, countsRiftsAsNormal);
        if (distances.get(tilePosition2) != null) {
            return distances.get(tilePosition2);
        }
        return 100;
    }

    public static Map<String, Integer> getTileDistancesRelativeToAllYourUnlockedTiles(Game game, Player player) {
        Map<String, Integer> distances = new HashMap<>();
        List<Tile> originTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!AddCC.hasCC(player, tile) && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                distances.put(tile.getPosition(), 0);
                originTiles.add(tile);
            }
        }
        for (Tile tile : originTiles) {
            Map<String, Integer> someDistances = getTileDistances(game, player, tile.getPosition(), 15, true);
            for (String tilePos : someDistances.keySet()) {
                if (AddCC.hasCC(player, game.getTileByPosition(tilePos))) {
                    continue;
                }
                if (distances.get(tilePos) == null && someDistances.get(tilePos) != null) {
                    distances.put(tilePos, someDistances.get(tilePos));
                } else {
                    if (distances.get(tilePos) != null && someDistances.get(tilePos) != null && distances.get(tilePos) > someDistances.get(tilePos)) {
                        distances.put(tilePos, someDistances.get(tilePos));
                    }
                }
            }
        }
        return distances;
    }

    public static List<String> getAllTilesACertainDistanceAway(Game game, Player player, Map<String, Integer> distances, int target) {
        List<String> tiles = new ArrayList<>();
        for (String pos : distances.keySet()) {
            if (distances.get(pos) != null && distances.get(pos) == target) {
                tiles.add(pos);
            }
        }
        Collections.sort(tiles);
        return tiles;
    }

    public static Map<String, Integer> getTileDistances(Game game, Player player, String tilePosition, int maxDistance, boolean forMap) {
        Map<String, Integer> distances = new HashMap<>();
        distances.put(tilePosition, 0);

        for (int i = 1; i <= maxDistance; i++) {
            Map<String, Integer> distancesCopy = new HashMap<>(distances);
            for (String existingPosition : distancesCopy.keySet()) {
                Tile tile = game.getTileByPosition(existingPosition);
                int num = 0;
                int distance = i;
                if (!existingPosition.equalsIgnoreCase(tilePosition)) {
                    if (tile == null || (tile.isNebula() && player != null && !player.getAbilities().contains("voidborn") && !ButtonHelper.isLawInPlay(game, "shared_research")) || (tile.isSupernova() && player != null && !player.getAbilities().contains("gashlai_physiology")) || (tile.isAsteroidField() && player != null && !player.getTechs().contains("amd") && !player.getTechs().contains("absol_amd"))) {
                        continue;
                    }
                }
                if (!forMap) {
                    if (tile != null && tile.isGravityRift(game)) {
                        num = -1;
                    }
                }
                if (distances.get(existingPosition) != null) {
                    distance = distances.get(existingPosition) + 1;
                }

                addAdjacentPositionsIfNotThereYet(game, existingPosition, distances, player, distance + num);
            }
        }

        for (String otherTilePosition : game.getTileMap().keySet()) {
            distances.putIfAbsent(otherTilePosition, null);
        }

        return distances;
    }

    private static void addAdjacentPositionsIfNotThereYet(Game game, String position, Map<String, Integer> distances, Player player, int distance) {
        for (String tilePosition : adjacentPositions(game, position, player)) {
            if (distances.get(tilePosition) != null && distances.get(tilePosition) > distance) {
                distances.remove(tilePosition);
            }
            distances.putIfAbsent(tilePosition, distance);
        }
    }

    private static Set<String> adjacentPositions(Game game, String position, Player player) {
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, position, player, false);
    }
}
