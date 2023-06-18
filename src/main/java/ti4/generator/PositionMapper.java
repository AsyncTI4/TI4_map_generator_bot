package ti4.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final java.util.Map<String, TileModel> allTilesMap = new HashMap<>();
    private static final java.util.Map<String, PlanetModel> allPlanetsMap = new HashMap<>();

    public static void init() {
        readData("6player.properties", positionTileMap6Player, "Could not read position file");
        readData("8player.properties", positionTileMap8Player, "Could not read position file");
        readData("8ring.properties", positionTileMap8Ring, "Could not read position file");
        readData("planet.properties", planetPositions, "Could not read planet position file"); //TODO: DELETE
        readData("ship_position_tilesbytype.properties", tileType, "Could not read tile type file"); //TODO: DELETE
        readData("ship_position.properties", shipPosition, "Could not read ship position file"); // TODO: DELETE
        readData("space_token.properties", spaceTokenPositions, "Could not read space token position file"); //TODO: DELETE
        readData("planet_token.properties", planetTokenPositions, "Could not read planet token position file"); //TODO: DELETE
        readData("6player_info.properties", playerInfo, "Could not read player info position file");
        readData("8player_info.properties", playerInfo8, "Could not read player info position file");
        readData("8ring_info.properties", playerInfo8ring, "Could not read player info position file");
        readData("stats.properties", stats, "Could not read player info position file");
        readData("reinforcements.properties", reinforcements, "Could not read reinforcements position file");
//        readData("adjacent.properties", adjacentTiles, "Could not read adjacent tiles file");
        readData("adjacent8ring.properties", adjacent8RingTiles, "Could not read adjacent tiles file");

        readData("migrate.properties", migrate, "Could not read wormholes file");
        readData("migrate8rings.properties", migrate8rings, "Could not read wormholes file");
        jsonInit();
    }

    public static void jsonInit() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<TileModel> allTiles = new ArrayList<>();
        String file = ResourceHelper.getInstance().getTileJsonFile("tiles.json");
        if(Optional.ofNullable(file).isEmpty()) {
            BotLogger.log("Tile JSON is null!");
            return;
        }

        try {
            InputStream input = new FileInputStream(file);
            allTiles = objectMapper.readValue(input, new TypeReference<List<TileModel>>(){});
        } catch (Exception e) {
            BotLogger.log("Could not deserialise tile JSON!");
            System.out.println(e.getMessage());
        }

        allTiles.forEach(
                tileModel -> {
                    allTilesMap.put(tileModel.getId(), tileModel);
                    tileModel.getPlanets().forEach(
                            planetModel -> allPlanetsMap.put(planetModel.getId(), planetModel)
                    );
                }
        );
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

    public static String getMigratePosition(String position) {
        return migrate.getProperty(position);
    }

    public static String getMigrate8RingsPosition(String position) {
        return migrate8rings.getProperty(position);
    }

    public static java.util.Map<String, Point> getTilePlanetPositions(String tileID) {
        return allTilesMap.get(tileID).getPlanets().stream()
                .collect(Collectors.toMap(PlanetModel::getId, PlanetModel::getPositionInTile));
    }

    public static List<Point> getSpaceTokenPositions(String tileID) {
        return allTilesMap.get(tileID).getSpaceTokenLocations();
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
        return allPlanetsMap.get(planetName).getUnitPositions();
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
        return allTilesMap.get(tileId).getShipPositionsType().getPositions();
    }

    public static UnitTokenPosition getSpaceUnitPosition(String planetName, String tileID) {
        String shipPositionString = allTilesMap.get(tileID).getShipPositionsType().getPositions();

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

    public static java.util.Map<String, TileModel> getAllTiles() {
        return allTilesMap;
    }

    public static java.util.Map<String, PlanetModel> getAllPlanets() {
        return allPlanetsMap;
    }
}
