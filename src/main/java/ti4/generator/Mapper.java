package ti4.generator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.message.BotLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
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
    private static final Properties secretObjectives = new Properties();
    private static final Properties actionCards = new Properties();
    private static final Properties agendas = new Properties();
    private static final Properties publicObjectives = new Properties();
    private static final Properties promissoryNotes = new Properties();
    private static final Properties techs = new Properties();
    private static final Properties explore = new Properties();
    private static final Properties relics = new Properties();
    private static final HashMap<String, String> techList = new HashMap<>();
    private static final HashMap<String, String[]> techListInfo = new HashMap<>();
    private static final Properties planets = new Properties();
    private static final Properties faction_representation = new Properties();
    private static final Properties planet_representation = new Properties();
    private static final Properties leader_representation = new Properties();
    private static final Properties tile_representation = new Properties();
    private static final Properties unit_representation = new Properties();
    private static final Properties attachmentInfo = new Properties();
    private static final Properties leaders = new Properties();
    private static final Properties playerSetup = new Properties();
    private static final Properties miltyDraft = new Properties();
    private static final Properties agendaRepresentation = new Properties();
    private static final Properties adjacentTiles = new Properties();
    private static final Properties hyperlaneAdjacencies = new Properties();
    private static final Properties wormholes = new Properties();
    private static final HashMap<String, HashMap<String, ArrayList<String>>> leadersInfo = new HashMap<>();

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
        readData("secret_objectives.properties", secretObjectives, "Could not read secret objectives file");
        readData("action_cards.properties", actionCards, "Could not read action cards file");
        readData("agendas.properties", agendas, "Could not read agendas file");
        readData("public_objective.properties", publicObjectives, "Could not read public objective file");
        readData("promissory_notes.properties", promissoryNotes, "Could not read promissory notes file");
        readData("exploration.properties", explore, "Could not read explore file");
        readData("leaders.properties", leaders, "Could not read leaders file");
        readData("relics.properties", relics, "Could not read relic file");
        readData("tech.properties", techs, "Could not read tech file");
        readData("planets.properties", planets, "Could not read planets file");
        readData("attachments_info.properties", attachmentInfo, "Could not read attachment info file");
        readData("faction_representation.properties", faction_representation, "Could not read faction representation file");
        readData("planets_representation.properties", planet_representation, "Could not read planet representation file");
        readData("tile_representation.properties", tile_representation, "Could not read tile representation file");
        readData("leader_representation.properties", leader_representation, "Could not read leader representation file");
        readData("unit_representation.properties", unit_representation, "Could not read unit representation file");
        readData("faction_setup.properties", playerSetup, "Could not read player setup file");
        readData("milty_draft.properties", miltyDraft, "Could not read milty draft file");
        readData("agenda_representation.properties", agendaRepresentation, "Could not read agenda representaion file");
        readData("adjacent.properties", adjacentTiles, "Could not read adjacent tiles file");
        readData("hyperlanes.properties",hyperlaneAdjacencies,"Could not read hyperlanes file");
        readData("wormholes.properties", wormholes, "Could not read wormholes file");
    }

    private static void readData(String propertyFileName, Properties properties, String s) {
        String propFile = ResourceHelper.getInstance().getInfoFile(propertyFileName);
        if (propFile != null) {
            try (InputStream input = new FileInputStream(propFile)) {
                properties.load(input);
            } catch (IOException e) {
                BotLogger.log(s);
            }
        }
    }

    public static List<String> getPromissoryNotes(String color, String faction) {
        List<String> pnList = new ArrayList<>();
        color = AliasHandler.resolveColor(color);
        if (Mapper.isColorValid(color) && Mapper.isFaction(faction)) {
            for (Map.Entry<Object, Object> entry : promissoryNotes.entrySet()) {
                String value = (String) entry.getValue();
                String[] pns = value.split(";");
                String id = pns[1].toLowerCase();
                if (id.equals(color) || (isFaction(id) && AliasHandler.resolveFaction(id).equals(faction))) { 
                    pnList.add((String) entry.getKey());
                }
            }
        }
        return pnList;
    }
    
    public static List<String> getPromissoryNotes() {
        List<String> pnList = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : promissoryNotes.entrySet()) {
            pnList.add((String) entry.getKey());
        }
        return pnList;
    }
    public static boolean isColorValid(String color) {
        String property = colors.getProperty(color);
        return property != null && !property.equals("null");
    }

    public static boolean isFaction(String faction) {
        return factions.getProperty(faction) != null;
    }

    public static String getColorID(String color) {
        return colors.getProperty(color);
    }

    public static String getSpecialCaseValues(String id) {
        String property = special_case.getProperty(id);
        return property != null ? property : "";
    }

    public static String getTileID(String tileID) {
        return tiles.getProperty(tileID);
    }

    public static List<String> getAdjacentTilesIDs(String tileID) {
        String property = adjacentTiles.getProperty(tileID);
        if (property == null){
            return Collections.emptyList();
        }
        return Arrays.stream(property.split(",")).toList();
    }

    public static List<List<Boolean>> getHyperlaneData(String tileID) {
        String property = hyperlaneAdjacencies.getProperty(tileID);
        if (property == null) return Collections.emptyList();

        List<String> directions = Arrays.stream(property.split(";")).toList();
        List<List<Boolean>> data = new ArrayList<>();
        for (String dir : directions) {
            List<String> info = Arrays.stream(dir.split(",")).toList();
            List<Boolean> connections = new ArrayList<>();
            for (String value : info) connections.add(value.equals("1"));
            data.add(connections);
        }
        return data;
    }

    public static Set<String> getWormholes(String tileID) {
        String property = wormholes.getProperty(tileID);
        if (property == null){
            return new HashSet<>();
        }
        return Arrays.stream(property.split(",")).collect(Collectors.toSet());
    }

    public static Set<String> getWormholesTiles(String wormholeID) {
        Set<String> tileIDs = new HashSet<>();
        for (Map.Entry<Object, Object> wormholeEntry : wormholes.entrySet()) {
            Object value = wormholeEntry.getValue();
            if (value instanceof String){
                if (Arrays.asList(((String) value).split(",")).contains(wormholeID)){
                    tileIDs.add((String)wormholeEntry.getKey());
                }
            }
        }
        return tileIDs;
    }

    public static String getFactionFileName(String factionID) {
        return factions.getProperty(factionID);
    }

    public static String getGeneralFileName(String id) {
        return general.getProperty(id);
    }

    public static Map<String, String> getUnits() {
        Map<String, String> unitMap = new HashMap<>();
        for (Map.Entry<Object, Object> entry : units.entrySet()) {
            String representation = (String)unit_representation.get(entry.getKey());
            unitMap.put((String)entry.getValue(), representation);
        }
        return unitMap;
    }

    public static Map<String, String> getColorToId() {
        Map<String, String> unitMap = new HashMap<>();
        for (Map.Entry<Object, Object> entry : colors.entrySet()) {
            unitMap.put((String)entry.getValue(), (String)entry.getKey());
        }
        return unitMap;
    }

    public static String getUnitID(String unitID, String color) {
        String property = colors.getProperty(color);
        return property + units.getProperty(unitID);
    }

    public static List<String> getUnitIDList() {
        return units.keySet().stream().filter(unit -> unit instanceof String)
                .map(unit -> (String) unit)
                .sorted()
                .collect(Collectors.toList());
    }

    public static String getCCID(String color) {
        String property = colors.getProperty(color);
        return cc_tokens.get("cc") + property + ".png";
    }

    public static String getFleeCCID(String color) {
        String property = colors.getProperty(color);
        return cc_tokens.get("fleet") + property + ".png";
    }

    public static String getAttachmentID(String tokenID) {
        return attachment_tokens.getProperty(tokenID);
    }

    public static String getTokenID(String tokenID) {
        return tokens.getProperty(tokenID);
    }

    public static String getPlayerSetup(String factionID) {
        return playerSetup.getProperty(factionID);
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

    public static Map<String, String> getTokensToName() {
        Map<String, String> tokensToName = new HashMap<>();
        for (Map.Entry<Object, Object> attachment : attachment_tokens.entrySet()) {
            String key = (String)attachment.getKey();
            String value = (String)attachment.getValue();
            tokensToName.put(value, key);
        }

        for (Map.Entry<Object, Object> tokens : tokens.entrySet()) {
            String key = (String)tokens.getKey();
            String value = (String)tokens.getValue();
            tokensToName.put(value, key);
        }
        return tokensToName;
    }

    public static String getSecretObjective(String id) {
        return (String) secretObjectives.get(id);
    }

    public static String getActionCard(String id) {
        return (String) actionCards.get(id);
    }

    @Nullable
    public static String getActionCardName(String id) {
        String info = (String) actionCards.get(id);
        // if we would break trying to split the note, just return whatever is there
        if ((info == null) || !info.contains(";")) {
            return info;
        }
        String[] split = info.split(";");
        return split[0];
    }

    public static String getPromissoryNote(String id, boolean longDisplay) {
        if (longDisplay) {
            return getPromissoryNote(id);
        } else {
            return getShortPromissoryNote(id);
        }
    }

    public static String getPromissoryNote(String id) {
        return (String) promissoryNotes.get(id);
    }

    public static String getShortPromissoryNote(String id) {
        String promStr = promissoryNotes.getProperty(id);
        // if we would break trying to split the note, just return whatever is there
        if ((promStr == null) || !promStr.contains(";")) {
            return promStr;
        }
        String[] pns = ((String) promissoryNotes.get(id)).split(";");
        return pns[0] + ";" + pns[1];
    }

    public static String getPromissoryNoteOwner(String id) {
        String pnInfo = (String) promissoryNotes.get(id);
        String[] pns = pnInfo.split(";");
        return pns[1].toLowerCase();
    }

    public static String getPublicObjective(String id) {
        return (String) publicObjectives.get(id);
    }

    public static String getAgenda(String id) {
        return (String) agendas.get(id);
    }

    public static String getExplore(String id) {
        return (String) explore.get(id);
    }

    public static String getRelic(String id) {
        return (String) relics.get(id);
    }

    public static String getPlanet(String id) {
        return (String) planets.get(id);
    }

    public static String getAttachmentInfo(String id) {
        return (String) attachmentInfo.get(id);
    }

    public static List<String> getAttachmentInfoAll() {
        return Stream.of(attachmentInfo.keySet(), tokens.keySet()).flatMap(Collection::stream)
                .filter(token -> token instanceof String)
                .map(token -> (String) token)
                .sorted()
                .collect(Collectors.toList());
    }

    public static String getAgendaForOnly(String id) {
        StringBuilder agenda = new StringBuilder((String) agendas.get(id));
        try {
            String[] split = agenda.toString().split(";");
            agenda = new StringBuilder();
            boolean justAddNext = false;
            for (String part : split) {
                if ("For/Against".equals(part)) {
                    justAddNext = true;
                    continue;
                }
                agenda.append(part).append(";");
                if (justAddNext) {
                    break;
                }
            }
        } catch (Exception e) {
            agenda = new StringBuilder((String) agendas.get(id));
        }
        return agenda.toString();
    }

    @Nullable
    public static String getAgendaTitle(String id) {
        String agendaInfo = (String)agendaRepresentation.get(id);
        if (agendaInfo == null){
            return null;
        }
        String[] split = agendaInfo.split(";");
        return split[1];
    }

    public static String getAgendaType(String id) {
        String agendaInfo = (String)agendaRepresentation.get(id);
        if (agendaInfo == null){
            return "1";
        }
        String[] split = agendaInfo.split(";");
        return split[0];
    }

    @Nullable
    public static String getAgendaText(String id) {
        String agendaInfo = (String)agendaRepresentation.get(id);
        if (agendaInfo == null){
            return null;
        }
        String[] split = agendaInfo.split(";");
        return split[2];
    }

    public static HashMap<String, String> getSecretObjectives() {
        HashMap<String, String> soList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : secretObjectives.entrySet()) {
            soList.put((String) entry.getKey(), (String) entry.getValue());
        }
        return soList;
    }

    public static HashMap<String, String> getPlanetRepresentations() {
        HashMap<String, String> planets = new HashMap<>();
        for (Map.Entry<Object, Object> entry : planet_representation.entrySet()) {
            planets.put((String) entry.getKey(), (String) entry.getValue());
        }
        return planets;
    }

    public static HashMap<String, String> getFactionRepresentations() {
        HashMap<String, String> factions = new HashMap<>();
        for (Map.Entry<Object, Object> entry : faction_representation.entrySet()) {
            factions.put((String) entry.getKey(), (String) entry.getValue());
        }
        return factions;
    }

    public static HashMap<String, String> getLeaderRepresentations() {
        HashMap<String, String> leaders = new HashMap<>();
        for (Map.Entry<Object, Object> entry : leader_representation.entrySet()) {
            leaders.put((String) entry.getKey(), (String) entry.getValue());
        }
        return leaders;
    }

    public static HashMap<String, String> getTileRepresentations() {
        HashMap<String, String> tiles = new HashMap<>();
        for (Map.Entry<Object, Object> entry : tile_representation.entrySet()) {
            tiles.put((String) entry.getKey(), (String) entry.getValue());
        }
        return tiles;
    }

    public static HashMap<String, String> getMiltyDraftTiles() {
        HashMap<String, String> tiles = new HashMap<>();
        for (Map.Entry<Object, Object> entry : miltyDraft.entrySet()) {
            tiles.put((String) entry.getKey(), (String) entry.getValue());
        }
        return tiles;
    }

    public static HashMap<String, String> getUnitRepresentations() {
        HashMap<String, String> units = new HashMap<>();
        for (Map.Entry<Object, Object> entry : unit_representation.entrySet()) {
            units.put((String) entry.getKey(), (String) entry.getValue());
        }
        return units;
    }

    public static HashMap<String, String> getSecretObjectivesJustNames() {
        HashMap<String, String> soList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : secretObjectives.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            soList.put((String) entry.getKey(), tokenizer.nextToken());
        }
        return soList;
    }

    public static HashMap<String, String> getAgendaJustNames() {
        HashMap<String, String> agendaList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : agendas.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            String value = tokenizer.nextToken();
            value = value.replace("_", "");
            value = value.replace("*", "");
            agendaList.put((String) entry.getKey(), value);
        }
        return agendaList;
    }

    @Nullable
    public static String getCCPath(String ccID) {
        String ccPath = ResourceHelper.getInstance().getCCFile(ccID);
        if (ccPath == null) {
//            LoggerHandler.log("Could not find command counter: " + ccID);
            return null;
        }
        return ccPath;
    }

    @Nullable
    public static String getTokenPath(String tokenID) {
        String tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null || !(new File(tokenPath).exists())) {
            tokenPath = ResourceHelper.getInstance().getTokenFile(tokenID);
            if (tokenPath == null) {
                BotLogger.log("Could not find token: " + tokenID);
                return null;
            }
        }
        return tokenPath;
    }

    public static HashMap<String, String> getActionCards() {
        HashMap<String, String> acList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : actionCards.entrySet()) {
            acList.put((String) entry.getKey(), (String) entry.getValue());
        }
        return acList;
    }

    public static HashMap<String, String> getACJustNames() {
        HashMap<String, String> agendaList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : actionCards.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            String value = tokenizer.nextToken();
            agendaList.put((String) entry.getKey(), value);
        }
        return agendaList;
    }

    public static String getTechType(String id) {
        String property = techs.getProperty(id);
        return property.split(",")[1];
    }

    public static HashMap<String, String> getTechs() {
        if (techList.isEmpty()) {
            for (Map.Entry<Object, Object> entry : techs.entrySet()) {
                String value = (String) entry.getValue();
                value = value.split(",")[0];
                techList.put((String) entry.getKey(), value);
            }
        }
        return techList;
    }

    public static HashMap<String, String[]> getTechsInfo() {
        if (techListInfo.isEmpty()) {
            for (Map.Entry<Object, Object> entry : techs.entrySet()) {
                String value = (String) entry.getValue();
                String[] split = value.split(",");
                techListInfo.put((String) entry.getKey(), split);
            }
        }
        return techListInfo;
    }

    public static HashMap<String, HashMap<String, ArrayList<String>>> getLeadersInfo() {
        if (leadersInfo.isEmpty()) {
            for (Map.Entry<Object, Object> entry : leaders.entrySet()) {
                String value = (String) entry.getValue();
                String[] leaders = value.split(";");
                HashMap<String, ArrayList<String>> leaderMap = new HashMap<>();
                for (String leader : leaders) {
                    ArrayList<String> filteredNames = new ArrayList<>();
                    if (leader.contains(",")) {
                        String[] names = leader.split(",");
                        if (names.length > 1) {
                            for (String name : names) {
                                if (!name.equals(Constants.AGENT) &&
                                    !name.equals(Constants.COMMANDER) &&
                                    !name.equals(Constants.HERO))
                                {
                                    filteredNames.add(name);
                                }
                            }
                        }
                        leaderMap.put(names[0], filteredNames);
                    } else {
                        leaderMap.put(leader, filteredNames);
                    }
                }
                leadersInfo.put((String)entry.getKey(), leaderMap);
            }
        }
        return leadersInfo;
    }

    public static boolean isValidTech(String id) {
        HashMap<String, String> techs = getTechs();
        return techs.get(id) != null;
    }

    public static boolean isValidPlanet(String id) {
        return AliasHandler.getPlanetList().contains(id);
    }


    public static HashMap<String, String> getPublicObjectivesState1() {
        return getPublicObjectives("1");
    }

    public static HashMap<String, String> getPublicObjectivesState2() {
        return getPublicObjectives("2");
    }

    @NotNull
    private static HashMap<String, String> getPublicObjectives(String requiredStage) {
        HashMap<String, String> poList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : publicObjectives.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            if (tokenizer.countTokens() == 4) {
                String name = tokenizer.nextToken();
                String category = tokenizer.nextToken();
                String description = tokenizer.nextToken();
                String stage = tokenizer.nextToken();
                if (requiredStage.equals(stage)) {
//                    poList.put((String) entry.getKey(), name + ";" + category + ";" + description);
                    poList.put((String) entry.getKey(), name);
                }
            }
        }
        return poList;
    }

    public static HashMap<String, String> getExplores() {
        HashMap<String, String> expList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : explore.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            expList.put((String) entry.getKey(), tokenizer.nextToken());
        }
        return expList;
    }

    public static HashMap<String, String> getRelics() {
        HashMap<String, String> relicList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : relics.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            relicList.put((String) entry.getKey(), tokenizer.nextToken());
        }
        return relicList;
    }

    public static HashMap<String, String> getAgendas() {
        HashMap<String, String> agendaList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : agendas.entrySet()) {
            agendaList.put((String) entry.getKey(), (String) entry.getValue());
        }
        return agendaList;
    }

    public static List<String> getFactions() {
        return factions.keySet().stream()
                .filter(token -> token instanceof String)
                .map(token -> (String) token)
                .sorted()
                .collect(Collectors.toList());
    }

    public static String getTilesList() {
        return "Tiles: " + tiles.values().stream()
                .sorted()
                .filter(value -> value instanceof String)
                .map(value -> (String) value)
                .collect(Collectors.joining(", "));
    }

    public static String getPlanetList() {
        return "Planets: " + AliasHandler.getPlanetList().stream()
                .sorted()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    public static String getUnitList() {
        return "Units: " + AliasHandler.getUnitList().stream()
                .sorted()
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n "));
    }
}
