package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.LoggerHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

public class Mapper {
    private static final Properties tiles = new Properties();
    private static final Properties units = new Properties();
    private static final Properties colors = new Properties();
    private static final Properties cc_tokens = new Properties();

    public static void init() {
        readData("tiles.properties", tiles, "Could not read tiles name file");
        readData("units.properties", units, "Could not read unit name file");
        readData("color.properties", colors, "Could not read color name file");
        readData("cc_tokens.properties", cc_tokens, "Could not read token name file");
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

    public static String getColorID(String color){
        return colors.getProperty(color);
    }

    public static String getTileID(String tileID) {
        return tiles.getProperty(tileID);
    }

    public static boolean isUnitIDValid(String unitID) {
        return units.getProperty(unitID) != null;
    }

    public static String getUnitID(String unitID, String color) {
        String property = colors.getProperty(color);
        return property + units.getProperty(unitID);
    }

    public static String getCCID(String color) {
        String property = colors.getProperty(color);
        return cc_tokens.get("cc") + property + ".png";
    }

    public static String getControlID(String color) {
        String property = colors.getProperty(color);
        return cc_tokens.get("control") + property + ".png";
    }

    public static List<String> getColors() {
        return colors.keySet().stream().filter(color -> color instanceof String)
                .map(color -> (String) color)
                .sorted()
                .collect(Collectors.toList());
    }

    public static String getTilesList()
    {
        return  "Tiles: " +  AliasHandler.getUnitList().stream()
                .sorted()
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n "));
    }
    public static String getPlanetList()
    {
        return  "Planets: " + AliasHandler.getPlanetList().stream()
                .sorted()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    public static String getUnitList()
    {
        return  "Units: " +  AliasHandler.getUnitList().stream()
                .sorted()
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n "));
    }
}
