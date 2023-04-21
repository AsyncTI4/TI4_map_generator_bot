package ti4.map;

import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.player.PlanetRemove;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.message.BotLogger;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class Map {

    private String ownerID;
    private String ownerName = "";
    private String name;

    private String latestCommand = "";

    private MiltyDraftManager miltyDraftManager;

    private HashMap<String, UnitHolder> planets = new HashMap<>();

    @Nullable
    private DisplayType displayTypeForced = null;
    @ExportableField
    private int playerCountForMap = 6;

    @ExportableField
    private int ringCount = 0;
    @ExportableField
    private int vp = 10;
    @ExportableField
    private boolean communityMode = false;
    @ExportableField
    private boolean allianceMode = false;
    @ExportableField
    private boolean fowMode = false;
    @ExportableField
    private String largeText = "small";
    @ExportableField
    private boolean absolMode = false;
    @ExportableField
    private boolean discordantStarsMode = false;
    private boolean hasEnded = false;

    @Nullable
    private MessageChannel tableTalkChannel = null;
    @Nullable
    private MessageChannel mainChannel = null;
    @Nullable
    private ThreadChannel botMapChannel = null;
    
    //UserID, UserName
    private LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    @ExportableField
    private MapStatus mapStatus = MapStatus.open;

    private HashMap<Integer, Boolean> scPlayed = new HashMap<>();
    @ExportableField
    private String speaker = "";
    @ExportableField
    private String creationDate;
    @ExportableField
    private String customName = "";
    @ExportableField
    private long lastModifiedDate;
    @ExportableField
    private int round = 1;

    @ExportableField
    private String activePlayer = null;
    private Date lastActivePlayerPing = new Date(0);
    private Date lastActivePlayerChange = new Date(0);

    private List<String> secretObjectives;
    private List<String> actionCards;
    private LinkedHashMap<String, Integer> discardActionCards = new LinkedHashMap<>();

    private List<String> agendas;
    private LinkedHashMap<Integer, Integer> scTradeGoods = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> discardAgendas = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> sentAgendas = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> laws = new LinkedHashMap<>();
    private LinkedHashMap<String, String> lawsInfo = new LinkedHashMap<>();
    @ExportableField
    private LinkedHashMap<String, Integer> revealedPublicObjectives = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> customPublicVP = new LinkedHashMap<>();
    private LinkedHashMap<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>();
    private LinkedHashMap<String, List<String>> customAdjacentTiles = new LinkedHashMap<>();
    private LinkedHashMap<Pair<String, Integer>, String> adjacencyOverrides = new LinkedHashMap<>();
    private ArrayList<String> publicObjectives1 = new ArrayList<>();
    private ArrayList<String> publicObjectives2 = new ArrayList<>();
    private ArrayList<String> soToPoList = new ArrayList<>();
    private ArrayList<String> purgedPN = new ArrayList<>();

    private ArrayList<String> explore = new ArrayList<>();
    private ArrayList<String> discardExplore = new ArrayList<>();
    private ArrayList<String> relics = new ArrayList<>();

    private static HashMap<Player, Integer> playerVPs = new HashMap<>();

    public Map() {
        creationDate = Helper.getDateRepresentation(new Date().getTime());
        lastModifiedDate = new Date().getTime();
        miltyDraftManager = new MiltyDraftManager();

        HashMap<String, String> secretObjectives = Mapper.getSecretObjectives();
        this.secretObjectives = new ArrayList<>(secretObjectives.keySet());
        Collections.shuffle(this.secretObjectives);

        HashMap<String, String> actionCards = Mapper.getActionCards();
        this.actionCards = new ArrayList<>(actionCards.keySet());
        Collections.shuffle(this.actionCards);

        resetAgendas();

        Set<String> po1 = Mapper.getPublicObjectivesState1().keySet();
        Set<String> po2 = Mapper.getPublicObjectivesState2().keySet();
        publicObjectives1.addAll(po1);
        publicObjectives2.addAll(po2);
        Collections.shuffle(publicObjectives1);
        Collections.shuffle(publicObjectives2);
        addCustomPO(Constants.CUSTODIAN, 1);

        Set<String> exp = Mapper.getExplores().keySet();
        explore.addAll(exp);
        Collections.shuffle(explore);
        resetRelics();

        //Default SC initialization
        for (int i = 0; i < 8; i++) {
            scTradeGoods.put(i + 1, 0);
        }
    }

    synchronized public Map copy() {
        Map m = this;
        return m;
    }

    public HashMap<String, Object> getExportableFieldMap() {
        Class<? extends Map> aClass = this.getClass();
        Field[] fields = aClass.getDeclaredFields();
        HashMap<String, Object> returnValue = new HashMap<>();

        for (Field field : fields) {
            if (field.getDeclaredAnnotation(ExportableField.class) != null) {
                try {
                    returnValue.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    // This shouldn't really happen since we
                    // can even see private fields.
                    BotLogger.log("Unknown error exporting fields from map.", e);
                }
            }
        }
        return returnValue;
    }

    public String getLatestCommand() {
        return latestCommand;
    }

    public void setLatestCommand(String latestCommand) {
        this.latestCommand = latestCommand;
    }

    public MiltyDraftManager getMiltyDraftManager() {
        return miltyDraftManager;
    }

    public void setPurgedPN(String purgedPN) {
        this.purgedPN.add(purgedPN);
    }

    public void removePurgedPN(String purgedPN){
        this.purgedPN.remove(purgedPN);
    }

    public void setPurgedPNs(ArrayList<String> purgedPN) {
        this.purgedPN = purgedPN;
    }

    public ArrayList<String> getPurgedPN() {
        return purgedPN;
    }

    public int getVp() {
        return vp;
    }

    public void setVp(int vp) {
        this.vp = vp;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        if (round <= 0) {
            this.round = 1;
        } else {
            this.round = round;
        }
    }

    public String getCreationDate() {
        return creationDate;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    //GAME MODES
    public boolean isCommunityMode() {
        return communityMode;
    }

    public void setCommunityMode(boolean communityMode) {
        this.communityMode = communityMode;
    }

    public boolean isAllianceMode() {
        return allianceMode;
    }

    public void setAllianceMode(boolean allianceMode) {
        this.allianceMode = allianceMode;
    }

    public boolean isFoWMode() {
        return fowMode;
    }

    public void setFoWMode(boolean fowMode) {
        this.fowMode = fowMode;
    }

    public void setLargeText(String largeText) {
        this.largeText = largeText;
    }
    public String getLargeText() {
        return largeText;
    }

    public boolean isAbsolMode() {
        return absolMode;
    }

    public void setAbsolMode(boolean absolMode) {
        this.absolMode = absolMode;
    }

    public boolean isDiscordantStarsMode() {
        return discordantStarsMode;
    }

    public void setDiscordantStarsMode(boolean discordantStarsMode) {
        this.discordantStarsMode = discordantStarsMode;
    }

    public String getGameModesText() {
        HashMap<String,Boolean> gameModes = new HashMap<>() {{
            put("Community", isCommunityMode());
            put("Alliance", isAllianceMode());
            put("FoW", isFoWMode());
            put("Absol", isAbsolMode());
            put("DiscordantStars", isDiscordantStarsMode());
        }};
        return gameModes.entrySet().stream().filter(gm -> gm.getValue()).map(java.util.Map.Entry::getKey).collect(Collectors.joining(", "));
    }

    public void setTableTalkChannel(MessageChannel channel) {
        tableTalkChannel = channel;
    }

    public MessageChannel getTableTalkChannel() {
        return tableTalkChannel;
    }

    public void setMainGameChannel(MessageChannel channel) {
        mainChannel = channel;
    }

    public MessageChannel getMainGameChannel() {
        return mainChannel;
    }

    public void setActionChannel(MessageChannel channel) {
        mainChannel = channel;
    }

    public MessageChannel getActionChannel() {
        return mainChannel;
    }

    public void setBotMapChannel(ThreadChannel channel) {
        botMapChannel = channel;
    }

    public ThreadChannel getBotMapChannel() {
        return botMapChannel;
    }

    public boolean isHasEnded() {
        return hasEnded;
    }

    public void setHasEnded(boolean hasEnded) {
        this.hasEnded = hasEnded;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public long getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(long lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    //Position, Tile
    private HashMap<String, Tile> tileMap = new HashMap<>();

    public HashMap<Integer, Boolean> getScPlayed() {
        return scPlayed;
    }

    public DisplayType getDisplayTypeForced() {
        return displayTypeForced;
    }

    public void setDisplayTypeForced(DisplayType displayTypeForced) {
        this.displayTypeForced = displayTypeForced;
    }

    public int getPlayerCountForMap() {
        return playerCountForMap;
    }

    public void setPlayerCountForMap(int playerCountForMap) {
        this.playerCountForMap = playerCountForMap;
    }

    public int getRingCount() {
        return ringCount;
    }

    public void setRingCount(int ringCount) {
        this.ringCount = ringCount;
    }

    public void setSCPlayed(Integer scNumber, Boolean playedStatus) {
        this.scPlayed.put(scNumber, playedStatus);
    }

    public String getSpeaker() {
        return speaker;
    }

    /**
     * @param speaker - The player's userID: player.getID()
     */
    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    public String getActivePlayer() {
        return activePlayer;
    }

    public void updateActivePlayer(Player player) {
        /// update previous active player stats
        Date newTime = new Date();
        if (activePlayer != null) {
            Player prevPlayer = getPlayer(activePlayer);
            if (prevPlayer != null) {
                long elapsedTime = newTime.getTime() - lastActivePlayerChange.getTime();
                prevPlayer.updateTurnStats(elapsedTime);
            }
        }

        // reset timers for ping and stats
        setActivePlayer(player == null ? null : player.getUserID());
        setLastActivePlayerChange(newTime);
        setLastActivePlayerPing(newTime);
    }

    /**
     * @param player - The player's userID: player.getID()
     */
    public void setActivePlayer(String player) {
        this.activePlayer = player;
    }

    public Date getLastActivePlayerPing() {
        return lastActivePlayerPing;
    }

    public void setLastActivePlayerPing(Date time) {
        this.lastActivePlayerPing = time;
    }

    public Date getLastActivePlayerChange() {
        return lastActivePlayerChange;
    }

    public void setLastActivePlayerChange(Date time) {
        this.lastActivePlayerChange = time;
    }

    public void setSentAgenda(String id) {
        Collection<Integer> values = sentAgendas.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
        }
        sentAgendas.put(id, identifier);
    }

    public void addDiscardAgenda(String id) {
        Collection<Integer> values = discardAgendas.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
        }
        discardAgendas.put(id, identifier);
    }

    public void addRevealedPublicObjective(String id) {
        Collection<Integer> values = revealedPublicObjectives.values();
        int identifier = 0;
        while (values.contains(identifier)) {
            identifier++;
        }
        revealedPublicObjectives.put(id, identifier);
    }

    public LinkedHashMap<Integer, Integer> getScTradeGoods() {
        return scTradeGoods;
    }

    public void setScTradeGoods(LinkedHashMap<Integer, Integer> scTradeGoods) {
        this.scTradeGoods = scTradeGoods;
    }

    public void setScTradeGood(Integer sc, Integer tradeGoodCount) {
        if (Objects.isNull(tradeGoodCount)) tradeGoodCount = 0;
        scTradeGoods.put(sc, tradeGoodCount);
    }

    public void setScPlayed(HashMap<Integer, Boolean> scPlayed) {
        this.scPlayed = scPlayed;
    }

    public LinkedHashMap<String, Integer> getRevealedPublicObjectives() {
        return revealedPublicObjectives;
    }

    public ArrayList<String> getPublicObjectives1() {
        return publicObjectives1;
    }

    public ArrayList<String> getPublicObjectives2() {
        return publicObjectives2;
    }
    
    public java.util.Map.Entry<String, Integer> revealState1() {
        return revealObjective(publicObjectives1);
    }

    public java.util.Map.Entry<String, Integer> revealState2() {
        return revealObjective(publicObjectives2);
    }
    
    public java.util.Map.Entry<String, Integer> revealObjective(ArrayList<String> objectiveList) {
        if (!objectiveList.isEmpty()) {
            String id = objectiveList.get(0);
            objectiveList.remove(id);
            addRevealedPublicObjective(id);
            for (java.util.Map.Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }
    
    public java.util.Map.Entry<String, Integer> addSpecificStage1(String objective) {
        return addSpecificObjective(publicObjectives1, objective);
    }

    public java.util.Map.Entry<String, Integer> addSpecificStage2(String objective) {
        return addSpecificObjective(publicObjectives2, objective);
    }

    public java.util.Map.Entry<String, Integer> addSpecificObjective(ArrayList<String> objectiveList, String objective) {
        if (!objectiveList.isEmpty()) {
            objectiveList.remove(objective);
            addRevealedPublicObjective(objective);
            for (java.util.Map.Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(objective)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public boolean shuffleObjectiveBackIntoDeck(Integer idNumber) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            revealedPublicObjectives.remove(id);
            Set<String> po1 = Mapper.getPublicObjectivesState1().keySet();
            Set<String> po2 = Mapper.getPublicObjectivesState2().keySet();
            if (po1.contains(id)) {
                publicObjectives1.add(id);
                Collections.shuffle(publicObjectives1);
            } else if (po2.contains(id)) {
                publicObjectives2.add(id);
                Collections.shuffle(publicObjectives2);
            }
            return true;
        }
        return false;
    }

    public boolean scorePublicObjective(String userID, Integer idNumber) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
            if (!Constants.CUSTODIAN.equals(id) && scoredPlayerList.contains(userID)) {
                return false;
            }
            scoredPlayerList.add(userID);
            scoredPublicObjectives.put(id, scoredPlayerList);
            return true;
        }
        return false;
    }

    public boolean unscorePublicObjective(String userID, Integer idNumber) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
            if (scoredPlayerList.contains(userID)) {
                scoredPlayerList.remove(userID);
                scoredPublicObjectives.put(id, scoredPlayerList);
                return true;
            }
        }
        return false;
    }

    public Integer addCustomPO(String poName, int vp) {
        customPublicVP.put(poName, vp);
        addRevealedPublicObjective(poName);
        return revealedPublicObjectives.get(poName);
    }

    public boolean removeCustomPO(Integer idNumber) {

        String id = "";
        for (java.util.Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            revealedPublicObjectives.remove(id);
            soToPoList.remove(id);
            customPublicVP.remove(id);
            scoredPublicObjectives.remove(id);
            return true;
        }
        return false;
    }

    public boolean removeSOFromGame(String id) {
        return secretObjectives.remove(id);
    }

    public boolean removeACFromGame(String id) {
        return actionCards.remove(id);
    }

    public boolean removeAgendaFromGame(String id) {
        return agendas.remove(id);
    }

    public LinkedHashMap<String, Integer> getCustomPublicVP() {
        return customPublicVP;
    }

    public void setCustomPublicVP(LinkedHashMap<String, Integer> customPublicVP) {
        this.customPublicVP = customPublicVP;
    }

    public void setRevealedPublicObjectives(LinkedHashMap<String, Integer> revealedPublicObjectives) {
        this.revealedPublicObjectives = revealedPublicObjectives;
    }

    public void setScoredPublicObjectives(LinkedHashMap<String, List<String>> scoredPublicObjectives) {
        this.scoredPublicObjectives = scoredPublicObjectives;
    }

    public void setCustomAdjacentTiles(LinkedHashMap<String, List<String>> customAdjacentTiles) {
        this.customAdjacentTiles = customAdjacentTiles;
    }

    public void addCustomAdjacentTiles(String primaryTile, List<String> customAdjacentTiles) {
        this.customAdjacentTiles.put(primaryTile, customAdjacentTiles);
    }

    public void removeCustomAdjacentTiles(String primaryTile) {
        this.customAdjacentTiles.remove(primaryTile);
    }

    public void clearCustomAdjacentTiles() {
        this.customAdjacentTiles.clear();
    }

    public void setPublicObjectives1(ArrayList<String> publicObjectives1) {
        this.publicObjectives1 = publicObjectives1;
    }

    public void setPublicObjectives2(ArrayList<String> publicObjectives2) {
        this.publicObjectives2 = publicObjectives2;
    }

    public ArrayList<String> getSoToPoList() {
        return soToPoList;
    }

    public void setSoToPoList(ArrayList<String> soToPoList) {
        this.soToPoList = soToPoList;
    }

    public void addToSoToPoList(String id) {
        soToPoList.add(id);
    }

    public void removeFromSoToPoList(String id) {
        soToPoList.remove(id);
    }



    public LinkedHashMap<String, List<String>> getScoredPublicObjectives() {
        return scoredPublicObjectives;
    }

    public LinkedHashMap<String, List<String>> getCustomAdjacentTiles() {
        return customAdjacentTiles;
    }

    public LinkedHashMap<Pair<String, Integer>, String> getAdjacentTileOverrides() {
        return adjacencyOverrides;
    }

    public void addAdjacentTileOverride(String primaryTile, int direction, String secondaryTile) {
        Pair<String, Integer> primary = new ImmutablePair<String,Integer>(primaryTile, direction);
        Pair<String, Integer> secondary = new ImmutablePair<String,Integer>(secondaryTile, (direction + 3) % 6);

        adjacencyOverrides.put(primary, secondaryTile);
        adjacencyOverrides.put(secondary, primaryTile);
    }

    public void setAdjacentTileOverride(LinkedHashMap<Pair<String, Integer>, String> overrides) {
        adjacencyOverrides = overrides;
    }

    public void clearAdjacentTileOverrides() {
        adjacencyOverrides.clear();
    }

    public void removeAdjacentTileOverrides(String primary) {
        for (int i = 0; i < 6; i++) {
            String secondary = getAdjacentTileOverride(primary, i);
            int j = (i + 3) % 6;

            if (secondary != null) {
                adjacencyOverrides.remove(new ImmutablePair<String, Integer>(primary, i));
                adjacencyOverrides.remove(new ImmutablePair<String, Integer>(secondary, j));
            }
        }
    }

    public List<String> getAdjacentTileOverrides(String position) {
        List<String> output = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String secondary = getAdjacentTileOverride(position, i);
            output.add(secondary);
        }
        return output;
    }

    public String getAdjacentTileOverride(String position, int direction) {
        Pair<String, Integer> primary = new ImmutablePair<String, Integer>(position, direction);
        if (adjacencyOverrides.containsKey(primary)) {
            return adjacencyOverrides.get(primary);
        }
        return null;
    }

    public LinkedHashMap<String, Integer> getLaws() {
        return laws;
    }

    public LinkedHashMap<String, String> getLawsInfo() {
        return lawsInfo;
    }

    public void setAgendas(List<String> agendas) {
        this.agendas = agendas;
    }

    public void shuffleAgendas() {
        Collections.shuffle(agendas);
    }

    public void resetAgendas() {
        HashMap<String, String> agendas = Mapper.getAgendas(); //ALL agendas, including absol
        if (this.absolMode) {
            this.agendas = new ArrayList<>(agendas.keySet().stream().filter(a -> a.startsWith("absol_")).toList());
        } else { //ALL agendas, except absol - if more decks get added, this will need to be rebuilt
            this.agendas = new ArrayList<>(agendas.keySet().stream().filter(Predicate.not(a -> a.startsWith("absol_"))).toList());
        }
        Collections.shuffle(this.agendas);
        discardAgendas = new LinkedHashMap<>();
    }

    public void resetDrawStateAgendas() {
        sentAgendas.clear();
    }

    public void setDiscardAgendas(LinkedHashMap<String, Integer> discardAgendas) {
        this.discardAgendas = discardAgendas;
    }

    public void setDiscardAgendas(ArrayList<String> discardAgendasList) {
        LinkedHashMap<String, Integer> discardAgendas = new LinkedHashMap<>();
        for (String card : discardAgendasList) {
            Collection<Integer> values = discardAgendas.values();
            int identifier = new Random().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = new Random().nextInt(1000);
            }
            discardAgendas.put(card, identifier);
        }
        this.discardAgendas = discardAgendas;
    }

    public void setSentAgendas(LinkedHashMap<String, Integer> sentAgendas) {
        this.sentAgendas = sentAgendas;
    }

    public void setLaws(LinkedHashMap<String, Integer> laws) {
        this.laws = laws;
    }

    public void setLawsInfo(LinkedHashMap<String, String> lawsInfo) {
        this.lawsInfo = lawsInfo;
    }

    public List<String> getAgendas() {
        return agendas;
    }

    public LinkedHashMap<String, Integer> getSentAgendas() {
        return sentAgendas;
    }

    public LinkedHashMap<String, Integer> getDiscardAgendas() {
        return discardAgendas;
    }

    public boolean addLaw(Integer idNumber, String optionalText) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {

            Collection<Integer> values = laws.values();
            int identifier = new Random().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = new Random().nextInt(1000);
            }
            discardAgendas.remove(id);
            laws.put(id, identifier);
            if (optionalText != null) {
                lawsInfo.put(id, optionalText);
            }
            return true;
        }
        return false;
    }

    public boolean shuffleBackIntoDeck(Integer idNumber) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardAgendas.remove(id);
            agendas.add(id);
            shuffleAgendas();
            return true;
        }
        return false;
    }

    public boolean removeLaw(Integer idNumber) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> ac : laws.entrySet()) {
            if (ac.getValue().equals(idNumber)) {
                id = ac.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            laws.remove(id);
            lawsInfo.remove(id);
            addDiscardAgenda(id);
            return true;
        }
        return false;
    }

    public boolean putAgendaTop(Integer idNumber) {
        if (sentAgendas.containsValue(idNumber)) {

            String id = "";
            for (java.util.Map.Entry<String, Integer> ac : sentAgendas.entrySet()) {
                if (ac.getValue().equals(idNumber)) {
                    id = ac.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                agendas.remove(id);
                agendas.add(0, id);
                sentAgendas.remove(id);
                return true;
            }
        }
        return false;
    }

    public boolean putAgendaBottom(Integer idNumber) {
        if (sentAgendas.containsValue(idNumber)) {
            String id = "";
            for (java.util.Map.Entry<String, Integer> ac : sentAgendas.entrySet()) {
                if (ac.getValue().equals(idNumber)) {
                    id = ac.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                agendas.remove(id);
                agendas.add(id);
                sentAgendas.remove(id);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public java.util.Map.Entry<String, Integer> drawAgenda() {
        if (!agendas.isEmpty()) {
            for (String id : agendas) {
                if (!sentAgendas.containsKey(id)) {
                    setSentAgenda(id);
                    for (java.util.Map.Entry<String, Integer> entry : sentAgendas.entrySet()) {
                        if (entry.getKey().equals(id)) {
                            return entry;
                        }
                    }
                }
            }
        }
        return null;
    }

    public String lookAtTopAgenda(int index) {
        return agendas.get(index);
    }

    public String lookAtBottomAgenda(int indexFromEnd) {
        return agendas.get(agendas.size() - 1 - indexFromEnd);
    }

    public String revealAgenda(boolean revealFromBottom) {
        int index = revealFromBottom ? agendas.size() - 1 : 0;
        String id = agendas.remove(index);
        addDiscardAgenda(id);
        return id;
    }

    @Nullable
    public LinkedHashMap<String, Integer> drawActionCard(String userID) {
        if (!actionCards.isEmpty()) {
            String id = actionCards.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                actionCards.remove(id);
                player.setActionCard(id);
                return player.getActionCards();
            }
        } else {
            actionCards.addAll(discardActionCards.keySet());
            discardActionCards.clear();
            Collections.shuffle(actionCards);
            return drawActionCard(userID);
        }
        return null;
    }

    private ArrayList<String> getExplores(String reqType, List<String> superDeck) {
        ArrayList<String> deck = new ArrayList<String>();
        for (String id : superDeck) {
            String card = Mapper.getExplore(id);
            if (card != null) {
                String[] split = card.split(";");
                String type = split[1];
                if (reqType.equalsIgnoreCase(type)) {
                    deck.add(id);
                }
            }
        }
        return deck;
    }

    public ArrayList<String> getExploreDeck(String reqType) {
        return getExplores(reqType, explore);
    }

    public ArrayList<String> getExploreDiscard(String reqType) {
        return getExplores(reqType, discardExplore);
    }

    public String drawExplore(String reqType) {
        List<String> deck = getExplores(reqType, explore);
        if (!deck.isEmpty()) {
            String id = deck.get(0);
            discardExplore(id);
            return id;
        } else {
            deck = getExplores(reqType, discardExplore);
            if (!deck.isEmpty()) {
                explore.addAll(deck);
                Collections.shuffle(explore);
                discardExplore.removeAll(deck);
                return drawExplore(reqType);
            }
        }
        return null;
    }

    public void discardExplore(String id) {
        explore.remove(id);
        if (Mapper.getExplore(id) != null) {
            discardExplore.add(id);
        }
    }

    public void purgeExplore(String id) {
        explore.remove(id);
        discardExplore.remove(id);
    }

    public void addExplore(String id) {
        if (Mapper.getExplore(id) != null) {
            explore.add(id);
        }
        discardExplore.remove(id);
    }

    public void resetExplore() {
        explore.clear();
        discardExplore.clear();
        Set<String> exp = Mapper.getExplores().keySet();
        explore.addAll(exp);
    }

    public String drawRelic() {
        ArrayList<String> relics_ = new ArrayList<>(relics);
        relics_.remove(Constants.ENIGMATIC_DEVICE);
        if (relics_.isEmpty()) {
            return "";
        }
        String remove = relics_.remove(0);
        relics.remove(remove);
        return remove;
    }

    public boolean shuffleRelicBack(String relicID) {
        if (!relics.contains(relicID)){
            relics.add(relicID);
            Collections.shuffle(relics);
            return true;
        }
        return false;
    }

    @Nullable
    public String drawActionCardAndDiscard() {
        if (!actionCards.isEmpty()) {
            String id = actionCards.get(0);
            actionCards.remove(id);
            setDiscardActionCard(id);
            return id;
        } else {
            actionCards.addAll(discardActionCards.keySet());
            discardActionCards.clear();
            Collections.shuffle(actionCards);
            return drawActionCardAndDiscard();
        }
    }

    @Nullable
    public LinkedHashMap<String, Integer> drawSecretObjective(String userID) {
        if (!secretObjectives.isEmpty()) {
            String id = secretObjectives.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                secretObjectives.remove(id);
                player.setSecret(id);
                return player.getSecrets();
            }
        }
        return null;
    }

    @Nullable
    public LinkedHashMap<String, Integer> drawSpecificSecretObjective(String soID, String userID) {
        if (!secretObjectives.isEmpty()) {
            boolean remove = secretObjectives.remove(soID);
            if (remove) {
                Player player = getPlayer(userID);
                if (player != null) {
                    player.setSecret(soID);
                    return player.getSecrets();
                }
            }
        }
        return null;
    }

    public void setDiscardActionCard(String id) {
        Collection<Integer> values = discardActionCards.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
        }
        discardActionCards.put(id, identifier);
    }

    public boolean discardActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> actionCards = player.getActionCards();
            String acID = "";
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                player.removeActionCard(acIDNumber);
                setDiscardActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public void shuffleActionCards() {
        Collections.shuffle(actionCards);
    }

    public LinkedHashMap<String, Integer> getDiscardActionCards() {
        return discardActionCards;
    }

    public boolean pickActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            String acID = "";
            for (java.util.Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                discardActionCards.remove(acID);
                player.setActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public boolean shuffleActionCardBackIntoDeck(Integer acIDNumber) {
        String acID = "";
        for (java.util.Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {
            if (ac.getValue().equals(acIDNumber)) {
                acID = ac.getKey();
                break;
            }
        }
        if (!acID.isEmpty()) {
            discardActionCards.remove(acID);
            actionCards.add(acID);
            Collections.shuffle(actionCards);
            return true;

        }
        return false;
    }

    public boolean scoreSecretObjective(String userID, Integer soIDNumber, ti4.map.Map map) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecret(soIDNumber);
                player.setSecretScored(soID, map);
                return true;
            }
        }
        return false;
    }

    public boolean unscoreSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecretsScored();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecretScored(soIDNumber);
                player.setSecret(soID);
                return true;
            }
        }
        return false;
    }

    public boolean discardSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecret(soIDNumber);
                secretObjectives.add(soID);
                Collections.shuffle(secretObjectives);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public LinkedHashMap<String, Integer> getSecretObjective(String userID) {
        Player player = getPlayer(userID);
        if (player != null) {
            return player.getSecrets();
        }
        return null;
    }

    @Nullable
    public LinkedHashMap<String, Integer> getScoredSecretObjective(String userID) {
        Player player = getPlayer(userID);
        if (player != null) {
            return player.getSecretsScored();
        }
        return null;
    }

    public void addSecretObjective(String id) {
        if (!secretObjectives.contains(id)) {
            secretObjectives.add(id);
            Collections.shuffle(this.secretObjectives);
        }
    }

    public List<String> getSecretObjectives() {
        return secretObjectives;
    }

    public List<String> getActionCards() {
        return actionCards;
    }

    public List<String> getAllExplores() {
        return explore;
    }

    public List<String> getAllExploreDiscard() {
        return discardExplore;
    }

    public void setExploreDeck(ArrayList<String> deck) {
        explore = deck;
    }

    public void setExploreDiscard(ArrayList<String> discard) {
        discardExplore = discard;
    }

    public String pickExplore(String id) {
        if (explore.contains(id)) {
            discardExplore(id);
            return id;
        } else if (discardExplore.contains(id)) {
            return id;
        }
        return null;
    }

    public List<String> getAllRelics() {
        return relics;
    }

    public void setRelics(ArrayList<String> deck) {
        deck = new ArrayList<>(new HashSet<>(deck));
        relics = deck;
    }

    public void resetRelics() {
        HashMap<String, String> relics = Mapper.getRelics(); //ALL agendas including absol
        if (this.absolMode) {
            this.relics = new ArrayList<>(relics.keySet().stream().filter(r -> r.startsWith("absol_")).toList());
            this.relics.add(Constants.ENIGMATIC_DEVICE);
        } else { //ALL relics, except absol - if more decks get added, this will need to be rebuilt
            this.relics = new ArrayList<>(relics.keySet().stream().filter(Predicate.not(r -> r.startsWith("absol_"))).toList());
        }
        Collections.shuffle(this.relics);
    }

    public void setSecretObjectives(List<String> secretObjectives) {
        this.secretObjectives = secretObjectives;
    }

    public void setActionCards(List<String> actionCards) {
        this.actionCards = actionCards;
    }

    public void setDiscardActionCards(LinkedHashMap<String, Integer> discardActionCards) {
        this.discardActionCards = discardActionCards;
    }

    public void setDiscardActionCards(ArrayList<String> discardActionCardList) {
        LinkedHashMap<String, Integer> discardActionCards = new LinkedHashMap<>();
        for (String card : discardActionCardList) {
            Collection<Integer> values = discardActionCards.values();
            int identifier = new Random().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = new Random().nextInt(1000);
            }
            discardActionCards.put(card, identifier);
        }
        this.discardActionCards = discardActionCards;
    }

    public String getOwnerID() {
        return ownerID;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getName() {
        return name;
    }

    public HashMap<String, Tile> getTileMap() {
        return tileMap;
    }

    public Tile getTile(String tileID) {
        return tileMap.values().stream()
                .filter(tile -> tile.getTileID().equals(tileID))
                .findFirst()
                .orElse(null);
    }

    public Tile getTileByPosition(String position) {
        return tileMap.get(position);
    }

    public boolean isTileDuplicated(String tileID) {
        return tileMap.values().stream()
                .filter(tile -> tile.getTileID().equals(tileID))
                .count() > 1;
    }

    public void addPlayer(String id, String name) {
        if (MapStatus.open.equals(mapStatus)) {
            Player player = new Player(id, name);
            players.put(id, player);
        }
    }

    public Player addPlayerLoad(String id, String name) {
        Player player = new Player(id, name);
        players.put(id, player);
        return player;
    }

    public LinkedHashMap<String, Player> getPlayers() {
        return players;
    }

    public void setPlayers(LinkedHashMap<String, Player> players) {
        this.players = players;
    }

    public void setPlayer(String playerID, Player player) {
        players.put(playerID, player);
    }

    public Player getPlayer(String userID) {
        return players.get(userID);
    }

    public Set<String> getPlayerIDs() {
        return players.keySet();
    }

    public void removePlayer(String playerID) {
        if (MapStatus.open.equals(mapStatus)) {
            players.remove(playerID);
        }
    }

    public void removePlayerForced(String playerID) {
        players.remove(playerID);
    }

    public void setMapStatus(MapStatus status) {
        mapStatus = status;
    }

    public boolean isMapOpen() {
        return mapStatus == MapStatus.open;
    }

    public String getMapStatus() {
        return mapStatus.value;
    }

    public void setOwnerID(String ownerID) {
        if (ownerID.length() > 18)
        {
            ownerID = ownerID.substring(0, 18);
        }
        this.ownerID = ownerID;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTileMap(HashMap<String, Tile> tileMap) {
        this.tileMap = tileMap;
        planets.clear();
    }

    public void clearTileMap() {
        this.tileMap.clear();
        planets.clear();
    }

    public void setTile(Tile tile) {
        tileMap.put(tile.getPosition(), tile);
        planets.clear();
    }

    public void removeTile(String position) {
        Tile tileToRemove = tileMap.get(position);
        if (tileToRemove != null) {
            for (UnitHolder unitHolder : tileToRemove.getUnitHolders().values()) {
                if (unitHolder instanceof Planet) {
                    removePlanet(unitHolder);
                }
            }
        }

        tileMap.remove(position);
        planets.clear();
    }

    public void removePlanet(UnitHolder planet) {
        for (Player player_ : players.values()) {
            String color = player_.getColor();
            planet.removeAllUnits(color);
            PlanetRemove.removePlayerControlToken(player_, planet);
            player_.removePlanet(planet.getName());
        }
    }

    public HashMap<String, UnitHolder> getPlanetsInfo() {
        if (planets.isEmpty()) {
            getPlanets();
        }
        return planets;
    }

    public void clearPlanetsCache(){
        planets.clear();
    }

    public Set<String> getPlanets() {
        if (planets.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (java.util.Map.Entry<String, UnitHolder> unitHolderEntry : tile.getUnitHolders().entrySet()) {
                    if (unitHolderEntry.getValue() instanceof Planet) {
                        planets.put(unitHolderEntry.getKey(), unitHolderEntry.getValue());
                    }
                }
            }
            planets.put("custodiavigilia", new Planet("custodiavigilia", new Point(0, 0)));
        }
        return planets.keySet();
    }

    private void calculatePlayerVPs() {
        playerVPs = new HashMap<>();
        for (Player player : getPlayers().values()) {
            playerVPs.put(player, player.getTotalVictoryPoints(this));
        }
    }

    public int getPlayerVPs(Player player) {
        calculatePlayerVPs();
        Integer playerVPCount = playerVPs.get(player);
        if (playerVPCount == null) playerVPCount = 0;
        return playerVPCount;
    }

    public void endGameIfOld() {
        Date lastModifiedDate = new Date(this.lastModifiedDate);
        Date oldestLastModifiedDateBeforeEnding = new Date(1667260800000l); //2022-11-01
        if (!isHasEnded() && lastModifiedDate.compareTo(oldestLastModifiedDateBeforeEnding) < 0) {
            BotLogger.log("Game: " + getName() + " has not been modified since ~" + Helper.getDateRepresentation(lastModifiedDate.getTime()) + " - the game flag `hasEnded` has been set to true");
            setHasEnded(true);
        }
    }
}
