package ti4.generator;

import ti4.ResourceHelper;
import ti4.map.Map;
import ti4.message.BotLogger;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;

//Handles positions of map
public class PositionMapper {

    private static final Properties positionTileMap6Player = new Properties();
    private static final Properties positionTileMap8Player = new Properties();
    private static final Properties planetPositions = new Properties();
    private static final Properties spaceTokenPositions = new Properties();
    private static final Properties planetTokenPositions = new Properties();

    private static final Properties tileType = new Properties();
    private static final HashMap<String, String> tileTypeList = new HashMap<>();
    private static final Properties shipPosition = new Properties();

    private static final Properties playerInfo = new Properties();
    private static final Properties playerInfo8 = new Properties();
    private static final Properties reinforcements = new Properties();

    public static void init() {
        readData("6player.properties", positionTileMap6Player, "Could not read position file");
        readData("8player.properties", positionTileMap8Player, "Could not read position file");
        readData("planet.properties", planetPositions, "Could not read planet position file");
        readData("ship_position_tilesbytype.properties", tileType, "Could not read tile type file");
        readData("ship_position.properties", shipPosition, "Could not read ship position file");
        readData("space_token.properties", spaceTokenPositions, "Could not read space token position file");
        readData("planet_token.properties", planetTokenPositions, "Could not read planet token position file");
        readData("6player_info.properties", playerInfo, "Could not read player info position file");
        readData("8player_info.properties", playerInfo8, "Could not read player info position file");
        readData("reinforcements.properties", reinforcements, "Could not read reinforcements position file");
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
                BotLogger.log("Could not parse position");
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

    public static boolean isTilePositionValid(String position, Map userActiveMap) {
        if (userActiveMap != null && userActiveMap.getPlayerCountForMap() == 8) {
            return positionTileMap8Player.getProperty(position) != null;
        }
        return positionTileMap6Player.getProperty(position) != null;
    }

    @CheckForNull
    public static Point getTilePosition(String position, Map map) {
        if (map != null && map.getPlayerCountForMap() == 8) {
            return getPosition(position, positionTileMap8Player);
        }
        return getPosition(position, positionTileMap6Player);
    }

    private static Point getPosition(String position, Properties positionTileMap6Player) {
        String value = positionTileMap6Player.getProperty(position);
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
                BotLogger.log("Could not parse position coordinates");
            }
        }
        return null;
    }

    public static ArrayList<Point> getPlayerPosition(int playerPosition, Map map) {
        ArrayList<Point> positions = new ArrayList<>();
        String info;
        if (map != null && map.getPlayerCountForMap() == 8) {
            info = (String) playerInfo8.get(Integer.toString(playerPosition));
        } else {
            info = (String) playerInfo.get(Integer.toString(playerPosition));
        }
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
            BotLogger.log("Could not parse player positions");
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

}
