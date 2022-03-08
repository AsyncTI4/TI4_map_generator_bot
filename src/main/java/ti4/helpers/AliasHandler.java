package ti4.helpers;

import ti4.ResourceHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class AliasHandler {
    private static HashMap<String, String> tileAliasList = new HashMap<>();
    private static HashMap<String, String> unitAliasList = new HashMap<>();
    private static ArrayList<String> unitList = new ArrayList<>();
    private static HashMap<String, String> planetAliasList = new HashMap<>();

    public static void init()
    {
        readAliasFile("tile_alias.properties", tileAliasList, "Could not read tiles alias file");
        readAliasFile("unit_alias.properties", unitAliasList, "Could not read unit alias file");
        readAliasFile("unit_alias.properties", unitList);
        readAliasFile("planet_alias.properties", planetAliasList, "Could not read planet alias file");
    }
    private static void readAliasFile(String fileName, ArrayList list) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                for (Object value : aliasProperties.values()) {
                    if (value instanceof String) {
                        String valueString = (String) value;
                        //noinspection unchecked
                        list.add(valueString);
                    }
                }
            } catch (IOException e) {
                LoggerHandler.log("Could not read alias file", e);
            }
        }
    }

    private static void readAliasFile(String fileName, HashMap<String, String> aliasList, String errorMessage) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                for (String id: aliasProperties.stringPropertyNames()) {
                    StringTokenizer tokenizer = new StringTokenizer(aliasProperties.getProperty(id), ",");
                    while (tokenizer.hasMoreTokens())
                    {
                        String aliasToken = tokenizer.nextToken();
                        if(!aliasToken.isEmpty()) {
                            aliasList.put(aliasToken.toLowerCase(), id);
                        }
                    }
                }
            } catch (IOException e) {
                LoggerHandler.log(errorMessage, e);
            }
        }
    }

    public static String resolveTile(String name)
    {
        String aliasID = tileAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveUnit(String name)
    {
        String aliasID = unitAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static ArrayList<String> getUnitList() {
        return unitList;
    }

    public static String resolvePlanet(String name)
    {
        String aliasID = planetAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
}
