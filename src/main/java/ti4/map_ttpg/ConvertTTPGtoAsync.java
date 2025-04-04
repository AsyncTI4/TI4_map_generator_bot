package ti4.map_ttpg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;

public class ConvertTTPGtoAsync {

    private static final List<String> validColors = new ArrayList<>() {
        {
            add("W"); //White
            add("B"); //Blue
            add("P"); //Purple
            add("Y"); //Yellow
            add("R"); //Red
            add("G"); //Green
            add("E"); //Orange
            add("K"); //Pink
        }
    };

    private static final List<String> validUnits = new ArrayList<>() {
        {
            add("c"); //carrier
            add("d"); //dreadnought
            add("f"); //fighter
            add("h"); //flagship
            add("i"); //infantry
            add("m"); //mech
            add("o"); //control_token
            add("p"); //pds
            add("r"); //cruiser
            add("s"); //space_dock
            add("t"); //command_token
            add("w"); //war_sun
            add("y"); //destroyer
        }
    };

    private static final List<String> validAttachments = new ArrayList<>() {
        {
            add("C"); //cybernetic_research_facility_face
            add("I"); //biotic_research_facility_face
            add("O"); //propulsion_research_facility_face
            add("W"); //warfare_research_facility_face
            add("a"); //alpha_wormhole
            add("b"); //beta_wormhole
            add("c"); //cybernetic_Research_Facility_back
            add("d"); //dyson_sphere
            add("e"); //frontier
            add("f"); //nano_forge
            add("g"); //gamma_wormhole
            add("h"); //grav_tear
            add("i"); //biotic_research_facility_back
            add("j"); //tomb_of_emphidia
            add("k"); //mirage
            add("l"); //stellar_converter
            add("m"); //mining_world
            add("n"); //ion_storm
            add("o"); //propulsion_research_facility_back
            add("p"); //paradise_world
            add("q"); //ul_sleeper
            add("r"); //rich_world
            add("t"); //ul_terraform
            add("u"); //ul_geoform
            add("w"); //warfare_research_facility_back
            add("x"); //lazax_survivors
            add("z"); //dmz
        }
    };

    public static final Map<String, String> fakePlayers = new HashMap<>() {
        {
            put(Constants.prisonerOneId, "PrisonerOne");
            put(Constants.tspId, "Holytispoon");
            put("947763140517560331", "TI4 Game Management");
            put("235148962103951360", "Carl-bot");
            put("812171459564011580", "RoboDane");
            put("572698679618568193", "Dicecord");
            put("814883082033037383", "Map Bot");
            put("936929561302675456", "Midjourney Bot");
            // put("345897843757678603", "TerTerro");
            // put("936295970671566879", "somno");
            // put("426282231234035722", "Son of Leto(UTC-6)");
            // put("960683086570487848", "TheEpicNerd");
            // put("1059869343636263023", "TI4-Bot-Test"); //PrisonerOne's Test Bot
        }
    };

    //Uncomment main for debugging locally without running the bot
    // public static void main(String[] args) throws Exception {
    //     PositionMapper.init();
    //     Mapper.init();
    //     AliasHandler.init();
    //     Storage.init();
    //     // String jsonSource = readFileAsString("storage/ttpg_exports/TTPG-Export.json");
    //     // JsonNode node = parse(jsonSource);
    //     TTPGMap ttpgMap = getTTPGMapFromJsonFile("storage/ttpg_exports/export_holyt2.json");

    //     Map map = ConvertTTPGMaptoAsyncMap(ttpgMap, "ttpgimport_dev");

    //     // JsonNode node = toJson(map);
    //     // System.out.println(generateString(node,true));

    //     MapSaveLoadManager.saveMap(map, "Converted from TTPG");
    //     // Map newMap = MapSaveLoadManager.loadMap
    // }

    public static Boolean ImportTTPG(String filename, String gamename) {
        try {
            File file = Storage.getTTPGExportStorage(filename);
            TTPGMap ttpgMap = getTTPGMapFromJsonFile(file);
            if (ttpgMap == null) {
                BotLogger.warning("TTPG Import Failed:\n> filename: " + filename + " is not valid TTPG export JSON format");
                return false;
            }
            Game game = ConvertTTPGMaptoAsyncMap(ttpgMap, gamename);
            GameManager.save(game, "Imported from TTPG");
        } catch (Exception e) {
            BotLogger.error("TTPG Import Failed: " + gamename + "    filename: " + filename, e);
            return false;
        }
        return true;
    }

    public static Game ConvertTTPGMaptoAsyncMap(TTPGMap ttpgMap, String gameName) {
        Mapper.init();
        Game asyncGame = new Game() {
            {
                setOwnerID(Constants.prisonerOneId);
                setOwnerName("PrisonerOne");
                setPlayerCountForMap(ttpgMap.getPlayers().size());
                setVp(ttpgMap.getScoreboard());
                setRound(ttpgMap.getRound());
                setName(gameName);
            }
        };

        //ADD STAGE 1 PUBLIC OBJECTIVES
        for (String objective : ttpgMap.getObjectives().getPublicObjectivesI()) {
            asyncGame.addSpecificStage1(AliasHandler.resolveObjective(objective));
        }

        //ADD STAGE 2 PUBLIC OBJECTIVES
        for (String objective : ttpgMap.getObjectives().getPublicObjectivesII()) {
            asyncGame.addSpecificStage2(AliasHandler.resolveObjective(objective));
        }

        //ADD CUSTOM PUBLIC OBJECTIVES FROM AGENDAS
        for (String objective : ttpgMap.getObjectives().getAgenda()) {
            asyncGame.addCustomPO(objective, 1);
            //asyncMap.addLaw(null, objective); TODO: if point from Law, make sure law is added
        }

        //ADD CUSTOM PUBLIC OBJECTIVES FROM RELICS
        for (String objective : ttpgMap.getObjectives().getRelics()) {
            asyncGame.addCustomPO(objective, 1);
        }

        //ADD CUSTOM PUBLIC OBJECTIVES FROM OTHER SOURCES
        // for (String objective : ttpgMap.getObjectives().getOther()) {
        //     asyncMap.addCustomPO(objective, 1);
        // }

        //EMPTY MAP FOR <AgendaName, Faction> to add Laws later
        Map<String, String> electedPlayers = new HashMap<>();

        //PLAYER ORDER MAPPING
        // TTPG player array starts in bottom right and goes clockwise
        // Async player array starts at top and goes clockwise
        // for 6 player games, need to shift ttpgPlayers 2 right - i.e. 0,1,2,3,4,5 -> 4,5,0,1,2,3

        //PLAYERS
        int index = ttpgMap.getPlayers().size() - 2;
        for (Entry<String, String> fakePlayer : fakePlayers.entrySet()) {
            asyncGame.addPlayer(fakePlayer.getKey(), fakePlayer.getValue());
            Player asyncPlayer = asyncGame.getPlayer(fakePlayer.getKey());
            TTPGPlayer ttpgPlayer = ttpgMap.getPlayers().get(index);

            //PLAYER STATS
            asyncPlayer.setFaction(asyncGame, AliasHandler.resolveFaction(ttpgPlayer.getFactionShort().toLowerCase()));
            asyncPlayer.setColor(AliasHandler.resolveColor(ttpgPlayer.getColorActual().toLowerCase()));
            asyncPlayer.setCommodities(ttpgPlayer.getCommodities());
            asyncPlayer.setCommoditiesTotal(ttpgPlayer.getMaxCommodities());
            asyncPlayer.setTg(ttpgPlayer.getTradeGoods());
            asyncPlayer.setTacticalCC(ttpgPlayer.getCommandTokens().getTactics());
            asyncPlayer.setFleetCC(ttpgPlayer.getCommandTokens().getFleet());
            asyncPlayer.setStrategicCC(ttpgPlayer.getCommandTokens().getStrategy());

            //PLAYER STRATEGY CARDS
            if (!ttpgPlayer.getStrategyCards().isEmpty()) {
                String ttpgSC = (String) ttpgPlayer.getStrategyCards().getFirst();
                if (Objects.nonNull(ttpgSC)) asyncPlayer.addSC(Helper.getSCNumber(ttpgSC));
            }
            if (!ttpgPlayer.getStrategyCardsFaceDown().isEmpty()) {
                String ttpgSCplayed = (String) ttpgPlayer.getStrategyCardsFaceDown().getFirst();
                if (Objects.nonNull(ttpgSCplayed)) asyncGame.setSCPlayed(Helper.getSCNumber(ttpgSCplayed), true);
            }

            //PLAYER SCORED OBJECTIVES
            for (String ttpgScoredObjective : ttpgPlayer.getObjectives()) {
                String asyncScoredObjective = AliasHandler.resolveObjective(ttpgScoredObjective);
                if (asyncGame.getSecretObjectives().contains(asyncScoredObjective)) {
                    asyncPlayer.setSecret(asyncScoredObjective);
                    for (Entry<String, Integer> secretObjective : asyncPlayer.getSecrets().entrySet()) {
                        if (secretObjective.getKey().equalsIgnoreCase(asyncScoredObjective)) {
                            asyncPlayer.setSecretScored(asyncScoredObjective, secretObjective.getValue());
                            asyncPlayer.removeSecret(secretObjective.getValue());
                        }
                    }

                } else if (asyncGame.getRevealedPublicObjectives().containsKey(asyncScoredObjective)) {
                    for (Entry<String, Integer> revealedObjective : asyncGame.getRevealedPublicObjectives().entrySet()) {
                        if (asyncScoredObjective.equalsIgnoreCase(revealedObjective.getKey())) {
                            asyncGame.scorePublicObjective(asyncPlayer.getUserID(), revealedObjective.getValue());
                        }
                    }
                } else if (asyncGame.getCustomPublicVP().containsKey(ttpgScoredObjective)) {
                    for (Entry<String, Integer> customObjective : asyncGame.getCustomPublicVP().entrySet()) {
                        if (ttpgScoredObjective.equalsIgnoreCase(customObjective.getKey())) {
                            asyncGame.scorePublicObjective(asyncPlayer.getUserID(), customObjective.getValue());
                        }
                    }
                }
            }

            //PLAYER LAWS ELECTED
            for (String ttpgLaw : ttpgPlayer.getLaws()) {
                String asyncLaw = AliasHandler.resolveAgenda(ttpgLaw);
                electedPlayers.put(asyncLaw, asyncPlayer.getFaction());
                if ("warrant".equals(asyncLaw)) asyncPlayer.flipSearchWarrant();
            }

            //PLAYER SUPPORT FOR THE THRONE
            for (String objective : ttpgPlayer.getObjectives()) {
                if (objective.startsWith("Support for the Throne")) {
                    asyncPlayer.addPromissoryNoteToPlayArea(AliasHandler.resolvePromissory(objective));
                }
            }

            //PLAYER PLANETS
            for (String planet : ttpgPlayer.getPlanetCards()) {
                asyncPlayer.addPlanet(AliasHandler.resolvePlanet(planet.toLowerCase()));
            }

            //PLAYER LEADERS
            if (!"keleres".equals(asyncPlayer.getFaction()) && !"nomad".equals(asyncPlayer.getFaction())) {
                // asyncPlayer.unsafeGetLeader("agent").setLocked(ttpgPlayer.getLeaders().getAgent().equals("unlocked") ? false : true);
                asyncPlayer.unsafeGetLeader("commander").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getCommander()));
                asyncPlayer.unsafeGetLeader("hero").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getHero()));
            } else if ("keleres".equals(asyncPlayer.getFaction())) {
                String subFaction = ttpgPlayer.getFactionShort().toLowerCase();
                switch (subFaction) {
                    case "keleres - argent" -> {
                        System.out.println(subFaction);
                        asyncPlayer.unsafeGetLeader("kuuasi").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getHero()));
                        asyncPlayer.removeLeader("odlynn");
                        asyncPlayer.removeLeader("harka");
                    }
                    case "keleres - xxcha" -> {
                        System.out.println(subFaction);
                        asyncPlayer.unsafeGetLeader("odlynn").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getHero()));
                        asyncPlayer.removeLeader("kuuasi");
                        asyncPlayer.removeLeader("harka");
                    }
                    case "keleres - mentak" -> {
                        System.out.println(subFaction);
                        asyncPlayer.unsafeGetLeader("harka").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getHero()));
                        asyncPlayer.removeLeader("kuuasi");
                        asyncPlayer.removeLeader("odlynn");
                    }
                }
            } else if ("nomad".equals(asyncPlayer.getFaction())) { //need an example before we do this
                // asyncPlayer.getLeader("agent").setLocked(ttpgPlayer.getLeaders().getAgent().equals("unlocked") ? false : true);
                asyncPlayer.unsafeGetLeader("commander").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getCommander()));
                asyncPlayer.unsafeGetLeader("hero").setLocked(!"unlocked".equals(ttpgPlayer.getLeaders().getHero()));
            }

            //PURGE HERO IF PURGED
            if ("purged".equals(ttpgPlayer.getLeaders().getHero())) asyncPlayer.removeLeader(Constants.HERO);

            //PLAYER CUSTODIAN POINTS
            Integer ttpgCustodianPoints = ttpgPlayer.getCustodiansPoints();
            if (ttpgCustodianPoints > 0) {
                while (ttpgCustodianPoints > 0) {
                    asyncGame.scorePublicObjective(asyncPlayer.getUserID(), 0);
                    ttpgCustodianPoints--;
                }
            }

            //PLAYER TECHS
            for (String technology : ttpgPlayer.getTechnologies()) {
                asyncPlayer.addTech(AliasHandler.resolveTech(technology.toLowerCase()));
            }

            //PLAYER RELICS
            for (String relic : ttpgPlayer.getRelicCards()) {
                asyncPlayer.addRelic(AliasHandler.resolveRelic(relic));
                asyncGame.getAllRelics().remove(AliasHandler.resolveRelic(relic));
            }

            //CLEAN PLAYER HANDCARDS
            asyncPlayer.clearPromissoryNotes();

            //PLAYER HANDCARDS and TABLECARDS
            List<String> handAndTableCards = new ArrayList<>() {
                {
                    addAll(ttpgPlayer.getHandCards());
                    addAll(ttpgPlayer.getTableCards());
                }
            };
            for (String card : handAndTableCards) {
                switch (determineCardType(card)) {
                    case "promissory" -> asyncPlayer.setPromissoryNote(AliasHandler.resolvePromissory(card));
                    case "action" -> asyncPlayer.setActionCard(AliasHandler.resolveActionCard(card));
                    case "secret" -> asyncPlayer.setSecret(AliasHandler.resolveObjective(card));
                    case "relic" -> asyncPlayer.addRelic(AliasHandler.resolveRelic(card));
                    case "fragment" -> asyncPlayer.addFragment(AliasHandler.resolveExploration(card));
                    default -> System.out.println("Could not add card to hand: " + card);
                }
            }

            //PLAYER ALLIANCES
            for (String alliance : ttpgPlayer.getAlliances()) {
                asyncPlayer.addPromissoryNoteToPlayArea(AliasHandler.resolvePromissory(alliance + "_an"));
            }

            //INDEX
            if (index == ttpgMap.getPlayers().size() - 1)
                index = 0;
            else
                index++; //shift ttpgPlayer array to match Async array
            if (asyncGame.getPlayers().size() == asyncGame.getPlayerCountForMap()) break;
        }

        //ADD TILES -> PARSE HEX SUMMARY
        String[] hexSummary = ttpgMap.getHexSummary().split(",");
        for (String hex : hexSummary) {
            System.out.println("Hex: " + hex);
            if (!hex.isEmpty()) {
                Tile tile = ConvertTTPGHexToAsyncTile(asyncGame, hex);
                if (tile != null) {
                    asyncGame.setTile(tile);
                } else {
                    System.out.println("null tile");
                }
            } else {
                System.out.println("0 length hex string");
            }
        }

        //ADD CONTROL TOKENS
        for (Tile tile : asyncGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                for (Player player : asyncGame.getPlayers().values()) {
                    for (String planet : player.getPlanets()) {
                        // System.out.println(unitHolder.getName() + "  " + planet + "   " + player.getColor());
                        if (unitHolder.getName().equalsIgnoreCase(planet)) {
                            tile.addControl(Mapper.getControlID(player.getColor()), planet);
                        }
                    }
                }
            }
        }

        // ACTION CARD DECK
        List<String> actionCards = new ArrayList<>() {
            {
                addAll(ttpgMap.getDecks().getCardAction().getDeck());
                replaceAll(AliasHandler::resolveActionCard);
            }
        };
        Collections.shuffle(actionCards);
        asyncGame.setActionCards(actionCards);

        // ACTION CARD DISCARD
        List<String> ttpgActionDiscards = ttpgMap.getDecks().getCardAction().getDiscard();
        List<String> actionDiscards = new ArrayList<>() {
            {
                if (Objects.nonNull(ttpgActionDiscards)) addAll(ttpgActionDiscards);
                replaceAll(AliasHandler::resolveActionCard);
            }
        };
        asyncGame.setDiscardActionCards(actionDiscards);

        // AGENDA DECK
        List<String> agendaCards = new ArrayList<>() {
            {
                addAll(ttpgMap.getDecks().getCardAgenda().getDeck());
                replaceAll(AliasHandler::resolveAgenda);
            }
        };
        Collections.reverse(agendaCards);
        asyncGame.setAgendas(agendaCards);

        // AGENDA DISCARD
        List<String> ttpgAgendaDiscards = ttpgMap.getDecks().getCardAgenda().getDiscard();
        List<String> ttpgLawsInPlay = ttpgMap.getLaws();
        List<String> agendaDiscards = new ArrayList<>() {
            {
                if (Objects.nonNull(ttpgAgendaDiscards)) addAll(ttpgAgendaDiscards);
                if (Objects.nonNull(ttpgLawsInPlay)) addAll(ttpgLawsInPlay);
                replaceAll(AliasHandler::resolveAgenda);
            }
        };
        asyncGame.setDiscardAgendas(agendaDiscards);

        //ADD LAWS
        for (String law : ttpgLawsInPlay) {
            int agendaID = asyncGame.getDiscardAgendas().get(AliasHandler.resolveAgenda(law));
            String electedFaction = electedPlayers.get(AliasHandler.resolveAgenda(law));
            asyncGame.addLaw(agendaID, electedFaction);
        }

        // EXPLORATION DECK
        List<String> ttpgExploreCulturalCards = ttpgMap.getDecks().getCardExplorationCultural().getDeck();
        List<String> ttpgExploreHazardousCards = ttpgMap.getDecks().getCardExplorationHazardous().getDeck();
        List<String> ttpgExploreIndustrialCards = ttpgMap.getDecks().getCardExplorationIndustrial().getDeck();
        List<String> ttpgExploreFrontierCards = ttpgMap.getDecks().getCardExplorationFrontier().getDeck();
        List<String> exploreCards = new ArrayList<>() {
            {
                if (Objects.nonNull(ttpgExploreCulturalCards)) addAll(ttpgExploreCulturalCards);
                if (Objects.nonNull(ttpgExploreHazardousCards)) addAll(ttpgExploreHazardousCards);
                if (Objects.nonNull(ttpgExploreIndustrialCards)) addAll(ttpgExploreIndustrialCards);
                if (Objects.nonNull(ttpgExploreFrontierCards)) addAll(ttpgExploreFrontierCards);
                replaceAll(AliasHandler::resolveExploration);
            }
        };
        Collections.shuffle(exploreCards);
        asyncGame.setExploreDeck(exploreCards);

        // EXPLORATION DISCARD
        List<String> ttpgExploreCulturalDiscards = ttpgMap.getDecks().getCardExplorationCultural().getDiscard();
        List<String> ttpgExploreHazardousDiscards = ttpgMap.getDecks().getCardExplorationHazardous().getDiscard();
        List<String> ttpgExploreIndustrialDiscards = ttpgMap.getDecks().getCardExplorationIndustrial().getDiscard();
        List<String> ttpgExploreFrontierDiscards = ttpgMap.getDecks().getCardExplorationFrontier().getDiscard();
        List<String> exploreDiscards = new ArrayList<>() {
            {
                if (Objects.nonNull(ttpgExploreCulturalDiscards)) addAll(ttpgExploreCulturalDiscards);
                if (Objects.nonNull(ttpgExploreHazardousDiscards)) addAll(ttpgExploreHazardousDiscards);
                if (Objects.nonNull(ttpgExploreIndustrialDiscards)) addAll(ttpgExploreIndustrialDiscards);
                if (Objects.nonNull(ttpgExploreFrontierDiscards)) addAll(ttpgExploreFrontierDiscards);
                replaceAll(AliasHandler::resolveExploration);
            }
        };
        asyncGame.setExploreDiscard(exploreDiscards);

        // RELIC DECK
        List<String> ttpgRelicCards = ttpgMap.getDecks().getCardRelic().getDeck();
        List<String> relicCards = new ArrayList<>() {
            {
                if (Objects.nonNull(ttpgRelicCards)) addAll(ttpgRelicCards);
                replaceAll(AliasHandler::resolveRelic);
            }
        };
        Collections.shuffle(relicCards);
        asyncGame.setRelics(relicCards);

        // STAGE 1 PUBLIC OBJECTIVE DECK
        List<String> publicCardsStage1 = new ArrayList<>() {
            {
                addAll(ttpgMap.getObjectives().getPublicObjectivesII());
                replaceAll(AliasHandler::resolveObjective);
            }
        };
        asyncGame.getPublicObjectives1().removeAll(publicCardsStage1);

        // STAGE 2 PUBLIC OBJECTIVE DECK
        List<String> publicCardsStage2 = new ArrayList<>() {
            {
                addAll(ttpgMap.getObjectives().getPublicObjectivesII());
                replaceAll(AliasHandler::resolveObjective);
            }
        };
        asyncGame.getPublicObjectives2().removeAll(publicCardsStage2);

        // SECRET OBJECTIVE DECK
        List<String> secretCards = new ArrayList<>() {
            {
                addAll(ttpgMap.getDecks().getCardObjectiveSecret().getDeck());
                replaceAll(AliasHandler::resolveObjective);
            }
        };
        Collections.shuffle(secretCards);
        asyncGame.setSecretObjectives(secretCards);

        // TG ON STRAT CARDS
        asyncGame.setScTradeGood(1, ttpgMap.getUnpickedStrategyCards().getLeadership());
        asyncGame.setScTradeGood(2, ttpgMap.getUnpickedStrategyCards().getDiplomacy());
        asyncGame.setScTradeGood(3, ttpgMap.getUnpickedStrategyCards().getPolitics());
        asyncGame.setScTradeGood(4, ttpgMap.getUnpickedStrategyCards().getConstruction());
        asyncGame.setScTradeGood(5, ttpgMap.getUnpickedStrategyCards().getTrade());
        asyncGame.setScTradeGood(6, ttpgMap.getUnpickedStrategyCards().getWarfare());
        asyncGame.setScTradeGood(7, ttpgMap.getUnpickedStrategyCards().getTechnology());
        asyncGame.setScTradeGood(8, ttpgMap.getUnpickedStrategyCards().getImperial());

        return asyncGame;
    }

    private static String determineCardType(String card) {
        if (Mapper.getAllPromissoryNoteIDs().contains(AliasHandler.resolvePromissory(card))) {
            return "promissory";
        } else if (Mapper.getActionCards().containsKey(AliasHandler.resolveActionCard(card))) {
            return "action";
        } else if (Mapper.getSecretObjectives().containsKey(AliasHandler.resolveObjective(card))) {
            return "secret";
        } else if (Mapper.getRelics().containsKey(AliasHandler.resolveRelic(card))) {
            return "relic";
        } else if (Mapper.getExplores().containsKey(AliasHandler.resolveExploration(card))) {
            if (card.contains("Fragment")) {
                return "fragment";
            }
            return "explore";
        } else {
            return "unknown";
        }
    }

    public static Tile ConvertTTPGHexToAsyncTile(Game asyncGame, String ttpgHex) {
        // System.out.println(" Examining hex summary:  " + ttpgHex);

        // TILE +-X +-Y SPACE ; PLANET1 ; PLANET2 ; ...
        Pattern firstRegionPattern = Pattern.compile("^([0-9AB]+)([-+][0-9]+)([-+][0-9]+)(.*)?$");
        //Pattern rotPattern = Pattern.compile("^(\\d+)([AB])(\\d)$"); //ignore hyperlanes for now
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

        String tileID = AliasHandler.resolveTile(matcher.group(1));
        String ttpgXPosition = matcher.group(2);
        String ttpgYPosition = matcher.group(3);
        String ttpgPosition = ttpgXPosition + ttpgYPosition;

        String asyncPosition = AliasHandler.resolveTTPGPosition(ttpgPosition);

        //Handle special cases, tiles to go in TL/TR/BL/BR
        switch (tileID) {
            //TODO: smart placement of mallice/whdelta/nombox
            case "82" -> { //Mallice
                tileID = "82b"; //TODO: If 82 hasunits or control, then 82b, otherwise, 82a
                asyncPosition = "tl"; //hardcode top left for now
            }
            case "51" -> //Creuss
                //TODO: move DeltaWH if exists in tileList
                asyncPosition = "tr"; //hardcode top right for now
            case "17" -> { //DeltaWH
                //TODO: move Creuss if exists in tileList - i.e. if 17 is near BL, put 51 in BL
            }
        }

        if (asyncPosition == null) {
            System.out.println("    Could not map: " + ttpgPosition);
            return null;
        }

        //PER REGION/PLANET/UNITHOLDER
        Tile tile = new Tile(tileID, asyncPosition);
        String tileContents = matcher.group(4);
        int index = 0;
        String[] regions = tileContents.split(";");
        System.out.println("# of regions: " + regions.length);
        for (String regionContents : regions) {
            boolean regionIsSpace = index == 0;
            boolean regionIsPlanet = index > 0;

            String planetAlias = tileID + "_" + index; //unique planet ID in planet_alias.properties
            String planet = AliasHandler.resolvePlanet(planetAlias);

            if (regionIsSpace) {
                System.out.println("     spaceContents: " + regionContents);
            } else {
                System.out.println("     planet: " + planetAlias + ": " + planet);
                System.out.println("         contents: " + regionContents);
            }

            //Find attachments, and split off region
            Matcher matcherAttachments = regionAttachmentsPattern.matcher(regionContents);
            boolean hasAttachments = matcherAttachments.find();
            String attachments;
            System.out.println("         hasAttachments: " + hasAttachments);
            if (hasAttachments) {
                regionContents = matcherAttachments.group(1);
                attachments = matcherAttachments.group(2);
                for (char attachment : attachments.toCharArray()) {
                    if (!validAttachments.contains(String.valueOf(attachment))) {
                        String attachment_proper = attachment + (Character.isUpperCase(attachment) ? "_cap" : ""); //bypass AliasHandler's toLowercase'ing
                        String attachmentResolved = AliasHandler.resolveTTPGAttachment(attachment_proper);
                        System.out.println("          - " + attachment + ": " + attachmentResolved);

                        String attachmentFileName = Mapper.getAttachmentImagePath(attachmentResolved);
                        String tokenFileName = Mapper.getTokenID(attachmentResolved);

                        if (tokenFileName != null && regionIsPlanet) {
                            tile.addToken(tokenFileName, planet);
                        } else if (attachmentFileName != null && regionIsPlanet) {
                            tile.addToken(attachmentFileName, planet);
                        } else if (tokenFileName != null && regionIsSpace) {
                            tile.addToken(tokenFileName, Constants.SPACE);
                        } else if (attachmentFileName != null && regionIsSpace) {
                            tile.addToken(attachmentFileName, Constants.SPACE);
                        } else {
                            System.out.println("          - " + attachmentResolved + " could not be added - not found");
                        }

                        if (Constants.MIRAGE.equalsIgnoreCase(attachmentResolved)) {
                            Helper.addMirageToTile(tile);
                            tile.addToken(tokenFileName, Constants.SPACE);
                        }
                    } else {
                        System.out.println("                character not recognized:  " + attachment);
                    }

                }
            }

            String color = "";
            Integer regionCount = 1;

            //DECODE REGION STRING, CHAR BY CHAR
            for (int i = 0; i < regionContents.length(); i++) {
                char chr = regionContents.charAt(i);
                String str = Character.toString(chr);

                if (validColors.contains(str)) { //is a new Color, signify a new set of player's units //MAY ALSO BE AN ATTACHMENT???
                    //reset color & count
                    color = AliasHandler.resolveColor(str.toLowerCase());
                    regionCount = 1;

                    System.out.println("            player: " + color);

                } else if (Character.isDigit(chr)) { // is a count, signify a new group of units
                    System.out.println("                count: " + str);
                    regionCount = Integer.valueOf(str);

                } else if (Character.isLowerCase(chr) && validUnits.contains(str)) { // is a unit, control_token, or CC
                    if (!color.isEmpty()) { //color hasn't shown up yet, so probably just tokens in space, skip unit crap
                        if ("t".equals(str)) { //CC
                            tile.addCC(Mapper.getCCID(color));
                        } else if ("o".equals(str)) { //control_token
                            tile.addToken(Mapper.getControlID(color), AliasHandler.resolvePlanet(planetAlias));
                        } else { // is a unit
                            System.out.println("                unit:  " + AliasHandler.resolveTTPGUnit(str));
                            String unit = AliasHandler.resolveTTPGUnit(str);

                            UnitKey unitID = Mapper.getUnitKey(unit, color);
                            String unitCount = String.valueOf(regionCount);

                            if (regionIsSpace) {
                                tile.addUnit("space", unitID, unitCount);
                            } else if (regionIsPlanet) {
                                tile.addUnit(AliasHandler.resolvePlanet(planetAlias), unitID, unitCount);
                            }

                        }
                    }

                } else if (validAttachments.contains(str)) { //attachments that were there that didn't match the RegEx above
                    if ("e".equals(str)) { //frontier token
                        System.out.println("attempt to add frontier token to " + tile.getPosition());
                        // tile.addToken(Mapper.getTokenPath(Constants.FRONTIER), Constants.SPACE);
                        AddTokenCommand.addToken(null, tile, Constants.FRONTIER, null);
                    }
                } else {
                    System.out.println("                character not recognized:  " + str);
                }
            }

            index++; //next Region/Planet/UnitHolder
        }

        return tile;
    }

    public static String currentDateTime() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss"));
    }

    public static TTPGMap getTTPGMapFromJsonFile(String filePath) throws Exception {
        String jsonSource = readFileAsString(filePath);
        if (isValid(jsonSource)) {
            JsonNode node = parse(jsonSource);
            return fromJson(node, TTPGMap.class);
        }
        return null;
    }

    public static TTPGMap getTTPGMapFromJsonFile(File file) throws Exception {
        String jsonSource = readFileAsString(file);
        if (isValid(jsonSource)) {
            JsonNode node = parse(jsonSource);
            return fromJson(node, TTPGMap.class);
        }
        return null;
    }

    public static String readFileAsString(String filepath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(filepath)));
    }

    public static String readFileAsString(File file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
    }

    public static JsonNode parse(String source) throws JsonProcessingException {
        return objectMapper.readTree(source);
    }

    public static boolean isValid(String json) {
        try {
            objectMapper.readTree(json);
        } catch (JacksonException e) {
            return false;
        }
        return true;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <A> A fromJson(JsonNode node, Class<A> clazz) throws JsonProcessingException, IllegalArgumentException {
        return objectMapper.treeToValue(node, clazz);
    }

    public static JsonNode toJson(Object a) {
        return objectMapper.valueToTree(a);
    }

    public static String generateString(JsonNode node) throws JsonProcessingException {
        return generateString(node, false);
    }

    public static String generatePrettyString(JsonNode node) throws JsonProcessingException {
        return generateString(node, true);
    }

    public static String generateString(JsonNode node, Boolean prettyPrint) throws JsonProcessingException {
        ObjectWriter objectWriter = objectMapper.writer();
        if (prettyPrint)
            objectWriter = objectWriter.with(SerializationFeature.INDENT_OUTPUT);
        return objectWriter.writeValueAsString(node);
    }
}
