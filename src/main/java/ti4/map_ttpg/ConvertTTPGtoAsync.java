package ti4.map_ttpg;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.print.attribute.standard.Chromaticity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import ti4.commands.map.AddTileList;
import ti4.commands.player.PlanetAdd;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddControl;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.map_ttpg.TTPGPlayer;

public class ConvertTTPGtoAsync {
    public static final java.util.Map<String,String> fakePlayers = new HashMap<String, String>() {
        {
            put("481860200169472030", "PrisonerOne");
            put("345897843757678603", "TerTerro");
            put("150809002974904321", "Holytispoon");
            put("936295970671566879", "somno");
            put("426282231234035722", "Son of Leto(UTC-6)");
            put("960683086570487848", "TheEpicNerd");
            // put("947763140517560331", "TI4 Game Management");
            // put("1059869343636263023", "TI4-Bot-Test");
            // put("814883082033037383", "Map Bot");
            // put("235148962103951360", "Carl-bot");
            // put("936929561302675456", "Midjourney Bot");
            // put("812171459564011580", "RoboDane");
            // put("572698679618568193", "Dicecord");
        }
    };
    


    public static void main(String[] args) throws Exception {
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
        // String jsonSource = readFileAsString("storage/ttpg_exports/TTPG-Export.json");
        // JsonNode node = parse(jsonSource);
        TTPGMap ttpgMap = getTTPGMapFromJsonFile("storage/ttpg_exports/TTPG-Export-Hadouken.json");

        Map map = ConvertTTPGMaptoAsyncMap(ttpgMap);

        // JsonNode node = toJson(map);
        // System.out.println(generateString(node,true));

        MapSaveLoadManager.saveMap(map);
        // Map newMap = MapSaveLoadManager.loadMap
    }

    public static Map ConvertTTPGMaptoAsyncMap(TTPGMap ttpgMap){
        Mapper.init();
        Map asyncMap = new Map() {
            {
                setOwnerID("947763140517560331");
                setOwnerName("TI4 Game Management");
                setPlayerCountForMap(ttpgMap.getPlayers().size());
                setVp(ttpgMap.getScoreboard());
                setRound(ttpgMap.getRound());
                setName("ttpgimport");// + currentDateTime());
            }
        };
        
        // System.out.println("Mapped? " + AddTileList.setMapTileList(null, ttpgMap.getMapString(), asyncMap));

        Integer index = 0;
        LinkedHashMap<String, Player> asyncPlayers = asyncMap.getPlayers();
        for (Entry<String,String> fakePlayer : fakePlayers.entrySet()) {
            asyncMap.addPlayer(fakePlayer.getKey().toString(), fakePlayer.getValue().toString());
            Player asyncPlayer = asyncMap.getPlayer(fakePlayer.getKey().toString());
            TTPGPlayer ttpgPlayer = ttpgMap.getPlayers().get(index);

            //PLAYER STATS
            asyncPlayer.setCommodities(ttpgPlayer.getCommodities());
            asyncPlayer.setCommoditiesTotal(ttpgPlayer.getMaxCommodities());
            asyncPlayer.setTg(ttpgPlayer.getTradeGoods());
            asyncPlayer.setTacticalCC(ttpgPlayer.getCommandTokens().getTactics());
            asyncPlayer.setFleetCC(ttpgPlayer.getCommandTokens().getFleet());
            asyncPlayer.setStrategicCC(ttpgPlayer.getCommandTokens().getStrategy());
            asyncPlayer.setFaction(AliasHandler.resolveFaction(ttpgPlayer.getFactionShort().toLowerCase()));
            asyncPlayer.setColor(AliasHandler.resolveColor(ttpgPlayer.getColorActual().toLowerCase()));

            //PLANETS
            for (String planet : ttpgPlayer.getPlanetCards()) {
                asyncPlayer.addPlanet(AliasHandler.resolvePlanet(planet.toLowerCase()));
            }

            //LEADERS
            if (!asyncPlayer.getFaction().equals("keleres") || !asyncPlayer.getFaction().equals("nomad")) { //deal with these chumps later
                asyncPlayer.getLeader("agent").setLocked(ttpgPlayer.getLeaders().getAgent().equals("unlocked") ? false : true);
                asyncPlayer.getLeader("commander").setLocked(ttpgPlayer.getLeaders().getCommander().equals("unlocked") ? false : true);
                asyncPlayer.getLeader("hero").setLocked(ttpgPlayer.getLeaders().getHero().equals("unlocked") ? false : true);
            }

            //CUSTODIAN POINTS
            Integer ttpgCustodianPoints = ttpgPlayer.getCustodiansPoints();
            if (ttpgCustodianPoints > 0) {
                while (ttpgCustodianPoints > 0) {
                    asyncMap.scorePublicObjective(asyncPlayer.getUserID(), 0);
                    ttpgCustodianPoints--;
                }
            }

            //TECHS
            for (String technology : ttpgPlayer.getTechnologies()) {
                asyncPlayer.addTech(AliasHandler.resolveTech(technology.toLowerCase()));
            }
            
            index++;
        }
        // setScoredPublicObjectives(ttpgMap.getObjectives().getPublicObjectivesI());

        //TILES - HEX SUMMARY
        String[] hexSummary = ttpgMap.getHexSummary().split(",");
        for (String hex : hexSummary) {
            System.out.println("Hex: " + hex);
            if (hex.length() > 0) {
                Tile tile = ConvertTTPGHexToAsyncTile(asyncMap, hex);
                if (tile != null) {
                    asyncMap.setTile(tile);
                } else {
                    System.out.println("null tile");
                }
            } else {
                System.out.println("0 length hex string");
            }
        }

        //add control tokens to all owned planets
        for (Tile tile : asyncMap.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                for (Player player : asyncMap.getPlayers().values()) {
                    for (String planet : player.getPlanets()) {
                        // System.out.println(unitHolder.getName() + "  " + planet + "   " + player.getColor());
                        if (unitHolder.getName().equalsIgnoreCase(planet)) {
                            tile.addControl(Mapper.getControlID(player.getColor()), planet);
                        }
                    }
                }
            }
        }

        return asyncMap;
    }

    // public static Player ConvertTTPGPlayerToAsyncPlayer (TTPGPlayer ttpgPlayer) {
    //     Player asyncPlayer = new Player(ttpgPlayer);
    //     return asyncPlayer;
    // }
    
    public static Tile ConvertTTPGHexToAsyncTile (Map asyncMap, String ttpgHex) {
        // System.out.println(" Examining hex summary:  " + ttpgHex);

        // TILE +-X +-Y SPACE ; PLANET1 ; PLANET2 ; ...
        Pattern firstRegionPattern = Pattern.compile("^([0-9AB]+)([-+][0-9]+)([-+][0-9]+)(.*)?$");
        Pattern rotPattern = Pattern.compile("^(\\d+)([AB])(\\d)$"); //ignore hyperlanes for now
        Pattern regionAttachmentsPattern = Pattern.compile("^(.*)\\*(.*)$");  
        
        Matcher matcher = firstRegionPattern.matcher(ttpgHex);
        if (matcher.find()) {
            // System.out.println("     Matches!");
            // System.out.println("       group(0):" + matcher.group(0));
            System.out.println("     TileID:" + matcher.group(1));
            System.out.println("     X:" + matcher.group(2));
            System.out.println("     Y:" + matcher.group(3));
            // System.out.println("       group(4):" + matcher.group(4));
            
        } else {
            System.out.println("     No Match");
            return null;
        }
        
        Tile tile = null;
        String tileID = AliasHandler.resolveTile(matcher.group(1));
        String ttpgXPosition = matcher.group(2);
        String ttpgYPosition = matcher.group(3);
        String ttpgPosition = ttpgXPosition + ttpgYPosition;

        String asyncPosition = AliasHandler.resolveTTPGPosition(ttpgPosition);

        switch (tileID) {
            //TODO: smart placement of mallice/whdelta/nombox
            case "82" -> { //Mallice
                tileID = "82b"; //TODO: If 82 hasunits, then 82b, otherwise, 82a
                asyncPosition = "tl"; //hardcode top left for now
            }
            case "51" -> { //Creuss
                //TODO: move DeltaWH if exists in tileList
                asyncPosition = "tr"; //hardcode top right for now
            }
            case "17" -> { //DeltaWH
                //TODO: move Creuss if exists in tileList
            }
            case "54" -> { //Cabal, add S11 cabal prison nearby
                Tile prison = new Tile("S11", "br");
                asyncMap.setTile(prison);
            }
        }

        if (asyncPosition == null) {
            System.out.println("    Could not map: " + ttpgPosition);
            return tile;
        }
        
        
        tile = new Tile(tileID, asyncPosition);
        String tileContents = matcher.group(4);

        Integer index = 0;
        String[] regions = tileContents.split(";");
        System.out.print(regions.length);

        //PER REGION/PLANET/UNITHOLDER
        for (String regionContents : regions) {
            Boolean isSpaceRegion = index == 0 ? true : false;
            Boolean isPlanetRegion = index > 0 ? true : false;
            String planetAlias = tileID + "_" + index;
            if (isSpaceRegion) {
                System.out.println("     spaceContents: " + regionContents);
            } else {

                String asyncPlanet = AliasHandler.resolvePlanet(planetAlias);
                System.out.println("     planet: " + planetAlias + ": " + asyncPlanet);
                System.out.println("         contents: " + regionContents);
                
                //Find attachments
                Matcher matcher2 = regionAttachmentsPattern.matcher(regionContents);
                Boolean hasAttachments = matcher2.find();
                String attachments = null;
                System.out.println("         hasAttachments: " + hasAttachments.toString());
                if (hasAttachments) {
                    attachments = matcher2.group(2);
                    for (Character attachment : attachments.toCharArray()) {
                        System.out.println("          - " + attachment + ": "+ AliasHandler.resolveTTPGAttachment(Character.toString(attachment)));
                        tile.addToken(AliasHandler.resolveTTPGAttachment(Character.toString(attachment)), planetAlias);
                    }
                }
            }


            String colour = "";
            Integer regionCount = 1;

            //DECODE REGION STRING, CHAR BY CHAR
            for (int i = 0; i < regionContents.length(); i++) {
                Character chr = regionContents.charAt(i);
                String str = Character.toString(chr);

                if (Character.isUpperCase(chr)) { //is a new Color, signify a new set of player's units
                    //reset colour & count
                    colour = AliasHandler.resolveColor(str.toLowerCase());
                    regionCount = 1;

                    System.out.println("            player: " + colour);
                    // playerUnits.put(colour, "");

                } else if (Character.isDigit(chr)) { // is a count, signify a new group of units
                    System.out.println("                count: " + str);
                    regionCount = Integer.valueOf(str);

                } else if (Character.isLowerCase(chr)) { // is a unit, control_token, or CC
                    if (!colour.equals("")){ //colour hasn't shown up yet, so probably just tokens in space, skip unit crap
                        if (str.equals("t")) { //CC
                            tile.addCC(Mapper.getCCID(colour));
                        } else if (str.equals("o")) { //control_token
                            tile.addToken(Mapper.getControlID(colour), AliasHandler.resolvePlanet(planetAlias));
                        } else { // is a unit
                            System.out.println("                unit:  " + AliasHandler.resolveTTPGUnit(str));
                            String asyncPlanet = isPlanetRegion ? " " + AliasHandler.resolvePlanet(planetAlias) : "";
                            String unit = AliasHandler.resolveTTPGUnit(str);
                            
                            
                            String unitID = Mapper.getUnitID(unit, colour);
                            String unitCount = String.valueOf(regionCount);
                            
                            if (isSpaceRegion) {
                                tile.addUnit("space", unitID, unitCount);
                            } else if (isPlanetRegion) {
                                tile.addUnit(AliasHandler.resolvePlanet(planetAlias), unitID, unitCount);
                            }
                            
                            // playerUnits.computeIfPresent(colour, (k, v) -> v + unitCount + " " + unit + asyncPlanet + ", ");
                        }
                    }

                    if (str.equals("e")) {
                        System.out.println("attempt to add frontier token to " + tile.getPosition());
                        tile.addToken(Constants.FRONTIER, Constants.SPACE);
                    }
                } else {
                    System.out.println("                what is this?  " + str);
                }
            }

            // System.out.println(playerUnits.toString());
            // for (Entry<String,String> unitString : playerUnits.entrySet()) {
            //     System.out.println(unitString.getKey());
            //     System.out.println(tileID);
            //     System.out.println(unitString.getValue());
            //     String unitID = Mapper.getUnitID(unit, color);
            // }



            index++;
        }

        //String color = Helper.getColor(activeMap, event);
        // String unitID = Mapper.getUnitID(unit, color);

        // if (matcher2.find()) {
        //     System.out.println("     Matches!");
        //     for (int i = 0; i < matcher.groupCount()-1; i++) {
        //         System.out.println("       group(" + i + "):" + matcher2.group(i));
        //     }          
        // } else {
        //     System.out.println("     No Match");
        // }
    
        return tile;
    }

    // private static String parseRegion() {

    // }


    public static String currentDateTime() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern( "uuuuMMddHHmmss" ));   
    }

    public static TTPGMap getTTPGMapFromJsonFile(String filePath) throws Exception {
        String jsonSource = readFileAsString(filePath);
        JsonNode node = parse(jsonSource);

        // System.out.println(generateString(node,true));

        TTPGMap ttpgMap = fromJson(node, TTPGMap.class);
        return ttpgMap;
    }


    public static String readFileAsString(String file)throws Exception
    {
        return new String(Files.readAllBytes(Paths.get(file)));
    }

    public static JsonNode parse(String source) throws JsonMappingException, JsonProcessingException {
        return objectMapper.readTree(source);
    }

    private static ObjectMapper objectMapper = new ObjectMapper();

    private static ObjectMapper getDefaultObjectMapper(){

        ObjectMapper defaultObjectMapper = new ObjectMapper();
        //CONFIGS
        defaultObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // defaultObjectMapper.configure(DeserializationFeature.fail, false);
        //END CONFIGS
        return defaultObjectMapper;
    }

    public static <A> A fromJson(JsonNode node, Class<A> clazz) throws JsonProcessingException, IllegalArgumentException {
        return objectMapper.treeToValue(node, clazz);
    }

    public static JsonNode toJson(Object a) {
        return objectMapper.valueToTree(a);
    }

    public static String generateString(JsonNode node) throws JsonProcessingException {
        return generateString(node, false);
    }

    public static String generatePrettyString(JsonNode node, Boolean prettyPrint) throws JsonProcessingException {
        return generateString(node, true);
    }

    public static String generateString(JsonNode node, Boolean prettyPrint) throws JsonProcessingException {
        ObjectWriter objectWriter = objectMapper.writer();
        if (prettyPrint)
            objectWriter = objectWriter.with(SerializationFeature.INDENT_OUTPUT);     
        return objectWriter.writeValueAsString(node);
    }
}
