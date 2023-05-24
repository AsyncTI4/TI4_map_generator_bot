package ti4.helpers;

import ti4.ResourceHelper;
import ti4.message.BotLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class AliasHandler {
    private static HashMap<String, String> tileAliasList = new HashMap<>();
    private static HashMap<String, String> tileAliasEntryList = new HashMap<>();
    private static HashMap<String, String> tilemapAliasList = new HashMap<>();
    private static HashMap<String, String> unitAliasList = new HashMap<>();
    private static ArrayList<String> unitValuesList = new ArrayList<>();
    private static ArrayList<String> planetKeyList = new ArrayList<>();
    private static HashMap<String, String> planetAliasList = new HashMap<>();
    private static HashMap<String, String> planetAliasEntryList = new HashMap<>();
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
    private static HashMap<String, String> objectiveAliasList = new HashMap<>();
    private static HashMap<String, String> promissoryAliasList = new HashMap<>();
    private static HashMap<String, String> ttpgPositionAliasList = new HashMap<>();
    private static HashMap<String, String> ttpgAttachmentAliasList = new HashMap<>();
    private static HashMap<String, String> ttpgTokenAliasList = new HashMap<>();
    private static HashMap<String, String> ttpgUnitAliasList = new HashMap<>();

    public static void init() {
        readAliasFile("tile_alias.properties", tileAliasList, "Could not read tiles alias file");
        readAliasFile("tile_alias.properties", tileAliasEntryList);
        readAliasFile("tilemap_alias.properties", tilemapAliasList, "Could not read tilemap alias file");
        readAliasFile("unit_alias.properties", unitAliasList, "Could not read unit alias file");
        readAliasFile("unit_alias.properties", unitValuesList, false);
        readAliasFile("planet_alias.properties", planetKeyList, true);
        readAliasFile("planet_alias.properties", planetAliasEntryList);
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
        readAliasFile("objective_alias.properties", objectiveAliasList, "Could not read objective alias file");
        readAliasFile("promissory_alias.properties", promissoryAliasList, "Could not read promissory alias file");
        readAliasFile("position_alias.properties", ttpgPositionAliasList, "Could not read TTPG position_alias file");
        readAliasFile("ttpg_attachment_alias.properties", ttpgAttachmentAliasList, "Could not read TTPG attachment_alias file");
        readAliasFile("ttpg_token_alias.properties", ttpgTokenAliasList, "Could not read TTPG token_alias file");
        readAliasFile("ttpg_unit_alias.properties", ttpgUnitAliasList, "Could not read TTPG unit_alias file");
    }

    /** Loads aliases in a simple format - used primarily for displaying aliases to users with the /help commands
     * @param fileName file with lines like: key=value1,value2,value3
     * @param map map to load with key and values like: [key],[value1,value2,value3]
     */
    private static void readAliasFile(String fileName, Map<String, String> map) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                aliasProperties.forEach((x, y) -> map.put(x.toString(), y.toString()));
            } catch (IOException e) {
                BotLogger.log("Could not read alias file", e);
            }
        }
    }

    /** Loads just the key or value for aliases - use the list for simple ifExists / contains checks
     * @param fileName file with lines like: key=value1,value2,value3
     * @param list list to load
     * @param keys true ->  load [key] into the list
     * @param keys false -> load [value1,value2,value3] into the list
     */
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
                BotLogger.log("Could not read alias file", e);
            }
        }
    }

    /** Load aliases for actually resolving aliases
     * @param fileName file with lines like: key=value1,value2,value3
     * @param aliasList map to load aliases like: (value1=key),(value2=key),(value=key)
     * @param errorMessage error message provided
     */
    private static void readAliasFile(String fileName, HashMap<String, String> aliasList, String errorMessage) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                for (String id: aliasProperties.stringPropertyNames()) {
                    StringTokenizer tokenizer = new StringTokenizer(aliasProperties.getProperty(id), ",");
                    while (tokenizer.hasMoreTokens()) {
                        String aliasToken = tokenizer.nextToken();
                        if (!aliasToken.isEmpty()) {
                            aliasList.put(aliasToken.toLowerCase(), id);
                        }
                    }
                }
            } catch (IOException e) {
                BotLogger.log(errorMessage);
            }
        }
    }

    public static String resolveTile(String name) {
        
        if(name.equalsIgnoreCase("mirage"))
        {
            return name;
        }
        String aliasID = tileAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            System.out.println("Could not find an alias for Tile: " + name);
            return name;
        }
    }

    /** For resolving a TileID specific to this Async bot to a "Standard" TileID used by all other TI4 map tools, including TTPG/TTS
     * @param name - Async specific Tile ID
     * @return Standard TI4 Tile ID number
     */
    public static String resolveStandardTile(String name) {
        String aliasID = tilemapAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for StandardTile: " + name);
            return name;
        }
    }

    public static String resolveFaction(String name) {
        String aliasID = factionAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Faction: " + name);
            return name;
        }
    }

    public static String resolveColor(String name) {
        String aliasID = colorAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Color: " + name);
            return name;
        }
    }

    public static String resolveUnit(String name) {
        String aliasID = unitAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Unit: " + name);
            return name;
        }
    }

    public static ArrayList<String> getUnitValuesList() {
        return unitValuesList;
    }

    public static ArrayList<String> getPlanetKeyList() {
        return planetKeyList;
    }

    public static String resolvePlanet(String name) {
        if (name.contains(" ")) name = name.substring(0, name.lastIndexOf(" ")); //if there is a space " " then cut off remainder
        String aliasID = planetAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Planet: " + name);
            return name;
        }
    }

    public static String resolveAttachment(String name) {
        String aliasID = planetAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Attachment: " + name);
            return name;
        }
    }

    public static String resolveToken(String name) {
        String aliasID = cctokenAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Token: " + name);
            return name;
        }
    }

    public static String resolveTech(String name) {
        String aliasID = techAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Tech: " + name);
            return name;
        }
    }

    public static String resolveActionCard(String name) {
        String aliasID = actionCardAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for ActionCard: " + name);
            return name;
        }
    }

    public static String resolveAgenda(String name) {
        String aliasID = agendaAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Agenda: " + name);
            return name;
        }
    }

    public static String resolveExploration(String name) {
        String aliasID = explorationAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Exploration: " + name);
            return name;
        }
    }

    public static String resolvePromissory(String name) {
        String aliasID = promissoryAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Promissory: " + name);
            return name;
        }
    }

    public static String resolveRelic(String name) {
        String aliasID = relicAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Relic: " + name);
            return name;
        }
    }

    public static String resolveObjective(String name) {
        String aliasID = objectiveAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for Objective: " + name);
            return name;
        }
    }

    public static String resolveTTPGAttachment(String name) {
        String aliasID = ttpgAttachmentAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for ttpgAttachment: " + name);
            return name;
        }
    }

    public static String resolveTTPGToken(String name) {
        String aliasID = ttpgTokenAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        } else {
            //System.out.println("Could not find an alias for TTPGToken: " + name);
            return name;
        }
    }

    public static String resolveTTPGUnit(String name) {
        String aliasID = ttpgUnitAliasList.get(name.toLowerCase());
        if (aliasID != null) {
            return resolveUnit(aliasID);
        } else {
            //System.out.println("Could not find an alias for TTPGUnit: " + name);
            return name;
        }
    }

    /** Given a Position parameter like [+-][0-9][+-][0-9], will return Async position like [0-9][a-z]
     * @param position TTPG like [+-][0-9][+-][0-9] Eg. +0+0, +2-2, +0+8
     * @return Async position like [0-9][a-z] Eg. 0a, 2e, 4a
     */
    public static String resolveTTPGPosition(String position) {
        String aliasID = ttpgPositionAliasList.get(position);
        // System.out.println("resolving TTPG position: " + position + " to async position: " + aliasID);
        return aliasID != null ? aliasID : null;
    }

    public static HashMap<String, String> getPlanetAliasEntryList() {
        return planetAliasEntryList;
    }

    public static HashMap<String, String> getTileAliasEntryList() {
        return tileAliasEntryList;
    }
}
