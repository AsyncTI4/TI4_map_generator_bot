package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.LoggerHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TilesMapper {
    private static final Properties tiles = new Properties();

    public static void init() {
        String positionFile = ResourceHelper.getInstance().getInfoFile("tiles.properties");
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                tiles.load(input);
            } catch (IOException e) {
                LoggerHandler.log("Could not read tiles name file", e);
            }
        }
    }

    public static String getTileName(String tileID) {
        return tiles.getProperty(tileID);
    }

    public static String getTilesList()
    {
        return  "Tiles: " + tiles.values().stream()
                .sorted()
                .filter(value -> value instanceof String)
                .map(value -> (String) value)
                .collect(Collectors.joining(", "));
    }
}
