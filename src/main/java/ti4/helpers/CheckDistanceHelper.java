package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;

@UtilityClass
public class CheckDistanceHelper {

    public static int getDistanceBetweenTwoTiles(
            Game game, Player player, String tilePosition1, String tilePosition2, boolean countsRiftsAsNormal) {
        Map<String, Integer> distances = getTileDistances(game, player, tilePosition1, 8, countsRiftsAsNormal);
        if (distances.get(tilePosition2) != null) {
            return distances.get(tilePosition2);
        }
        return countsRiftsAsNormal || !game.getTileByPosition(tilePosition1).isGravityRift(game) ? 100 : 99;
    }

    private static boolean tileUnlockedForMoving(Game game, Player player, Tile tile) {
        if (ButtonHelper.nomadHeroAndDomOrbCheck(player, game)) return true;
        return !CommandCounterHelper.hasCC(player, tile);
    }

    public static Map<String, Integer> getTileDistancesRelativeToAllYourUnlockedTiles(Game game, Player player) {
        Map<String, Integer> distances = new HashMap<>();
        List<Tile> originTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tileUnlockedForMoving(game, player, tile) && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                distances.put(tile.getPosition(), 0);
                originTiles.add(tile);
            }
        }
        for (Tile tile : originTiles) {
            Map<String, Integer> someDistances = getTileDistances(game, player, tile.getPosition(), 15, true);
            for (Map.Entry<String, Integer> entry : someDistances.entrySet()) {
                String tilePos = entry.getKey();
                if (!ButtonHelper.canActivateTile(game, player, game.getTileByPosition(tilePos))) {
                    continue;
                }
                if (distances.get(tilePos) == null && entry.getValue() != null) {
                    distances.put(tilePos, entry.getValue());
                } else {
                    if (distances.get(tilePos) != null
                            && entry.getValue() != null
                            && distances.get(tilePos) > entry.getValue()) {
                        distances.put(tilePos, entry.getValue());
                    }
                }
            }
        }
        return distances;
    }

    public static List<String> getAllTilesACertainDistanceAway(
            Game game, Player player, Map<String, Integer> distances, int target) {
        List<String> tiles = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : distances.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == target) {
                tiles.add(entry.getKey());
            }
        }
        Collections.sort(tiles);
        return tiles;
    }

    public static Map<String, Integer> getTileDistances(
            Game game, Player player, String tilePosition, int maxDistance, boolean forMap) {
        Map<String, Integer> distances = new HashMap<>();
        distances.put(tilePosition, 0);
        Tile tile2 = game.getTileByPosition(tilePosition);
        for (int i = 1; i <= maxDistance; i++) {
            Map<String, Integer> distancesCopy = new HashMap<>(distances);
            for (String existingPosition : distancesCopy.keySet()) {
                Tile tile = game.getTileByPosition(existingPosition);
                int num = 0;
                int distance = i;
                if (!existingPosition.equalsIgnoreCase(tilePosition)) {
                    if (tile == null
                            || (tile.isNebula()
                                    && player != null
                                    && !player.hasAbility("celestial_being")
                                    && !player.getRelics().contains("circletofthevoid")
                                    && !player.hasAbility("voidborn")
                                    && !ButtonHelper.doesPlayerHaveFSHere("pinktf_flagship", player, tile2)
                                    && !ButtonHelper.isLawInPlay(game, "shared_research"))
                            || (tile.isSupernova()
                                    && player != null
                                    && !player.hasAbility("celestial_being")
                                    && !player.getRelics().contains("circletofthevoid")
                                    && !ButtonHelper.doesPlayerHaveFSHere("pinktf_flagship", player, tile2)
                                    && !player.hasAbility("gashlai_physiology")
                                    && !player.hasTech("tf-mr"))
                            || (player != null
                                    && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                                    && !player.hasTech("lwd")
                                    && !player.hasTech("absol_lwd")
                                    && tile2 != null
                                    && !ButtonHelper.doesPlayerHaveFSHere("yssaril_flagship", player, tile2))
                            || (player != null
                                    && FoWHelper.otherPlayersHaveMovementBlockersInSystem(player, tile, game))
                            || (tile.isAsteroidField()
                                    && player != null
                                    && !player.hasAbility("celestial_being")
                                    && !player.hasTech("amd")
                                    && !player.hasTech("wavelength")
                                    && !player.getRelics().contains("circletofthevoid")
                                    && !player.hasTech("absol_amd")
                                    && !ButtonHelper.doesPlayerHaveFSHere("pinktf_flagship", player, tile2))) {
                        continue;
                    }
                }
                if (!forMap) {
                    if (tile != null && tile.isGravityRift(game)) {
                        num = -1;
                        if (game.isCosmicPhenomenaeMode()) {
                            num = -2;
                        }
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

    private static void addAdjacentPositionsIfNotThereYet(
            Game game, String position, Map<String, Integer> distances, Player player, int distance) {
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
