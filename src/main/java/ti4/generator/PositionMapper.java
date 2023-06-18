package ti4.generator;

import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.map.Map;
import ti4.message.BotLogger;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;

//Handles positions of map
public class PositionMapper {

    private static final Properties positionTileMap6Player = new Properties();
    private static final Properties positionTileMap8Player = new Properties();
    private static final Properties positionTileMap8Ring = new Properties();
    private static final Properties planetPositions = new Properties();
    private static final Properties spaceTokenPositions = new Properties();
    private static final Properties planetTokenPositions = new Properties();

    private static final Properties tileType = new Properties();
    private static final HashMap<String, String> tileTypeList = new HashMap<>();
    private static final Properties shipPosition = new Properties();

    private static final Properties playerInfo = new Properties();
    private static final Properties playerInfo8 = new Properties();
    private static final Properties playerInfo8ring = new Properties();
    private static final Properties stats = new Properties();
    private static final Properties reinforcements = new Properties();

    //    private static final Properties adjacentTiles = new Properties();
    private static final Properties adjacent8RingTiles = new Properties();

    private static final Properties migrate = new Properties();
    private static final Properties migrate8rings = new Properties();

    public static void init() {
        readData("6player.properties", positionTileMap6Player, "Could not read position file");
        readData("8player.properties", positionTileMap8Player, "Could not read position file");
        readData("8ring.properties", positionTileMap8Ring, "Could not read position file");
        readData("planet.properties", planetPositions, "Could not read planet position file");
        readData("ship_position_tilesbytype.properties", tileType, "Could not read tile type file");
        readData("ship_position.properties", shipPosition, "Could not read ship position file");
        readData("space_token.properties", spaceTokenPositions, "Could not read space token position file");
        readData("planet_token.properties", planetTokenPositions, "Could not read planet token position file");
        readData("6player_info.properties", playerInfo, "Could not read player info position file");
        readData("8player_info.properties", playerInfo8, "Could not read player info position file");
        readData("8ring_info.properties", playerInfo8ring, "Could not read player info position file");
        readData("stats.properties", stats, "Could not read player info position file");
        readData("reinforcements.properties", reinforcements, "Could not read reinforcements position file");
//        readData("adjacent.properties", adjacentTiles, "Could not read adjacent tiles file");
        readData("adjacent8ring.properties", adjacent8RingTiles, "Could not read adjacent tiles file");

        readData("migrate.properties", migrate, "Could not read wormholes file");
        readData("migrate8rings.properties", migrate8rings, "Could not read wormholes file");

        //temp code migration
//        java.util.Map<String, String> migratedAdjacency = new LinkedHashMap<>();
//        for (java.util.Map.Entry<Object, Object> entry : adjacent8RingTiles.entrySet()) {
//            String key = (String)entry.getKey();
//            String value = (String)entry.getValue();
//            String newKey = PositionMapper.getMigrate8RingsPosition(key);
//            if (newKey != null){
//                String[] split = value.split(",");
//                String newAdjacentcy = "";
//                for (String splitID : split) {
//                    String newSplitID = PositionMapper.getMigrate8RingsPosition(splitID);
//                    if (newSplitID != null){
//                        newAdjacentcy += "," + newSplitID;
//                    } else {
//                        newAdjacentcy += "," + splitID;
//                        if (!splitID.equals("x")) {
//                            System.out.println("Could not find adjacent coordinates: " + splitID);
//                        }
//                    }
//                }
//                migratedAdjacency.put(newKey, newAdjacentcy);
//            }
//            else {
//                System.out.println("Could not find tile: " + key);
//            }
//        }
//        BufferedWriter writer = null;
//        try {
//            writer = new BufferedWriter(new FileWriter("E:\\DEV_TI4\\aaa.txt"));
//            ArrayList<String> keys = new ArrayList<>(migratedAdjacency.keySet());
//            Collections.sort(keys);
//            for (String key : keys) {
//                writer.write(key+"="+migratedAdjacency.get(key));
//                writer.write(System.lineSeparator());
//            }
//            writer.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        java.util.Map<String, String> positionsNew = new LinkedHashMap<>();
//        for (java.util.Map.Entry<Object, Object> entry : positionTileMap8Ring.entrySet()) {
//            String key = (String)entry.getKey();
//            String value = (String)entry.getValue();
//            String newKey = PositionMapper.getMigrate8RingsPosition(key);
//            if (newKey != null){
//                positionsNew.put(newKey, value);
//            }
//            else {
//                System.out.println("Could not find tile: " + key);
//            }
//        }
////        BufferedWriter writer = null;
//        try {
//            writer = new BufferedWriter(new FileWriter("E:\\DEV_TI4\\bbb.txt"));
//            ArrayList<String> keys = new ArrayList<>(positionsNew.keySet());
//            Collections.sort(keys);
//            for (String key : keys) {
//                writer.write(key+"="+positionsNew.get(key));
//                writer.write(System.lineSeparator());
//            }
//            writer.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

    }

    public static String getMigratePosition(String position) {
        return migrate.getProperty(position);
    }

    public static String getMigrate8RingsPosition(String position) {
        return migrate8rings.getProperty(position);
    }

    public static String getTilePlanetPositions(String tileID) {
        return (String) planetPositions.get(tileID);
    }

    public static ArrayList<Point> getSpaceTokenPositions(String tileID) {
        ArrayList<Point> points = new ArrayList<>();
        String value = (String) spaceTokenPositions.get(tileID);
        if (value == null) {
            return points;
        }
        StringTokenizer tokenizer = new StringTokenizer(value, ";");
        while (tokenizer.hasMoreTokens()) {
            try {
                String positionString = tokenizer.nextToken().replaceAll(" ", "");
                StringTokenizer position = new StringTokenizer(positionString, ",");
                if (position.countTokens() == 2) {
                    int x = Integer.parseInt(position.nextToken());
                    int y = Integer.parseInt(position.nextToken());
                    points.add(new Point(x, y));
                }
            } catch (Exception e) {
                BotLogger.log("Could not parse position", e);
            }
        }
        return points;
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
        Object value = planetTokenPositions.get(planetName);
        if (value == null) {
            return null;
        }
        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(planetName);
        String valuePosition = (String) value;
        StringTokenizer tokenizer = new StringTokenizer(valuePosition, ";");
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
        String offset = (String) shipPosition.get("offset");
        StringTokenizer positionTokenizer = new StringTokenizer(offset, ",");
        if (positionTokenizer.countTokens() == 2) {
            int x = Integer.parseInt(positionTokenizer.nextToken());
            int y = Integer.parseInt(positionTokenizer.nextToken());
            return new Point(x, y);
        }
        return null;
    }

    public static Point getAllianceUnitOffset() {
        String offset = (String) shipPosition.get("alliance_offset");
        StringTokenizer positionTokenizer = new StringTokenizer(offset, ",");
        if (positionTokenizer.countTokens() == 2) {
            int x = Integer.parseInt(positionTokenizer.nextToken());
            int y = Integer.parseInt(positionTokenizer.nextToken());
            return new Point(x, y);
        }
        return null;
    }

    public static String getTileSpaceUnitLayout(String tileId) {
        if (tileTypeList.isEmpty()) {
            for (java.util.Map.Entry<Object, Object> tileTypeEntry : tileType.entrySet()) {
                String tileTypeKey = (String) tileTypeEntry.getKey();
                String values = (String) tileTypeEntry.getValue();
                values = values.replaceAll(" ", "");
                String[] split = values.split(",");
                for (String tileID_ : split) {
                    tileTypeList.put(tileID_, tileTypeKey);
                }
            }
        }
        return tileTypeList.get(tileId);
    }

    public static UnitTokenPosition getSpaceUnitPosition(String planetName, String tileID) {
        if (tileTypeList.isEmpty()) {
            for (java.util.Map.Entry<Object, Object> tileTypeEntry : tileType.entrySet()) {
                String tileTypeKey = (String) tileTypeEntry.getKey();
                String values = (String) tileTypeEntry.getValue();
                values = values.replaceAll(" ", "");
                String[] split = values.split(",");
                for (String tileID_ : split) {
                    tileTypeList.put(tileID_, tileTypeKey);
                }
            }
        }
        String tileType = tileTypeList.get(tileID);
        if (tileType == null) {
            return null;
        }
        Object value = shipPosition.get(tileType);
        if (value == null) {
            return null;
        }
        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(planetName, false);
        String valuePosition = (String) value;
        StringTokenizer tokenizer = new StringTokenizer(valuePosition, ";");
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
