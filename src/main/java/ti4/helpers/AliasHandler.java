package ti4.helpers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import lombok.Getter;
import ti4.ResourceHelper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.BotLogger;
import ti4.model.ColorModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

public class AliasHandler {

    private static final Map<String, String> tilemapAliasList = new HashMap<>();
    private static final Map<String, String> unitAliasList = new HashMap<>();
    @Getter
    private static final Map<String, String> unitListForHelp = new HashMap<>();
    private static final Map<String, String> ccTokenAliasList = new HashMap<>();
    private static final Map<String, String> attachmentAliasList = new HashMap<>();
    private static final Map<String, String> tokenAliasList = new HashMap<>();
    private static final Map<String, String> factionAliasList = new HashMap<>();
    private static final Map<String, String> techAliasList = new HashMap<>();
    private static final Map<String, String> actionCardAliasList = new HashMap<>();
    private static final Map<String, String> agendaAliasList = new HashMap<>();
    private static final Map<String, String> explorationAliasList = new HashMap<>();
    private static final Map<String, String> relicAliasList = new HashMap<>();
    private static final Map<String, String> objectiveAliasList = new HashMap<>();
    private static final Map<String, String> promissoryAliasList = new HashMap<>();
    private static final Map<String, String> ttpgPositionAliasList = new HashMap<>();
    private static final Map<String, String> ttpgAttachmentAliasList = new HashMap<>();
    private static final Map<String, String> ttpgTokenAliasList = new HashMap<>();
    private static final Map<String, String> ttpgUnitAliasList = new HashMap<>();
    private static final Map<String, String> rulesLinks = new HashMap<>();
    private static final Map<String, String> allTileAliases = new HashMap<>();
    private static final Map<String, String> allPlanetAliases = new HashMap<>();

    private static final List<String> unitValuesList = new ArrayList<>();
    private static final List<String> factionAliasValuesList = new ArrayList<>();

    public static void init() {
        readAliasFile("tilemap_alias.properties", tilemapAliasList, "Could not read tilemap alias file");
        readAliasFile("unit_alias.properties", unitAliasList, "Could not read unit alias file");
        readAliasFile("unit_alias.properties", unitListForHelp);
        readAliasFile("cc_token_alias.properties", ccTokenAliasList, "Could not read CC token alias file");
        readAliasFile("attachment_alias.properties", attachmentAliasList, "Could not read attachement token alias file");
        readAliasFile("tokens_alias.properties", tokenAliasList, "Could not read token alias file");
        readAliasFile("faction_alias.properties", factionAliasList, "Could not read faction alias file");
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
        readAliasFile("rules_injection.properties", rulesLinks, "Could not read TTPG unit_alias file");

        readAliasFile("unit_alias.properties", unitValuesList, false);
        readAliasFile("faction_alias.properties", factionAliasValuesList, false);

        initAliases();
    }

    /**
     * Loads aliases in a simple format - used primarily for displaying aliases to users with the /help commands
     * 
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
                BotLogger.error("Could not read alias file", e);
            }
        }
    }

    /**
     * Loads just the key or value for aliases - use the list for simple ifExists / contains checks
     * 
     * @param fileName file with lines like: key=value1,value2,value3
     * @param list list to load
     * @param keys true -> load [key] into the list
     * @param keys false -> load [value1,value2,value3] into the list
     */
    private static void readAliasFile(String fileName, List<String> list, boolean keys) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                if (keys) {
                    for (Object key : aliasProperties.keySet()) {
                        if (key instanceof String valueString) {
                            list.add(valueString);
                        }
                    }
                } else {
                    for (Object value : aliasProperties.values()) {
                        if (value instanceof String valueString) {
                            list.add(valueString);
                        }
                    }
                }
            } catch (IOException e) {
                BotLogger.error("Could not read alias file", e);
            }
        }
    }

    /**
     * Load aliases for actually resolving aliases
     * 
     * @param fileName file with lines like: key=value1,value2,value3
     * @param aliasList map to load aliases like: (value1=key),(value2=key),(value=key)
     * @param errorMessage error message provided
     */
    private static void readAliasFile(String fileName, Map<String, String> aliasList, String errorMessage) {
        Properties aliasProperties = new Properties();
        String aliasFile = ResourceHelper.getInstance().getAliasFile(fileName);
        if (aliasFile != null) {
            try (InputStream input = new FileInputStream(aliasFile)) {
                aliasProperties.load(input);
                for (String id : aliasProperties.stringPropertyNames()) {
                    StringTokenizer tokenizer = new StringTokenizer(aliasProperties.getProperty(id), ",");
                    while (tokenizer.hasMoreTokens()) {
                        String aliasToken = tokenizer.nextToken();
                        if (!aliasToken.isEmpty()) {
                            aliasList.put(aliasToken.toLowerCase(), id);
                        }
                    }
                }
            } catch (IOException e) {
                BotLogger.error(errorMessage, e);
            }
        }
    }

    public static void initAliases() {
        TileHelper.getAllTileModels().forEach(AliasHandler::addNewTileAliases);
        TileHelper.getAllPlanetModels().forEach(AliasHandler::addNewPlanetAliases);
    }

    public static void addNewPlanetAliases(PlanetModel planetModel) {
        Optional.ofNullable(planetModel.getAliases()).orElse(new ArrayList<>())
            .forEach(alias -> allPlanetAliases.put(alias.toLowerCase(), planetModel.getId()));
        allPlanetAliases.put(planetModel.getId(), planetModel.getId()); // add the planet itself to aliashandler
    }

    public static void addNewTileAliases(TileModel tileModel) {
        Optional.ofNullable(tileModel.getAliases()).orElse(new ArrayList<>())
            .forEach(alias -> allTileAliases.put(alias.toLowerCase(), tileModel.getId()));
        Optional.ofNullable(tileModel.getPlanets()).orElse(new ArrayList<>())
            .forEach(planet -> allTileAliases.put(planet.toLowerCase(), tileModel.getId()));
    }

    public static String resolveTile(String name) {
        if ("mirage".equalsIgnoreCase(name)) {
            return name;
        }
        if (TileHelper.getTileById(name) != null) { // name is already an ID
            return name;
        }
        String tileId = allTileAliases.get(name.toLowerCase());
        if (tileId != null) {
            return tileId;
        }
        return name;
    }

    /**
     * For resolving a TileID specific to this Async bot to a "Standard" TileID used by all other TI4 map tools, including TTPG/TTS
     * 
     * @param name - Async specific Tile ID
     * @return Standard TI4 Tile ID number
     */
    public static String resolveStandardTile(String name) {
        String aliasID = tilemapAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for StandardTile: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveFaction(String name) {
        String aliasID = factionAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Faction: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveColor(String name) {
        return Optional.ofNullable(Mapper.getColor(name)).map(ColorModel::getName).orElse(name);
    }

    public static String resolveUnit(String name) {
        String aliasID = unitAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Unit: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static List<String> getPlanetKeyList() {
        return TileHelper.getAllPlanetModels().stream()
            .map(PlanetModel::getId)
            .toList();
    }

    public static String resolvePlanet(String name) {
        if (name.contains(" ")) name = name.substring(0, name.lastIndexOf(" ")); //if there is a space " " then cut off remainder
        if ("gamma".equalsIgnoreCase(name)) {
            return name;
        }
        String aliasID = allPlanetAliases.get(name.toLowerCase());
        if (aliasID != null) {
            return aliasID;
        }
        if (!"space".equals(name)) System.out.println("Could not find an alias for Planet: " + name);
        return name;
    }

    public static String resolveAttachment(String name) {
        String aliasID = allPlanetAliases.get(name.toLowerCase());
        if ("gamma".equalsIgnoreCase(name)) {
            return name;
        }
        //System.out.println("Could not find an alias for Attachment: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveToken(String name) {
        String aliasID = ccTokenAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Token: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveTech(String name) {
        String aliasID = techAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Tech: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveActionCard(String name) {
        String aliasID = actionCardAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for ActionCard: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveAgenda(String name) {
        String aliasID = agendaAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Agenda: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveExploration(String name) {
        String aliasID = explorationAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Exploration: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolvePromissory(String name) {
        String aliasID = promissoryAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Promissory: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveRelic(String name) {
        String aliasID = relicAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Relic: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveObjective(String name) {
        String aliasID = objectiveAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for Objective: " + name);
        return Objects.requireNonNullElse(aliasID, name);
    }

    public static String resolveTTPGAttachment(String name) {
        String aliasID = ttpgAttachmentAliasList.get(name.toLowerCase());
        //System.out.println("Could not find an alias for ttpgAttachment: " + name);
        return Objects.requireNonNullElse(aliasID, name);
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

    public static Set<String> getInjectedRules() {
        return rulesLinks.keySet();
    }

    public static String getInjectedRule(String keyWork) {
        return rulesLinks.get(keyWork);
    }

    /**
     * Given a Position parameter like [+-][0-9][+-][0-9], will return Async position like [0-9][a-z]
     * 
     * @param position TTPG like [+-][0-9][+-][0-9] Eg. +0+0, +2-2, +0+8
     * @return Async position like [0-9][a-z] Eg. 0a, 2e, 4a
     */
    public static String resolveTTPGPosition(String position) {
        // System.out.println("resolving TTPG position: " + position + " to async position: " + aliasID);
        return ttpgPositionAliasList.get(position);
    }

    public static String getFactionAliasEntryList(String faction) {
        return factionAliasValuesList.stream().filter(a -> a.startsWith(faction)).findFirst().orElse(faction);
    }
}
