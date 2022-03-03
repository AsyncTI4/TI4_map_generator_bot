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

    public static void init()
    {
        initTileAlias();
    }

    private static void initTileAlias() {
        Properties tileALiasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile("tile_alias.properties");
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                tileALiasProperties.load(input);
                for (String id: tileALiasProperties.stringPropertyNames()) {
                    StringTokenizer tokenizer = new StringTokenizer(tileALiasProperties.getProperty(id), ",");
                    while (tokenizer.hasMoreTokens())
                    {
                        tileAliasList.put(tokenizer.nextToken(), id);
                    }
                }
            } catch (IOException e) {
                LoggerHandler.log("Could not read tiles name file", e);
            }
        }
    }

    public static String resolveTile(String name)
    {
        String aliasID = tileAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
}
