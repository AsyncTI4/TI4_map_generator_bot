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
    private static ArrayList<String> planetList = new ArrayList<>();
    private static HashMap<String, String> planetAliasList = new HashMap<>();
    private static HashMap<String, String> cctokenAliasList = new HashMap<>();
    private static HashMap<String, String> attachmentAliasList = new HashMap<>();
    private static HashMap<String, String> tokenAliasList = new HashMap<>();
    private static HashMap<String, String>  factionAliasList = new HashMap<>();
    private static HashMap<String, String>  colorAliasList = new HashMap<>();

    public static void init()
    {
        readAliasFile("tile_alias.properties", tileAliasList, "Could not read tiles alias file");
        readAliasFile("unit_alias.properties", unitAliasList, "Could not read unit alias file");
        readAliasFile("unit_alias.properties", unitList);
        readAliasFile("planet_alias.properties", planetList, true);
        readAliasFile("planet_alias.properties", planetAliasList, "Could not read planet alias file");
        readAliasFile("cc_token_alias.properties", cctokenAliasList, "Could not read cc token alias file");
        readAliasFile("attachment_alias.properties", attachmentAliasList, "Could not read attachement token alias file");
        readAliasFile("tokens_alias.properties", tokenAliasList, "Could not read token alias file");
        readAliasFile("faction_alias.properties", factionAliasList, "Could not read faction alias file");
        readAliasFile("color_alias.properties", colorAliasList, "Could not read color alias file");
    }
    private static void readAliasFile(String fileName, ArrayList<String> list) {
        readAliasFile(fileName, list, false);
    }
    private static void readAliasFile(String fileName, ArrayList<String> list, boolean keys) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                if (keys){
                    for (Object key : aliasProperties.keySet()) {
                        if (key instanceof String) {
                            String valueString = (String) key;
                            list.add(valueString);
                        }
                    }
                } else {
                    for (Object value : aliasProperties.values()) {
                        if (value instanceof String) {
                            String valueString = (String) value;
                            list.add(valueString);
                        }
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

    public static String resolveFaction(String name)
    {
        String aliasID = factionAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveColor(String name)
    {
        String aliasID = colorAliasList.get(name);
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

    public static ArrayList<String> getPlanetList() {
        return planetList;
    }

    public static String resolvePlanet(String name)
    {
        String aliasID = planetAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveAttachment(String name)
    {
        String aliasID = planetAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveToken(String name)
    {
        String aliasID = cctokenAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
}
