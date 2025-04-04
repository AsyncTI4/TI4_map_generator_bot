package ti4.image;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.model.PlanetModel;
import ti4.model.ShipPositionModel;
import ti4.model.ShipPositionModel.ShipPosition;
import ti4.model.TileModel;

//Handles positions of map
public class PositionMapper {
    private static final Properties tileImageCoordinates = new Properties();
    private static final Properties playerInfo = new Properties();
    private static final Properties playerInfo8 = new Properties();
    private static final Properties playerInfo8ring = new Properties();
    private static final Properties stats = new Properties();
    private static final Properties reinforcements = new Properties();

    private static final Properties tileAdjacencies = new Properties();

    public static void init() {
        readData("tileImageCoordinates.properties", tileImageCoordinates, "Could not read position file");
        readData("6player_info.properties", playerInfo, "Could not read player info position file");
        readData("8player_info.properties", playerInfo8, "Could not read player info position file");
        readData("8ring_info.properties", playerInfo8ring, "Could not read player info position file");
        readData("stats.properties", stats, "Could not read player info position file");
        readData("reinforcements.properties", reinforcements, "Could not read reinforcements position file");
        readData("tileAdjacencies.properties", tileAdjacencies, "Could not read adjacent tiles file");
    }

    private static void readData(String fileName, Properties positionMap, String errorMessage) {
        String positionFile = ResourceHelper.getInstance().getPositionFile(fileName);
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                positionMap.load(input);
            } catch (IOException e) {
                BotLogger.error(errorMessage, e);
            }
        }
    }

    public static Map<String, Point> getTilePlanetPositions(String tileID) {
        if ("nombox".equals(tileID)) {
            return null;
        }
        var tileIdToPlanets = TileHelper.getPlanetsByTileId(tileID);
        return tileIdToPlanets == null ? null : tileIdToPlanets.stream().collect(Collectors.toMap(PlanetModel::getId, PlanetModel::getPositionInTile));
    }

    public static List<Point> getSpaceTokenPositions(String tileID) {
        List<Point> backup = List.of(new Point(190, 30), new Point(215, 110), new Point(185, 205), new Point(100, 190), new Point(60, 130));
        TileModel tile = TileHelper.getTileById(tileID);

        return Optional.ofNullable(tile.getShipPositionsType()).map(ShipPosition::getSpaceTokenLayout).orElse(backup);
    }

    public static boolean isTilePositionValid(String position) {
        return tileImageCoordinates.getProperty(position) != null;
    }

    public static Set<String> getTilePositions() {
        Set<String> positions = new HashSet<>();
        for (Object key : tileImageCoordinates.keySet()) {
            if (key instanceof String position) {
                positions.add(position);
            }
        }
        return positions;
    }

    @Nullable
    public static Point getTilePosition(String position) {
        return getPosition(position, tileImageCoordinates);
    }

    private static Point getPosition(String position, Properties positionTileMap) {
        if (position == null) return null;
        String value = positionTileMap.getProperty(position);
        return getPoint(value);
    }

    public static Point getPoint(String value) {
        if (value != null) {
            StringTokenizer tokenizer = new StringTokenizer(value, ",");
            try {
                int x = Integer.parseInt(tokenizer.nextToken());
                int y = Integer.parseInt(tokenizer.nextToken());
                return new Point(x, y);
            } catch (Exception e) {
                BotLogger.error("Could not parse position coordinates", e);
            }
        }
        return null;
    }

    public static Point getPlayerStats(String id) {
        Point point = new Point();
        String info = (String) stats.get(id);
        if (info == null) {
            return new Point(0, 0);
        }

        try {
            StringTokenizer individualPoints = new StringTokenizer(info, ",");
            String x = individualPoints.nextToken();
            String y = individualPoints.nextToken();
            point = new Point(Integer.parseInt(x), Integer.parseInt(y));
        } catch (Exception e) {
            BotLogger.error("Could not parse player positions", e);
        }
        return point;
    }

    public static List<Point> getPlayerPosition(int playerPosition) {
        List<Point> positions = new ArrayList<>();
        String info = (String) playerInfo8ring.get(Integer.toString(playerPosition));
        if (info == null) {
            return positions;
        }

        try {
            StringTokenizer pointInfo = new StringTokenizer(info, ";");
            while (pointInfo.hasMoreTokens()) {
                StringTokenizer individualPoints = new StringTokenizer(pointInfo.nextToken(), ",");
                String x = individualPoints.nextToken();
                String y = individualPoints.nextToken();
                positions.add(new Point(Integer.parseInt(x), Integer.parseInt(y)));
            }
        } catch (Exception e) {
            BotLogger.error("Could not parse player positions", e);
        }

        return positions;
    }

    public static UnitTokenPosition getPlanetTokenPosition(String planetName) {
        if ("space".equals(planetName))
            return null;

        PlanetModel planet = TileHelper.getPlanetById(planetName);
        if (planet.getUnitPositions() != null) {
            return SerializationUtils.clone(planet.getUnitPositions());
        }
        if (planet.getPlanetLayout() != null) {
            return planet.getPlanetLayout().generateUnitTokenPosition();
        }
        return null;
    }

    public static UnitTokenPosition getReinforcementsPosition(String unitId) {
        Object value = reinforcements.get(unitId);
        if (value == null) {
            return null;
        }
        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(unitId);
        String valuePosition = (String) value;
        StringTokenizer tokenizer = new StringTokenizer(valuePosition, ";");
        while (tokenizer.hasMoreTokens()) {
            String nextPoint = tokenizer.nextToken();
            StringTokenizer positionTokenizer = new StringTokenizer(nextPoint, ",");
            if (positionTokenizer.countTokens() == 2) {
                int x = Integer.parseInt(positionTokenizer.nextToken());
                int y = Integer.parseInt(positionTokenizer.nextToken());
                Point point = new Point(x, y);
                unitTokenPosition.addPosition(unitId, point);
            }
        }
        return unitTokenPosition;
    }

    public static Point getUnitOffset() {
        return new ShipPositionModel().getOffset();
    }

    public static Point getAllianceUnitOffset() {
        return new ShipPositionModel().getAllianceOffset();
    }

    public static String getTileSpaceUnitLayout(String tileId) {
        return Optional.ofNullable(TileHelper.getTileById(tileId).getShipPositionsType())
            .orElse(ShipPositionModel.ShipPosition.TYPE08).getPositions();
    }

    public static UnitTokenPosition getSpaceUnitPosition(String planetName, String tileID) {
        String shipPositionString = getTileSpaceUnitLayout(tileID);

        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(planetName, false);
        StringTokenizer tokenizer = new StringTokenizer(shipPositionString, ";");
        while (tokenizer.hasMoreTokens()) {
            String positionTemp = tokenizer.nextToken();
            String position = positionTemp.stripLeading();
            if (!position.isEmpty()) {
                StringTokenizer tokenPositionTokenizer = new StringTokenizer(position, " ");
                if (tokenPositionTokenizer.countTokens() == 2) {
                    String id = tokenPositionTokenizer.nextToken();
                    String pointValue = tokenPositionTokenizer.nextToken();
                    StringTokenizer positionTokenizer = new StringTokenizer(pointValue, ",");
                    if (positionTokenizer.countTokens() == 2) {
                        int x = Integer.parseInt(positionTokenizer.nextToken());
                        int y = Integer.parseInt(positionTokenizer.nextToken());
                        Point point = new Point(x, y);
                        unitTokenPosition.addPosition(id, point);
                    }
                }
            }
        }
        return unitTokenPosition;
    }

    public static List<String> getPositionsInRing(String ringNumOrCorners, @Nullable Game game) {
        List<String> positions = new ArrayList<>();
        if (ringNumOrCorners.equals("corners")) {
            positions.addAll(List.of("tl", "tr", "bl", "br"));
        } else {
            try {
                int ring = Integer.parseInt(ringNumOrCorners);
                int totalTiles = Math.max(6 * ring, 1);
                for (int x = 1; x <= totalTiles; x++) {
                    String pos = makeTileStr(ring, x);
                    if (isTilePositionValid(pos))
                        positions.add(pos);
                }
            } catch (Exception e) {
                return positions;
            }
        }
        if (game != null) positions.removeIf(pos -> game.getTileByPosition(pos) == null);
        return positions;
    }

    /**
     * @return List of tiles adjacent to position in clockwise compass order: [N, NE, SE, S, SW, NW]
     */
    public static List<String> getAdjacentTilePositions(String position) {
        String property = tileAdjacencies.getProperty(position);
        if (property == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(property.split(",")).toList();
    }

    // Below is JAZZ's attempt to rewrite the rewrite mentioned below. Adjacencies should follow the correct order (verification needed)
    // Below is an attempt to rewrite the above method to allow for ring count up to 16 without needing to specify adjacencies within .prop file - currently stuck on ensuring the adjacencies follow the correct order of [N, NE, SE, S, SW, NW]
    public static List<String> getAdjacentTilePositionsNew(String position) {
        List<String> adjacentTiles = new ArrayList<>();
        if (!Helper.isInteger(position)) return adjacentTiles;
        if (position.equals("000")) return List.of("101", "102", "103", "104", "105", "106");

        int ring = Integer.parseInt(position) / 100;
        int tile = Integer.parseInt(position) % 100;
        int side = (tile - 1) / ring; // math
        boolean isCorner = ((tile - 1) % ring) == 0;

        // Define the different relevant spots
        String nextRing1 = makeTileStr(ring + 1, tile + side);
        String nextRing2 = makeTileStr(ring + 1, tile + side + 1);
        String sameRingNext = makeTileStr(ring, tile + 1);
        String prevRing1 = makeTileStr(ring - 1, tile - side);
        String prevRing2 = makeTileStr(ring - 1, tile - side - 1); // this tile is not used for "corner tiles"
        String sameRingPrev = makeTileStr(ring, tile - 1);
        String nextRing3 = makeTileStr(ring + 1, tile + side - 1); // and THIS tile is ONLY used for "corner tiles"

        // First 4 tiles are the same for corner/non-corner
        List<String> ordering = new ArrayList<>(List.of(nextRing1, nextRing2, sameRingNext, prevRing1));
        if (!isCorner) ordering.addAll(List.of(prevRing2, sameRingPrev));
        if (isCorner) ordering.addAll(List.of(sameRingPrev, nextRing3));

        // This ordering is essentially "go out from mecatol, then go clockwise"
        // So we need to rotate it backwards based on that initial direction
        Collections.rotate(ordering, -1 * side);
        return new ArrayList<>(ordering.stream().map(pos -> isTilePositionValid(pos) ? pos : "x").toList());
    }

    // tileNum will be modulused to within the bounds of the ring (e.g. "tile 7" in ring 1 will become "tile 1")
    private static String makeTileStr(int ring, int tileNum) {
        tileNum = ((tileNum - 1) % (ring * 6)) + 1;
        ring = Math.abs(ring);
        String ringStr = Integer.toString(ring);
        String tileStr = "00" + (ring == 0 ? "00" : tileNum);
        return ringStr + tileStr.substring(tileStr.length() - 2);
    }

    public static Boolean isCornerOfHexRing(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ring = Integer.parseInt(tileID) / 100;
        for (int corner = 1; corner <= 6; corner++) {
            if (tileID.equals(getTileIDAtCornerPositionOfRing(ring, corner))) return true;
        }
        return false;
    }

    public static Boolean isStartOfHexRing(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        return Integer.parseInt(tileID) % 100 == 1;
    }

    public static Boolean isEndOfHexRing(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ring = Integer.parseInt(tileID) / 100;
        int tileNumber = Integer.parseInt(tileID) % 100;
        int maxRingTileCount = getMaxTilesInRing(ring);
        return tileNumber == maxRingTileCount;
    }

    private static int getMaxTilesInRing(int ring) {
        return ring * 6;
    }

    public static Integer getPositionWithinHexSide(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ring = Integer.parseInt(tileID) / 100;
        int tileNumber = Integer.parseInt(tileID) % 100;
        for (int corner = 1; corner <= 6; corner++) {
            int upperBound = 1 + ring * (corner);
            int lowerBound = 1 + ring * (corner - 1);
            if (tileNumber >= lowerBound && tileNumber < upperBound) return tileNumber - lowerBound;
        }
        return null;
    }

    public static String getTileIDAtPositionInRingSide(Integer ring, Integer side, Integer position) {
        if (ring == null || side == null || position == null) return null;
        return ring + String.format("%02d", 1 + ring * (side - 1) + position);
    }

    @Nullable
    public static Integer getRingSideNumberOfTileID(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ringNumber = Integer.parseInt(tileID) / 100;
        int tileNumber = Integer.parseInt(tileID) % 100;
        for (int corner = 1; corner <= 6; corner++) {
            int upperBound = 1 + ringNumber * (corner);
            int lowerBound = 1 + ringNumber * (corner - 1);
            if (tileNumber >= lowerBound && tileNumber < upperBound) return corner;
        }
        return null;
    }

    public static String getPositionOfTileOneRingLarger(String tileID) {
        int ring = Integer.parseInt(tileID) / 100;
        return getTileIDAtPositionInRingSide(ring + 1, getRingSideNumberOfTileID(tileID), getPositionWithinHexSide(tileID));
    }

    public static String getEquivalentPositionAtRing(int ring, String tileID) {
        return getTileIDAtPositionInRingSide(ring, getRingSideNumberOfTileID(tileID), getPositionWithinHexSide(tileID));
    }

    public static String getTileIDAtCornerPositionOfRing(int ring, int cornerNumber) {
        return ring + String.format("%02d", 1 + ring * (cornerNumber - 1));
    }

    public static int getLeftMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
            .mapToInt(pos -> {
                if (!Helper.isInteger(pos)) return 2080;
                Point p = PositionMapper.getTilePosition(pos);
                return (p != null ? p.x : 2080);
            })
            .min()
            .orElse(0);
    }

    public static int getRightMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
            .mapToInt(pos -> {
                if (!Helper.isInteger(pos)) return 2080;
                Point p = PositionMapper.getTilePosition(pos);
                return (p != null ? p.x : 2080);
            })
            .max()
            .orElse(0);
    }

    public static int getTopMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
            .mapToInt(pos -> {
                if (!Helper.isInteger(pos)) return 2550;
                Point p = PositionMapper.getTilePosition(pos);
                return (p != null ? p.y : 2550);
            })
            .min()
            .orElse(0);
    }

    public static int getBottomMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
            .mapToInt(pos -> {
                if (!Helper.isInteger(pos)) return 2550;
                Point p = PositionMapper.getTilePosition(pos);
                return (p != null ? p.y : 2550);
            })
            .max()
            .orElse(0);
    }
}
