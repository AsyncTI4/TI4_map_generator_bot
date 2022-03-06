package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.LoggerHandler;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.stream.Collectors;

public class Mapper {
    private static final Properties tiles = new Properties();
    private static final Properties units = new Properties();
    private static final Properties colors = new Properties();

    public static void init() {
        readData("tiles.properties", tiles, "Could not read tiles name file");
        readData("units.properties", units, "Could not read unit name file");
        readData("color.properties", colors, "Could not read color name file");
    }

    private static void readData(String propertyFileName, Properties colors, String s) {
        String colorFile = ResourceHelper.getInstance().getInfoFile(propertyFileName);
        if (colorFile != null) {
            try (InputStream input = new FileInputStream(colorFile)) {
                colors.load(input);
            } catch (IOException e) {
                LoggerHandler.log(s, e);
            }
        }
    }

    public static boolean isColorValid(String color){
        return colors.getProperty(color) != null;
    }

    public static String getTileID(String tileID) {
        return tiles.getProperty(tileID);
    }

    public static String getUnitID(String unitID, String color) {
        String property = colors.getProperty(color);
        return property + units.getProperty(unitID);
    }

    public static String getTilesList()
    {
        return  "Tiles: " + tiles.values().stream()
                .sorted()
                .filter(value -> value instanceof String)
                .map(value -> (String) value)
                .collect(Collectors.joining(", "));
    }

    public static String getUnitList()
    {
        return  "Units: " + units.values().stream()
                .sorted()
                .filter(value -> value instanceof String)
                .map(value -> (String) value)
                .collect(Collectors.joining(", "));
    }
}
