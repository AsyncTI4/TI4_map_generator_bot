package ti4.generator;

import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.StringTokenizer;

//Handles positions of map
public class PositionMapper {

    private static final Properties positionTileMap6Player = new Properties();
    private static final Properties unitPositions = new Properties();
    private static final Properties planetPositions = new Properties();

    public static void init() {
        readData("6player.properties", positionTileMap6Player, "Could not read position file");
        readData("unit_position.properties", unitPositions, "Could not read unit position file");
        readData("planet.properties", planetPositions, "Could not read planet position file");
    }

    public static String getTilePlanetPositions(String tileID) {
        return (String) planetPositions.get(tileID);
    }

    private static void readData(String fileName, Properties positionMap, String errorMessage) {
        String positionFile = ResourceHelper.getInstance().getPositionFile(fileName);
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                positionMap.load(input);
            } catch (IOException e) {
                LoggerHandler.log(errorMessage, e);
            }
        }
    }

    public static boolean isTilePositionValid(String position){
        return positionTileMap6Player.getProperty(position) != null;
    }

    @CheckForNull
    public static Point getTilePosition(String position) {
        return getPosition(position, positionTileMap6Player);
    }

    @CheckForNull
    public static Point getUnitPosition(String position) {
        return getPosition(position, unitPositions);
    }

    @Nullable
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
                LoggerHandler.log("Could not parse position coordinates", e);
            }
        }
        return null;
    }
}
