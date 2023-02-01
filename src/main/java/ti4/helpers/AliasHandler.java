package ti4.helpers;

import ti4.ResourceHelper;
import ti4.message.BotLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class AliasHandler {
    private static HashMap<String, String> tileAliasList = new HashMap<>();
    private static HashMap<String, String> tilemapAliasList = new HashMap<>();
    private static HashMap<String, String> unitAliasList = new HashMap<>();
    private static ArrayList<String> unitList = new ArrayList<>();
    private static ArrayList<String> planetList = new ArrayList<>();
    private static HashMap<String, String> planetAliasList = new HashMap<>();
    private static HashMap<String, String> cctokenAliasList = new HashMap<>();
    private static HashMap<String, String> attachmentAliasList = new HashMap<>();
    private static HashMap<String, String> tokenAliasList = new HashMap<>();
    private static HashMap<String, String> factionAliasList = new HashMap<>();
    private static HashMap<String, String> colorAliasList = new HashMap<>();
    private static HashMap<String, String> techAliasList = new HashMap<>();
    private static HashMap<String, String> actionCardAliasList = new HashMap<>();
    private static HashMap<String, String> agendaAliasList = new HashMap<>();
    private static HashMap<String, String> explorationAliasList = new HashMap<>();
    private static HashMap<String, String> relicAliasList = new HashMap<>();
    private static HashMap<String, String> publicObjectiveAliasList = new HashMap<>();
    private static HashMap<String, String> secretObjectiveAliasList = new HashMap<>();
    private static HashMap<String, String> promissoryAliasList = new HashMap<>();
    private static HashMap<String, String> ttpgPositionAliasList = new HashMap<>();

    public static void init()
    {
        readAliasFile("tile_alias.properties", tileAliasList, "Could not read tiles alias file");
        readAliasFile("tilemap_alias.properties", tilemapAliasList, "Could not read tilemap alias file");
        readAliasFile("unit_alias.properties", unitAliasList, "Could not read unit alias file");
        readAliasFile("unit_alias.properties", unitList);
        readAliasFile("planet_alias.properties", planetList, true);
        readAliasFile("planet_alias.properties", planetAliasList, "Could not read planet alias file");
        readAliasFile("cc_token_alias.properties", cctokenAliasList, "Could not read cc token alias file");
        readAliasFile("attachment_alias.properties", attachmentAliasList, "Could not read attachement token alias file");
        readAliasFile("tokens_alias.properties", tokenAliasList, "Could not read token alias file");
        readAliasFile("faction_alias.properties", factionAliasList, "Could not read faction alias file");
        readAliasFile("color_alias.properties", colorAliasList, "Could not read color alias file");
        readAliasFile("tech_alias.properties", techAliasList, "Could not read tech alias file");
        readAliasFile("action_card_alias.properties", actionCardAliasList, "Could not read action card alias file");
        readAliasFile("agenda_alias.properties", agendaAliasList, "Could not read agenda alias file");
        readAliasFile("exploration_alias.properties", explorationAliasList, "Could not read exploration alias file");
        readAliasFile("relic_alias.properties", relicAliasList, "Could not read relic alias file");
        readAliasFile("public_objective_alias.properties", publicObjectiveAliasList, "Could not read public objective alias file");
        readAliasFile("secret_objective_alias.properties", secretObjectiveAliasList, "Could not read secret objective alias file");
        readAliasFile("promissory_alias.properties", promissoryAliasList, "Could not read promissory alias file");
        readAliasFile("position_alias.properties", ttpgPositionAliasList, "Could not read TTPG position_alias file");
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
                BotLogger.log("Could not read alias file");
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
                BotLogger.log(errorMessage);
            }
        }
    }

    public static String resolveTile(String name)
    {
        String aliasID = tileAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    /** For resolving a TileID specific to this Async bot to a "Standard" TileID used by all other TI4 map tools, including TTPG/TTS
     * @param name - Async specific Tile ID
     * @return Standard TI4 Tile ID number
     */
    public static String resolveStandardTile(String name)
    {
        String aliasID = tilemapAliasList.get(name);
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

    public static String resolveTech(String name)
    {
        String aliasID = techAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveActionCard(String name)
    {
        String aliasID = actionCardAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveAgenda(String name)
    {
        String aliasID = agendaAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    public static String resolveExploration(String name)
    {
        String aliasID = explorationAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
    
    public static String resolvePromissory(String name)
    {
        String aliasID = promissoryAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
    
    public static String resolveRelic(String name)
    {
        String aliasID = relicAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
    
    public static String resolvePublicObjective(String name)
    {
        String aliasID = publicObjectiveAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }
    
    public static String resolveSecretObjective(String name)
    {
        String aliasID = secretObjectiveAliasList.get(name);
        return aliasID != null ? aliasID : name;
    }

    /** Given a Position parameter like [+-][0-9][+-][0-9], will return Async position like [0-9][a-z]
     * @param position TTPG like [+-][0-9][+-][0-9] Eg. +0+0, +2-2, +0+8
     * @return Async position like [0-9][a-z] Eg. 0a, 2e, 4a
     */
    public static String resolveTTPGPosition(String position)
    {
        String aliasID = ttpgPositionAliasList.get(position);
        System.out.println("resolving TTPG position: " + position + " to async position: " + aliasID);
        return aliasID != null ? aliasID : null;
    }
}
