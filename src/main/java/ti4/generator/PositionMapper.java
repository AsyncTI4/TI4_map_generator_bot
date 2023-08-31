package ti4.generator;

import org.apache.commons.lang3.SerializationUtils;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.Helper;
import ti4.message.BotLogger;
import ti4.model.PlanetModel;
import ti4.model.ShipPositionModel;
import ti4.model.TileModel;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

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
                BotLogger.log(errorMessage);
            }
        }
    }

    public static java.util.Map<String, Point> getTilePlanetPositions(String tileID) {
        if (tileID.equals("nombox")) {
            return null;
        }
        return TileHelper.getAllPlanets().values().stream()
                .filter(planetModel -> Optional.ofNullable(planetModel.getTileId()).isPresent())
                .filter(planetModel -> planetModel.getTileId().equals(tileID))
                .collect(Collectors.toMap(PlanetModel::getId, PlanetModel::getPositionInTile));
    }

    public static List<Point> getSpaceTokenPositions(String tileID) {
        List<Point> backup = List.of(new Point(190, 30), new Point(215, 110), new Point(185,205),
                new Point(100, 190), new Point(60, 130));
        TileModel tile = TileHelper.getAllTiles().get(tileID);

        return Optional.ofNullable(tile.getSpaceTokenLocations())
                .orElse(Optional.ofNullable(tile.getShipPositionsType()).isPresent()
                        ?
                        Optional.ofNullable(tile.getShipPositionsType().getSpaceTokenLayout())
                                .orElse(backup)
                        : backup);
    }

    public static boolean isTilePositionValid(String position) {
        return tileImageCoordinates.getProperty(position) != null;
    }

    public static HashSet<String> getTilePositions() {
        HashSet<String> positions = new HashSet<>();
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
                BotLogger.log("Could not parse position coordinates", e);
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
            BotLogger.log("Could not parse player positions", e);
        }
        return point;
    }

    public static ArrayList<Point> getPlayerPosition(int playerPosition) {
        ArrayList<Point> positions = new ArrayList<>();
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
            BotLogger.log("Could not parse player positions", e);
        }

        return positions;
    }


    public static UnitTokenPosition getPlanetTokenPosition(String planetName) {
        if(planetName.equals("space"))
            return null;
        UnitTokenPosition pos = TileHelper.getAllPlanets().get(planetName).getUnitPositions();
        return SerializationUtils.clone(pos);
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
        return Optional.ofNullable(TileHelper.getAllTiles().get(tileId).getShipPositionsType())
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

    /**
     * @param tileID
     * @return List of tiles adjacent to tileID in clockwise compass order: [N, NE, SE, S, SW, NW]
     */
    public static List<String> getAdjacentTilePositions(String tileID) {
        String property = tileAdjacencies.getProperty(tileID);
        if (property == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(property.split(",")).toList();
    }

    //Below is an attempt to rewrite the above method to allow for ring count up to 16 without needing to specify adjacencies within .prop file - currently stuck on ensuring the adjacencies follow the correct order of [N, NE, SE, S, SW, NW]
    public static List<String> getAdjacentTilePositionsNew(String tileID) {
        List<String> adjacentTiles = new ArrayList<>();
        if (!Helper.isInteger(tileID)) return adjacentTiles;

        int ring = Integer.parseInt(tileID) / 100;
        if (ring == 0) return List.of("101", "102", "103", "104", "105", "106");
        int tileNumber = Integer.parseInt(tileID) % 100;
        int side = getRingSideNumberOfTileID(tileID);
        int position = getPositionWithinHexSide(tileID);

        if (isStartOfHexRing(tileID)) {
            adjacentTiles.add(tileID + 100); //N
            adjacentTiles.add(tileID + 101); //NE
            adjacentTiles.add(tileID + 1); //SE
            adjacentTiles.add(tileID + -100); //S
            adjacentTiles.add(getEndOfHexRing(tileID)); //SW
            adjacentTiles.add(getEndOfHexRing(tileID + 100)); //NW
        } else if (isEndOfHexRing(tileID)) {
            adjacentTiles.add(getEndOfHexRing(tileID + 100)); //N
            adjacentTiles.add(getTileIDAtPositionInRingSide(ring, 1, 1)); //NE
            adjacentTiles.add(getTileIDAtPositionInRingSide(ring - 1, 1, 1)); //SE
            adjacentTiles.add(tileID + -1); //S
            adjacentTiles.add(getOutsideRingSamePosition(ring, side, position)); //SW
            adjacentTiles.add(getOutsideRingSamePosition(ring, side, position)); //NW
        } else if (isCornerOfHexRing(tileID)) {

        } else {
            switch (side) {
                case 1 -> {
                    adjacentTiles.add(getOutsideRingSamePosition(ring, side, position)); //N
                    adjacentTiles.add(getOutsideRingOnePositionForward(ring, side, position)); //NE
                    adjacentTiles.add(getSameRingOnePositionForward(ring, side, position)); //SE
                    adjacentTiles.add(getInsideRingOnePositionBack(ring, side, position)); //S
                    adjacentTiles.add(getInsideRingSamePosition(ring, side, position)); //SW
                    adjacentTiles.add(getSameRingOnePositionBack(ring, side, position)); //NW
                }
                case 2 -> {}
                case 3 -> {}
                case 4 -> {}
                case 5 -> {}
                case 6 -> {}
            }
        }

        // adjacentTiles.addAll(getAdjacentTilePositionsWithinRing(tileID));

        // if (isCornerOfHexRing(tileID)) {
        //     //Inside Corner
        //     adjacentTiles.add(getCornerPositionOfHexRing(ringNumber - 1, getSideNumberOfHexRing(tileID)));

        //     //Outside Corner
        //     String outsideCornerTileID = getCornerPositionOfHexRing(ringNumber + 1, getSideNumberOfHexRing(tileID));
        //     adjacentTiles.add(outsideCornerTileID);
        //     adjacentTiles.addAll(getAdjacentTilePositionsWithinRing(outsideCornerTileID));
        // } else {
        //     int currentPositionWithinSide = getPositionWithinHexSide(tileID);
        //     int currentSideNumber = getSideNumberOfHexRing(tileID);
        //     adjacentTiles.add(getInsideRingOnePositionBack(ringNumber, currentPositionWithinSide, currentSideNumber));
        //     adjacentTiles.add(getInsideRingSamePosition(ringNumber, currentPositionWithinSide, currentSideNumber));
        //     adjacentTiles.add(getOutsideRingSamePosition(ringNumber, currentPositionWithinSide, currentSideNumber));
        //     adjacentTiles.add(getOutsideRingOnePositionForward(ringNumber, currentPositionWithinSide, currentSideNumber));
        // }

        return adjacentTiles;
    }

    private static String getOutsideRingOnePositionForward(int ring, int side, int position) {
        return getTileIDAtPositionInRingSide(ring + 1, side, position + 1);
    }

    private static String getOutsideRingSamePosition(int ring, int side, int position) {
        return getTileIDAtPositionInRingSide(ring + 1, side, position);
    }

    private static String getInsideRingSamePosition(int ring, int side, int position) {
        return getTileIDAtPositionInRingSide(ring - 1, side, position);
    }

    private static String getInsideRingOnePositionBack(int ring, int side, int position) {
        return getTileIDAtPositionInRingSide(ring - 1, side, position - 1);
    }

    private static String getSameRingOnePositionForward(int ring, int side, int position) {
        int maxRingTileCount = getMaxTilesInRing(ring);
        return getTileIDAtPositionInRingSide(ring, side, position + 1);
    }

    private static String getSameRingOnePositionBack(int ring, int side, int position) {
        return getTileIDAtPositionInRingSide(ring, side, position - 1);
    }

    private static List<String> getAdjacentTilePositionsWithinRing(String tileID) {
        List<String> adjacentTiles = new ArrayList<>();
        if (!Helper.isInteger(tileID)) return adjacentTiles;

        int ringNumber = Integer.parseInt(tileID) / 100;
        int tileNumber = Integer.parseInt(tileID) % 100;
        int maxRingTileCount = getMaxTilesInRing(ringNumber);

        if (isEndOfHexRing(tileID)) { //last one in ring
            adjacentTiles.add(ringNumber + "01");
            adjacentTiles.add(ringNumber + String.format("%02d", maxRingTileCount - 1));
        } else if (isStartOfHexRing(tileID)) { //first one in ring
            adjacentTiles.add(ringNumber + String.format("%02d", maxRingTileCount));
            adjacentTiles.add(ringNumber + String.format("%02d", tileNumber + 1));
        } else {
            adjacentTiles.add(ringNumber + String.format("%02d", tileNumber - 1));
            adjacentTiles.add(ringNumber + String.format("%02d", tileNumber + 1));
        }
        return adjacentTiles;
    }

    private static String getTileFromRingTileNumber(int ring, int ringTileNumber) {
        return ring + String.format("%02d", ringTileNumber);
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

    private static String getEndOfHexRing(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ring = Integer.parseInt(tileID) / 100;
        int maxRingTileCount = getMaxTilesInRing(ring);
        return ring + String.format("%02d", maxRingTileCount);
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
}
