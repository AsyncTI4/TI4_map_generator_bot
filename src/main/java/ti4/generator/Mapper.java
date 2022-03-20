package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.LoggerHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Mapper {
    private static final Properties tiles = new Properties();
    private static final Properties units = new Properties();
    private static final Properties colors = new Properties();
    private static final Properties cc_tokens = new Properties();
    private static final Properties attachment_tokens = new Properties();
    private static final Properties tokens = new Properties();
    private static final Properties special_case = new Properties();
    private static final Properties factions = new Properties();
    private static final Properties general = new Properties();

    public static void init() {
        readData("tiles.properties", tiles, "Could not read tiles name file");
        readData("units.properties", units, "Could not read unit name file");
        readData("color.properties", colors, "Could not read color name file");
        readData("cc_tokens.properties", cc_tokens, "Could not read cc token name file");
        readData("attachments.properties", attachment_tokens, "Could not read attachment token name file");
        readData("tokens.properties", tokens, "Could not read token name file");
        readData("special_case.properties", special_case, "Could not read token name file");
        readData("general.properties", general, "Could not read general token name file");
        readData("factions.properties", factions, "Could not read factions name file");
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

    public static boolean isFaction(String faction){
        return factions.getProperty(faction) != null;
    }

    public static String getColorID(String color){
        return colors.getProperty(color);
    }

    public static String getSpecialCaseValues(String id){
        String property = special_case.getProperty(id);
        return property != null ? property : "";
    }

    public static String getTileID(String tileID) {
        return tiles.getProperty(tileID);
    }

    public static String getFactionFileName(String factionID) {
        return factions.getProperty(factionID);
    }

    public static String getGeneralFileName(String id) {
        return general.getProperty(id);
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

    public static String getAttachmentID(String tokenID) {
        return attachment_tokens.getProperty(tokenID);
    }

    public static String getTokenID(String tokenID) {
        return tokens.getProperty(tokenID);
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

    public static List<String> getTokens() {
        return Stream.of(attachment_tokens.keySet(), tokens.keySet()).flatMap(Collection::stream)
                .filter(token -> token instanceof String)
                .map(token -> (String) token)
                .sorted()
                .collect(Collectors.toList());
    }

    public static List<String> getFactions() {
        return factions.keySet().stream()
                .filter(token -> token instanceof String)
                .map(token -> (String) token)
                .sorted()
                .collect(Collectors.toList());
    }

    public static String getTilesList()
    {
        return  "Tiles: " +  tiles.values().stream()
                .sorted()
                .filter(value -> value instanceof String)
                .map(value -> (String) value)
                .collect(Collectors.joining(", "));
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
