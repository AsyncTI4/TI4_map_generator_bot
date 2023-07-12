package ti4.generator;

import org.apache.commons.lang3.SerializationUtils;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.map.Map;
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

    private static final Properties positionTileMap6Player = new Properties();
    private static final Properties positionTileMap8Player = new Properties();
    private static final Properties positionTileMap8Ring = new Properties();
    private static final Properties playerInfo = new Properties();
    private static final Properties playerInfo8 = new Properties();
    private static final Properties playerInfo8ring = new Properties();
    private static final Properties stats = new Properties();
    private static final Properties reinforcements = new Properties();

    private static final Properties adjacent8RingTiles = new Properties();


    public static void init() {
        readData("6player.properties", positionTileMap6Player, "Could not read position file");
        readData("8player.properties", positionTileMap8Player, "Could not read position file");
        readData("8ring.properties", positionTileMap8Ring, "Could not read position file");
        readData("6player_info.properties", playerInfo, "Could not read player info position file");
        readData("8player_info.properties", playerInfo8, "Could not read player info position file");
        readData("8ring_info.properties", playerInfo8ring, "Could not read player info position file");
        readData("stats.properties", stats, "Could not read player info position file");
        readData("reinforcements.properties", reinforcements, "Could not read reinforcements position file");
        readData("adjacent8ring.properties", adjacent8RingTiles, "Could not read adjacent tiles file");
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
        return positionTileMap8Ring.getProperty(position) != null;
    }

    public static HashSet<String> get8RingTiles() {
        HashSet<String> positions = new HashSet<>();
        for (Object key : positionTileMap8Ring.keySet()) {
            if (key instanceof String position) {
                positions.add(position);
            }
        }
        return positions;
    }

    @Nullable
    public static Point getTilePosition(String position) {
        return getPosition(position, positionTileMap8Ring);
    }

    private static Point getPosition(String position, Properties positionTileMap) {
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

    public static List<String> getAdjacentTilePositions(Map activeMap, String tileID) {
        String property = adjacent8RingTiles.getProperty(tileID);
        if (property == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(property.split(",")).toList();
    }
}
