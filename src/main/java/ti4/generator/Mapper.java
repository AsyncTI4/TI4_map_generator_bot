package ti4.generator;

import org.jetbrains.annotations.NotNull;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
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
    private static final HashMap<String, String> techList = new HashMap<>();
    private static final Properties planets = new Properties();
    private static final Properties planet_representation = new Properties();
    private static final Properties attachmentInfo = new Properties();

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
        readData("Secret_objectives.properties", secretObjectives, "Could not read secret objectives file");
        readData("action_cards.properties", actionCards, "Could not read action cards file");
        readData("Agendas.properties", agendas, "Could not read agendas file");
        readData("public_objective.properties", publicObjectives, "Could not read public objective file");
        readData("Promissory_Notes.properties", promissoryNotes, "Could not read promissory notes file");
        readData("exploration.properties", explore, "Could not read explore file");
        readData("tech.properties", techs, "Could not read tech file");
        readData("planets.properties", planets, "Could not read planets file");
        readData("attachments_info.properties", attachmentInfo, "Could not read attachment info file");
        readData("planets_representation.properties", planet_representation, "Could not read planet representation file");
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

    public static List<String>  getPromissoryNotes(String color, String faction){
        List<String> pnList = new ArrayList<>();
        color = AliasHandler.resolveColor(color);
        if (Mapper.isColorValid(color) && Mapper.isFaction(faction)) {
            for (Map.Entry<Object, Object> entry : promissoryNotes.entrySet()) {
                String value = (String) entry.getValue();
                String[] pns = value.split(";");
                String id = pns[1].toLowerCase();
                if (id.equals(color) || AliasHandler.resolveFaction(id).equals(faction)) {
                    pnList.add((String)entry.getKey());
                }
            }
        }
        return pnList;
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
    public static String getSecretObjective(String id) {
        return (String)secretObjectives.get(id);
    }

    public static String getActionCard(String id) {
        return (String)actionCards.get(id);
    }

    public static String getPromissoryNote(String id) {
        return (String)promissoryNotes.get(id);
    }

    public static String getPromissoryNoteOwner(String id) {
        String pnInfo = (String) promissoryNotes.get(id);
        String[] pns = pnInfo.split(";");
        return pns[1].toLowerCase();
    }

    public static String getPublicObjective(String id) {
        return (String)publicObjectives.get(id);
    }

    public static String getAgenda(String id) {
        return (String)agendas.get(id);
    }
    
    public static String getExplore(String id) {
    	return (String)explore.get(id);
    }
    
    public static String getPlanet(String id) {
    	return (String)planets.get(id);
    }

    public static String getAttachmentInfo(String id) {
    	return (String)attachmentInfo.get(id);
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
                if ("For/Against".equals(part)){
                    justAddNext = true;
                    continue;
                }
                agenda.append(part).append(";");
                if (justAddNext){
                    break;
                }
            }
        } catch (Exception e){
            agenda = new StringBuilder((String) agendas.get(id));
        }
        return agenda.toString();
    }
    public static  HashMap<String, String> getSecretObjectives() {
        HashMap<String, String> soList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : secretObjectives.entrySet()) {
            soList.put((String)entry.getKey(), (String)entry.getValue());
        }
        return soList;
    }

    public static HashMap<String, String> getPlanetRepresentations() {
        HashMap<String, String> planets = new HashMap<>();
        for (Map.Entry<Object, Object> entry : planet_representation.entrySet()) {
            planets.put((String)entry.getKey(), (String)entry.getValue());
        }
        return planets;
    }

    public static  HashMap<String, String> getSecretObjectivesJustNames() {
        HashMap<String, String> soList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : secretObjectives.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            soList.put((String)entry.getKey(), tokenizer.nextToken());
        }
        return soList;
    }

    @CheckForNull
    public static String getCCPath(String ccID) {
        String ccPath = ResourceHelper.getInstance().getCCFile(ccID);
        if (ccPath == null) {
            LoggerHandler.log("Could not find command counter: " + ccID);
            return null;
        }
        return ccPath;
    }

    @CheckForNull
    public static String getTokenPath(String tokenID) {
        String tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null) {
            tokenPath = ResourceHelper.getInstance().getTokenFile(tokenID);
            if (tokenPath == null) {
                LoggerHandler.log("Could not find token: " + tokenID);
                return null;
            }
        }
        return tokenPath;
    }

    public static  HashMap<String, String> getActionCards() {
        HashMap<String, String> acList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : actionCards.entrySet()) {
            acList.put((String)entry.getKey(), (String)entry.getValue());
        }
        return acList;
    }

    public static String getTechType(String id) {
        String property = techs.getProperty(id);
        return property.split(",")[1];
    }

    public static  HashMap<String, String> getTechs() {
        if (techList.isEmpty()) {
            for (Map.Entry<Object, Object> entry : techs.entrySet()) {
                String value = (String) entry.getValue();
                value = value.split(",")[0];
                techList.put((String) entry.getKey(), value);
            }
        }
        return techList;
    }

    public static boolean isValidTech(String id){
        HashMap<String, String> techs = getTechs();
        return techs.get(id) != null;
    }

    public static boolean isValidPlanet(String id){
        return  AliasHandler.getPlanetList().contains(id);
    }


    public static  HashMap<String, String> getPublicObjectivesState1() {
        return getPublicObjectives("1");
    }

    public static  HashMap<String, String> getPublicObjectivesState2() {
        return getPublicObjectives("2");
    }

    @NotNull
    private static HashMap<String, String> getPublicObjectives(String requiredStage) {
        HashMap<String, String> poList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : publicObjectives.entrySet()) {
            StringTokenizer tokenizer = new StringTokenizer((String) entry.getValue(), ";");
            if (tokenizer.countTokens() == 4){
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

    public static  HashMap<String, String> getAgendas() {
        HashMap<String, String> agendaList = new HashMap<>();
        for (Map.Entry<Object, Object> entry : agendas.entrySet()) {
            agendaList.put((String)entry.getKey(), (String)entry.getValue());
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
