package ti4.helpers;

import ti4.ResourceHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;

public class AliasHandler {
    private static HashMap<String, String> tileAliasList = new HashMap<>();
    private static HashMap<String, String> unitAliasList = new HashMap<>();
    private static HashMap<String, String> planetAliasList = new HashMap<>();

    public static void init()
    {
        readAliasFile("tile_alias.properties", tileAliasList, "Could not read tiles alias file");
        readAliasFile("unit_alias.properties", unitAliasList, "Could not read unit alias file");
        readAliasFile("planet_alias.properties", planetAliasList, "Could not read planet alias file");
    }

    private static void readAliasFile(String fileName, HashMap<String, String> aliasList, String errorMessage) {
        Properties aLiasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aLiasProperties.load(input);
                for (String id: aLiasProperties.stringPropertyNames()) {
                    StringTokenizer tokenizer = new StringTokenizer(aLiasProperties.getProperty(id), ",");
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

    public static String resolvePlanet(String name)
    {
        String aliasID = planetAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
}
