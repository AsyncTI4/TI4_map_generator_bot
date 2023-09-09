package ti4.map;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.MapGenerator;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.planet.PlanetRemove;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.model.DeckModel;
import ti4.model.StrategyCardModel;

import java.awt.*;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import java.util.*;

public class Game {

    private String ownerID;
    private String ownerName = "";
    private String name;

    private String latestCommand = "";

    private String latestOutcomeVotedFor = "";

    private String latestAfterMsg = "";
    private String latestWhenMsg = "";
    private String latestTransactionMsg = "";
    private String latestUpNextMsg = "";

    private int mapImageGenerationCount = 0;

    private MiltyDraftManager miltyDraftManager;
    private boolean ccNPlasticLimit = true;

    @JsonIgnore
    private HashMap<String, UnitHolder> planets = new HashMap<>();

    @Nullable
    private DisplayType displayTypeForced = null;
    @ExportableField
    private int playerCountForMap = 6;

    @Getter
    @Setter
    private boolean reverseSpeakerOrder = false;

    @ExportableField
    private int activationCount = 0;
    @ExportableField
    private int vp = 10;
    @ExportableField
    private boolean competitiveTIGLGame = false;
    @ExportableField
    private boolean communityMode = false;
    @ExportableField
    private boolean allianceMode = false;
    @ExportableField
    private boolean fowMode = false;
    @ExportableField
    private boolean naaluAgent = false;
    @ExportableField
    private boolean dominusOrb = false;
    @ExportableField
    private boolean componentAction = false;
    @ExportableField
    private boolean baseGameMode = false;
    @ExportableField
    private boolean lightFogMode = false;
    @ExportableField
    private boolean homebrewSCMode = false;
    @ExportableField
    private boolean stratPings = true;
    @ExportableField
    private String largeText = "small";
    @ExportableField
    private boolean absolMode = false;

    @Getter
    @Setter
    private String acDeckID = "action_cards_pok";
    @Getter
    @Setter
    private String soDeckID = "secret_objectives_pok";
    @Getter
    @Setter
    private String stage1PublicDeckID = "public_stage_1_objectives_pok";
    @Getter
    @Setter
    private String stage2PublicDeckID = "public_stage_2_objectives_pok";
    @Getter
    @Setter
    private String relicDeckID = "relics_pok";
    @Getter
    @Setter
    private String agendaDeckID = "agendas_pok";
    @Getter
    @Setter
    private String explorationDeckID = "explores_pok";
    @Getter
    @Setter
    @ExportableField
    private String scSetID = "pok";

    @ExportableField
    private boolean discordantStarsMode = false;
    private String outputVerbosity = Constants.VERBOSITY_VERBOSE;
    private boolean testBetaFeaturesMode = false;
    private boolean hasEnded = false;
    private long endedDate;
    @Getter
    @Setter
    private List<BorderAnomalyHolder> borderAnomalies = new ArrayList<>();
    @Nullable
    private String tableTalkChannelID = null;
    @Nullable
    private String mainChannelID = null;
    @Nullable
    private String botMapUpdatesThreadID = null;

    //UserID, UserName
    private LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    @ExportableField
    private GameStatus gameStatus = GameStatus.open;

    private HashMap<Integer, Boolean> scPlayed = new HashMap<>();

    private HashMap<String, String> currentAgendaVotes = new HashMap<>();
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
    private int pingSystemCounter = 0;

    @ExportableField
    private String playersWhoHitPersistentNoAfter = "";
    private String playersWhoHitPersistentNoWhen = "";
    @ExportableField
    private String[] listOfTilePinged = new String[10];

    @ExportableField
    private String activePlayer = null;
    @ExportableField
    private String activeSystem = null;
    private Date lastActivePlayerPing = new Date(0);
    private Date lastActivePlayerChange = new Date(0);
    private Date lastTimeGamesChecked = new Date(0);
    @JsonProperty("autoPingStatus")
    private boolean auto_ping_enabled = false;
    private long autoPingSpacer = 0;
    private List<String> secretObjectives;
    private List<String> actionCards;
    private LinkedHashMap<String, Integer> discardActionCards = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> purgedActionCards = new LinkedHashMap<>();
    private HashMap<String, Integer> displacedUnitsFrom1System = new HashMap<>();
    private HashMap<String, Integer> displacedUnitsFromEntireTacticalAction = new HashMap<>();
    private String phaseOfGame = "";
    private String currentAgendaInfo = null;
    private boolean hasHackElectionBeenPlayed = false;
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

    @JsonProperty("adjacentTileOverrides")
    @JsonDeserialize(keyUsing = MapPairKeyDeserializer.class)
    // @JsonDeserialize(keyUsing = MapPairKeyDeserializer.class)
    private LinkedHashMap<Pair<String, Integer>, String> adjacencyOverrides = new LinkedHashMap<>();

    private List<String> publicObjectives1 = new ArrayList<>();
    private List<String> publicObjectives2 = new ArrayList<>();
    private List<String> publicObjectives1Peakable = new ArrayList<>();
    private List<String> publicObjectives2Peakable = new ArrayList<>();
    private ArrayList<String> soToPoList = new ArrayList<>();

    @JsonIgnore
    private ArrayList<String> purgedPN = new ArrayList<>();

    private List<String> explore = new ArrayList<>();
    private ArrayList<String> discardExplore = new ArrayList<>();
    private List<String> relics = new ArrayList<>();

    @JsonIgnore
    private static HashMap<Player, Integer> playerVPs = new HashMap<>();

    //AUTOCOMPLETE CACHE
    @JsonIgnore
    List<SimpleEntry<String, String>> tileNameAutocompleteOptionsCache = null;
    @JsonIgnore
    List<SimpleEntry<String, String>> planetNameAutocompleteOptionsCache = null;

    private ArrayList<String> runDataMigrations = new ArrayList<>();

    public Game() {
        creationDate = Helper.getDateRepresentation(new Date().getTime());
        lastModifiedDate = new Date().getTime();

        miltyDraftManager = new MiltyDraftManager();

        //Card Decks
        this.secretObjectives = Mapper.getDecks().get("secret_objectives_pok").getShuffledCardList();
        Collections.shuffle(this.secretObjectives);

        this.actionCards = Mapper.getDecks().get("action_cards_pok").getShuffledCardList();
        Collections.shuffle(this.actionCards);

        this.explore = Mapper.getDecks().get("explores_pok").getShuffledCardList();
        Collections.shuffle(this.explore);

        this.publicObjectives1 = Mapper.getDecks().get("public_stage_1_objectives_pok").getShuffledCardList();
        Collections.shuffle(this.publicObjectives1);

        this.publicObjectives2 = Mapper.getDecks().get("public_stage_2_objectives_pok").getShuffledCardList();
        Collections.shuffle(this.publicObjectives2);

        resetAgendas();
        resetRelics();

        addCustomPO(Constants.CUSTODIAN, 1);

        //Default SC initialization
        for (int i = 0; i < 8; i++) {
            scTradeGoods.put(i + 1, 0);
        }
    }

    synchronized public Game copy() {
        return this;
    }

    public void fixScrewedSOs() {
        MessageHelper.sendMessageToChannel(getActionsChannel(),
            "The number of SOs in the deck before this operation is " + getNumberOfSOsInTheDeck() + ". The number in players hands is " + getNumberOfSOsInPlayersHands());

        List<String> defaultSecrets = Mapper.getDecks().get("secret_objectives_pok").getShuffledCardList();
        List<String> currentSecrets = new ArrayList<String>(secretObjectives);
        for (Player player : getPlayers().values()) {
            if (player == null) {
                continue;
            }
            if (player.getSecrets() != null) {
                currentSecrets.addAll(player.getSecrets().keySet());
            }
            if (player.getSecretsScored() != null) {
                currentSecrets.addAll(player.getSecretsScored().keySet());
            }
        }

        for (String defaultSO : defaultSecrets) {
            if (!currentSecrets.contains(defaultSO)) {

                secretObjectives.add(defaultSO);
            }
        }
        MessageHelper.sendMessageToChannel(getActionsChannel(),
            "Fixed the SOs, the total amount of SOs in deck is " + getNumberOfSOsInTheDeck() + ". The number in players hands is " + getNumberOfSOsInPlayersHands());
    }

    public int getNumberOfSOsInTheDeck() {
        return secretObjectives.size();
    }

    public boolean hasBorderAnomalyOn(String tile, Integer direction) {
        List<BorderAnomalyHolder> anomaliesOnBorder = this.borderAnomalies.stream()
            .filter(anomaly -> !anomaly.getType().equals(BorderAnomalyModel.BorderAnomalyType.ARROW))
            .filter(anomaly -> anomaly.getTile().equals(tile))
            .filter(anomaly -> anomaly.getDirection() == direction)
            .collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(anomaliesOnBorder);
    }

    public void addBorderAnomaly(String tile, Integer direction, BorderAnomalyModel.BorderAnomalyType anomalyType) {
        this.borderAnomalies.add(new BorderAnomalyHolder(tile, direction, anomalyType));
    }

    public void removeBorderAnomaly(String tile, Integer direction) {
        this.borderAnomalies.removeIf(anom -> anom.getTile().equals(tile) && anom.getDirection() == direction);
    }

    public int getNumberOfSOsInPlayersHands() {
        int soNum = 0;
        for (Player player : getPlayers().values()) {
            if (player == null) {
                continue;
            }

            soNum = soNum + player.getSo();
            soNum = soNum + player.getSoScored();

        }
        return soNum;
    }

    public HashMap<String, Object> getExportableFieldMap() {
        Class<? extends Game> aClass = this.getClass();
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

    public String getCurrentPhase() {
        return phaseOfGame;
    }

    public void setCurrentPhase(String phase) {
        phaseOfGame = phase;
    }

    public void setLatestCommand(String latestCommand) {
        this.latestCommand = latestCommand;
    }

    public String getLatestOutcomeVotedFor() {
        return latestOutcomeVotedFor;
    }

    public String getLatestAfterMsg() {
        return latestAfterMsg;
    }

    public String getLatestWhenMsg() {
        return latestWhenMsg;
    }

    public String getLatestTransactionMsg() {
        return latestTransactionMsg;
    }

    public String getLatestUpNextMsg() {
        return latestUpNextMsg;
    }

    public void setLatestOutcomeVotedFor(String outcomeVotedFor) {
        latestOutcomeVotedFor = outcomeVotedFor;
    }

    public void setLatestAfterMsg(String latestAfter) {
        latestAfterMsg = latestAfter;
    }

    public void setLatestWhenMsg(String latestWhen) {
        latestWhenMsg = latestWhen;
    }

    public void setLatestTransactionMsg(String latestTransaction) {
        latestTransactionMsg = latestTransaction;
    }

    public void setLatestUpNextMsg(String latestTransaction) {
        latestUpNextMsg = latestTransaction;
    }

    @JsonIgnore
    public MiltyDraftManager getMiltyDraftManager() {
        return miltyDraftManager;
    }

    public void setPurgedPN(String purgedPN) {
        this.purgedPN.add(purgedPN);
    }

    public void removePurgedPN(String purgedPN) {
        this.purgedPN.remove(purgedPN);
    }

    public void addActionCardDuplicates(List<String> ACs) {
        actionCards.addAll(ACs);
        Collections.shuffle(this.actionCards);
    }

    public void addSecretDuplicates(List<String> SOs) {
        secretObjectives.addAll(SOs);
        Collections.shuffle(this.secretObjectives);
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

    public boolean isACInDiscard(String name) {
        boolean isInDiscard = false;
        for (java.util.Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {

            if (Mapper.getActionCard(ac.getKey()).getName().contains(name)) {
                return true;
            }
        }
        return isInDiscard;
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

    public int getPingSystemCounter() {
        return pingSystemCounter;
    }

    public void setPingSystemCounter(int count) {
        pingSystemCounter = count;
    }

    public String[] getListOfTilesPinged() {
        return listOfTilePinged;
    }

    public void setTileAsPinged(int count, String tileName) {
        listOfTilePinged[count] = tileName;
    }

    //GAME MODES
    public boolean isCompetitiveTIGLGame() {
        return competitiveTIGLGame;
    }

    public void setCompetitiveTIGLGame(boolean competitiveTIGLGame) {
        this.competitiveTIGLGame = competitiveTIGLGame;
    }

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

    public boolean isLightFogMode() {
        return lightFogMode;
    }

    public boolean isBaseGameMode() {
        return baseGameMode;
    }

    public void setFoWMode(boolean fowMode) {
        this.fowMode = fowMode;
    }

    public void setLightFogMode(boolean lightFogMode) {
        this.lightFogMode = lightFogMode;
    }

    public void setBaseGameMode(boolean baseGameMode) {
        this.baseGameMode = baseGameMode;
    }

    public boolean isHomeBrewSCMode() {
        return homebrewSCMode;
    }

    public void setHomeBrewSCMode(boolean homeBrewSCMode) {
        this.homebrewSCMode = homeBrewSCMode;
    }

    public boolean isStratPings() {
        return stratPings;
    }

    public void setStratPings(boolean stratPings) {
        this.stratPings = stratPings;
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

    public String getOutputVerbosity() {
        return outputVerbosity;
    }

    public void setOutputVerbosity(String outputVerbosity) {
        if (Constants.VERBOSITY_OPTIONS.contains(outputVerbosity)) {
            this.outputVerbosity = outputVerbosity;
        }
    }

    public boolean isTestBetaFeaturesMode() {
        return testBetaFeaturesMode;
    }

    public void setTestBetaFeaturesMode(boolean testBetaFeaturesMode) {
        this.testBetaFeaturesMode = testBetaFeaturesMode;
    }

    @JsonIgnore
    public String getGameModesText() {
        HashMap<String, Boolean> gameModes = new HashMap<>() {
            {
                put("Normal", isNormalGame());
                put(Emojis.TIGL + "TIGL", isCompetitiveTIGLGame());
                put("Community", isCommunityMode());
                put("Alliance", isAllianceMode());
                put("FoW", isFoWMode());
                put(Emojis.Absol + "Absol", isAbsolMode());
                put(Emojis.DiscordantStars + "DiscordantStars", isDiscordantStarsMode());
                put("HomebrewSC", isHomeBrewSCMode());
            }
        };
        return gameModes.entrySet().stream().filter(Map.Entry::getValue).map(java.util.Map.Entry::getKey).collect(Collectors.joining(", "));
    }

    public boolean isNormalGame() {
        return !(isCompetitiveTIGLGame() || isCommunityMode() || isAllianceMode() || isAbsolMode() || isDiscordantStarsMode() || isFoWMode() || isHomeBrewSCMode());
    }

    @JsonIgnore
    public TextChannel getTableTalkChannel() {
        try {
            return MapGenerator.jda.getTextChannelById(getTableTalkChannelID());
        } catch (Exception e) {
            TextChannel tableTalkChannel = null;
            List<TextChannel> gameChannels = MapGenerator.jda.getTextChannels().stream()
                .filter(c -> c.getName().startsWith(getName()))
                .filter(Predicate.not(c -> c.getName().contains(Constants.ACTIONS_CHANNEL_SUFFIX)))
                .toList();
            if (!gameChannels.isEmpty() && gameChannels.size() == 1) {
                tableTalkChannel = gameChannels.get(0);
                setTableTalkChannelID(tableTalkChannel.getId());
                return tableTalkChannel;
            }
            // BotLogger.log("Could not retrieve TableTalkChannel for " + getName(), e);
        }
        return null;
    }

    public void setTableTalkChannelID(String channelID) {
        this.tableTalkChannelID = channelID;
    }

    public String getTableTalkChannelID() {
        return this.tableTalkChannelID;
    }

    @JsonIgnore
    public TextChannel getMainGameChannel() {
        try {
            return MapGenerator.jda.getTextChannelById(getMainGameChannelID());
        } catch (Exception e) {
            List<TextChannel> gameChannels = MapGenerator.jda.getTextChannelsByName(getName() + Constants.ACTIONS_CHANNEL_SUFFIX, true);
            if (!gameChannels.isEmpty() && gameChannels.size() == 1) {
                TextChannel mainGameChannel = gameChannels.get(0);
                setMainGameChannelID(mainGameChannel.getId());
                return mainGameChannel;
            }
            // BotLogger.log("Could not retrieve MainGameChannel for " + getName(), e);
        }
        return null;
    }

    public void setMainGameChannelID(String channelID) {
        this.mainChannelID = channelID;
    }

    public String getMainGameChannelID() {
        return this.mainChannelID;
    }

    @JsonIgnore
    public TextChannel getActionsChannel() {
        return getMainGameChannel();
    }

    @JsonIgnore
    public ThreadChannel getBotMapUpdatesThread() {
        try {
            return MapGenerator.jda.getThreadChannelById(getBotMapUpdatesThreadID());
        } catch (Exception e) {
            ThreadChannel threadChannel = null; //exists and is not locked
            List<ThreadChannel> botChannels = MapGenerator.jda.getThreadChannelsByName(getName() + Constants.BOT_CHANNEL_SUFFIX, true);
            if (!botChannels.isEmpty() && botChannels.size() == 1) { //found a matching thread
                threadChannel = botChannels.get(0);
            } else { //can't find it, might be archived
                for (ThreadChannel threadChannel_ : getActionsChannel().retrieveArchivedPublicThreadChannels()) {
                    if (threadChannel_.getName().equals(getName() + Constants.BOT_CHANNEL_SUFFIX)) {
                        threadChannel = threadChannel_;
                        setBotMapUpdatesThreadID(threadChannel.getId());
                        return threadChannel;
                    }
                }
            }
        }
        return null;
    }

    public void setBotMapUpdatesThreadID(String threadID) {
        this.botMapUpdatesThreadID = threadID;
    }

    public String getBotMapUpdatesThreadID() {
        return this.botMapUpdatesThreadID;
    }

    /**
     * @return Guild that the ActionsChannel or MainGameChannel resides
     */
    @Nullable
    @JsonIgnore
    public Guild getGuild() {
        return getActionsChannel() == null ? null : ((GuildChannel) getActionsChannel()).getGuild();
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

    public long getEndedDate() {
        return endedDate;
    }

    public void setEndedDate(long dateEnded) {
        this.endedDate = dateEnded;
    }

    //Position, Tile
    private HashMap<String, Tile> tileMap = new HashMap<>();

    public HashMap<Integer, Boolean> getScPlayed() {
        return scPlayed;
    }

    public HashMap<String, String> getCurrentAgendaVotes() {
        return currentAgendaVotes;
    }

    public void resetCurrentAgendaVotes() {
        currentAgendaVotes = new HashMap<>();
    }

    @JsonIgnore
    public List<Integer> getPlayedSCs() {
        return getScPlayed().entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
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
        if (getTileMap().isEmpty()) return 0;
        HashMap<String, Tile> tileMap = new HashMap<>(getTileMap());
        String highestPosition = tileMap.keySet().stream().filter(Helper::isInteger).max(Comparator.comparingInt(Integer::parseInt)).get();
        return Integer.parseInt(StringUtils.left(highestPosition, highestPosition.length() - 2));
    }

    public int getActivationCount() {
        return activationCount;
    }

    public boolean getNaaluAgent() {
        return naaluAgent;
    }

    public boolean getDominusOrbStatus() {
        return dominusOrb;
    }

    public boolean getComponentAction() {
        return componentAction;
    }

    public void setNaaluAgent(boolean onStatus) {
        naaluAgent = onStatus;
    }

    public void setDominusOrb(boolean onStatus) {
        dominusOrb = onStatus;
    }

    public void setComponentAction(boolean onStatus) {
        componentAction = onStatus;
    }

    public void setActivationCount(int count) {
        activationCount = count;
    }

    public void setSCPlayed(Integer scNumber, Boolean playedStatus) {
        this.scPlayed.put(scNumber, playedStatus);
    }

    public void setCurrentAgendaVote(String outcome, String voteInfo) {
        currentAgendaVotes.put(outcome, voteInfo);
    }

    public void removeOutcomeAgendaVote(String outcome) {
        currentAgendaVotes.remove(outcome);
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

    public String getPlayersWhoHitPersistentNoAfter() {
        return playersWhoHitPersistentNoAfter;
    }

    public String getPlayersWhoHitPersistentNoWhen() {
        return playersWhoHitPersistentNoWhen;
    }

    public boolean getHackElectionStatus() {
        return hasHackElectionBeenPlayed;
    }

    public void addPlayersWhoHitPersistentNoAfter(String faction) {
        if (playersWhoHitPersistentNoAfter != null && playersWhoHitPersistentNoAfter.length() > 0) {
            playersWhoHitPersistentNoAfter = playersWhoHitPersistentNoAfter + "_" + faction;
        } else {
            playersWhoHitPersistentNoAfter = faction;
        }
    }

    public void addPlayersWhoHitPersistentNoWhen(String faction) {
        if (playersWhoHitPersistentNoWhen != null && playersWhoHitPersistentNoWhen.length() > 0) {
            playersWhoHitPersistentNoWhen = playersWhoHitPersistentNoWhen + "_" + faction;
        } else {
            playersWhoHitPersistentNoWhen = faction;
        }
    }

    public void setPlayersWhoHitPersistentNoAfter(String persistent) {

        playersWhoHitPersistentNoAfter = persistent;

    }

    public void setPlayersWhoHitPersistentNoWhen(String persistent) {

        playersWhoHitPersistentNoWhen = persistent;

    }

    public void setHackElectionStatus(boolean hack) {

        hasHackElectionBeenPlayed = hack;

    }

    public String getActivePlayer() {
        return activePlayer;
    }

    public String getActiveSystem() {
        return activeSystem;
    }

    public void setActiveSystem(String system) {
        activeSystem = system;
    }

    public HashMap<String, Integer> getCurrentMovedUnitsFrom1System() {
        return displacedUnitsFrom1System;
    }

    public HashMap<String, Integer> getMovedUnitsFromCurrentActivation() {
        return displacedUnitsFromEntireTacticalAction;
    }

    public void setSpecificCurrentMovedUnitsFrom1System(String unit, int count) {
        displacedUnitsFrom1System.put(unit, count);
    }

    public void setCurrentMovedUnitsFrom1System(HashMap<String, Integer> displacedUnits) {
        displacedUnitsFrom1System = displacedUnits;
    }

    public void setSpecificCurrentMovedUnitsFrom1TacticalAction(String unit, int count) {
        displacedUnitsFromEntireTacticalAction.put(unit, count);
    }

    public void setCurrentMovedUnitsFrom1TacticalAction(HashMap<String, Integer> displacedUnits) {
        displacedUnitsFromEntireTacticalAction = displacedUnits;
    }

    public void resetCurrentMovedUnitsFrom1System() {
        displacedUnitsFrom1System = new HashMap<>();
    }

    public void resetCurrentMovedUnitsFrom1TacticalAction() {
        displacedUnitsFromEntireTacticalAction = new HashMap<>();
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

    public void setCurrentAgendaInfo(String agendaInfo) {
        currentAgendaInfo = agendaInfo;
    }

    public String getCurrentAgendaInfo() {
        return currentAgendaInfo;
    }

    public Date getLastTimeGamesChecked() {
        return lastTimeGamesChecked;
    }

    public void setLastTimeGamesChecked(Date time) {
        this.lastTimeGamesChecked = time;
    }

    public void setAutoPing(boolean status) {
        this.auto_ping_enabled = status;
    }

    public boolean getAutoPingStatus() {
        return auto_ping_enabled;
    }

    public long getAutoPingSpacer() {
        return autoPingSpacer;
    }

    public void setAutoPingSpacer(long spacer) {
        this.autoPingSpacer = spacer;
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

    public int addDiscardAgenda(String id) {
        Collection<Integer> values = discardAgendas.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
        }
        discardAgendas.put(id, identifier);
        return identifier;
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

    public boolean addSC(Integer sc) {
        if (!this.scTradeGoods.keySet().contains(sc)) {
            setScTradeGood(sc, 0);
            return true;
        } else
            return false;
    }

    public boolean removeSC(Integer sc) {
        if (this.scTradeGoods.keySet().contains(sc)) {
            this.scTradeGoods.remove(sc);
            return true;
        } else
            return false;
    }

    @JsonIgnore
    public List<Integer> getSCList() {
        return (new ArrayList<>(getScTradeGoods().keySet()));
    }

    /**
     * Add an additonal Strategy Card to use
     * 
     * @param sc the integer value of the new strategy card
     */
    public void addSC(int sc) {
        if (!getSCList().contains(sc)) {
            setScTradeGood(sc, null);
        }
    }

    public void purgeSC(int sc) {
        scTradeGoods.remove(sc);
    }

    public void setScPlayed(HashMap<Integer, Boolean> scPlayed) {
        this.scPlayed = scPlayed;
    }

    public LinkedHashMap<String, Integer> getRevealedPublicObjectives() {
        return revealedPublicObjectives;
    }

    public List<String> getPublicObjectives1() {
        return publicObjectives1;
    }

    public List<String> getPublicObjectives1Peakable() {
        return publicObjectives1Peakable;
    }

    public List<String> getPublicObjectives2() {
        return publicObjectives2;
    }

    public List<String> getPublicObjectives2Peakable() {
        return publicObjectives2Peakable;
    }

    public java.util.Map.Entry<String, Integer> revealState1() {
        if (publicObjectives1Peakable.isEmpty()) {
            return revealObjective(publicObjectives1);
        } else {
            return revealObjective(publicObjectives1Peakable);
        }
    }

    public java.util.Map.Entry<String, Integer> revealState2() {
        if (publicObjectives2Peakable.isEmpty()) {
            return revealObjective(publicObjectives2);
        } else {
            return revealObjective(publicObjectives2Peakable);
        }
    }

    public void setUpPeakableObjectives(int num) {
        for (int x = 0; x < num; x++) {
            if (!publicObjectives1.isEmpty()) {
                Collections.shuffle(publicObjectives1);
                String id = publicObjectives1.get(0);
                publicObjectives1.remove(id);
                publicObjectives1Peakable.add(id);
            }
            if (!publicObjectives2.isEmpty()) {
                Collections.shuffle(publicObjectives2);
                String id = publicObjectives2.get(0);
                publicObjectives2.remove(id);
                publicObjectives2Peakable.add(id);
            }
        }
    }

    public String peakAtStage1(int place) {
        return peakAtObjective(publicObjectives1Peakable, place);
    }

    public String peakAtStage2(int place) {
        return peakAtObjective(publicObjectives2Peakable, place);
    }

    public java.util.Map.Entry<String, Integer> revealSpecificStage1(String id) {
        return revealSpecificObjective(publicObjectives1, id);
    }

    public java.util.Map.Entry<String, Integer> revealSpecificStage2(String id) {
        return revealSpecificObjective(publicObjectives2, id);
    }

    public void swapStage1(int place1, int place2) {
        swapObjective(publicObjectives1Peakable, place1, place2);
    }

    public void swapStage2(int place1, int place2) {
        swapObjective(publicObjectives2Peakable, place1, place2);
    }

    public void swapObjective(List<String> objectiveList, int place1, int place2) {
        if (!objectiveList.isEmpty()) {
            place1 = place1 - 1;
            place2 = place2 - 1;
            String id = objectiveList.get(place1);
            String id2 = objectiveList.get(place2);
            objectiveList.set(place1, id2);
            objectiveList.set(place2, id);
        }
    }

    public String peakAtObjective(List<String> objectiveList, int place) {
        if (!objectiveList.isEmpty()) {
            place = place - 1;
            return objectiveList.get(place);
        }
        return null;
    }

    public java.util.Map.Entry<String, Integer> revealObjective(List<String> objectiveList) {
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

    public java.util.Map.Entry<String, Integer> revealSpecificObjective(List<String> objectiveList, String id) {
        if (objectiveList.contains(id)) {
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

    public java.util.Map.Entry<String, Integer> addSpecificObjective(List<String> objectiveList, String objective) {
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
            Set<String> po1 = Mapper.getPublicObjectivesStage1().keySet();
            Set<String> po2 = Mapper.getPublicObjectivesStage2().keySet();
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

    public boolean isCustodiansScored() {

        boolean custodiansTaken = false;
        String idC = "";
        for (java.util.Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(0)) {
                idC = po.getKey();
                break;
            }
        }
        if (!idC.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(idC, key -> new ArrayList<>());
            for (String playerID : players.keySet()) {
                if (scoredPlayerList.contains(playerID)) {
                    custodiansTaken = true;
                    break;
                }
            }
        }
        return custodiansTaken;
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

    public boolean addSOToGame(String id) {
        return secretObjectives.add(id);
    }

    public boolean removeSOFromGame(String id) {
        return secretObjectives.remove(id);
    }

    public boolean removePOFromGame(String id) {
        if (publicObjectives1.remove(id)) return true;
        return publicObjectives2.remove(id);
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

    public void setPublicObjectives1Peakable(ArrayList<String> publicObjectives1) {
        this.publicObjectives1Peakable = publicObjectives1;
    }

    public void setPublicObjectives2Peakable(ArrayList<String> publicObjectives2) {
        this.publicObjectives2Peakable = publicObjectives2;
    }

    public void removePublicObjective1(String key) {
        publicObjectives1.remove(key);
    }

    public void removePublicObjective2(String key) {
        publicObjectives2.remove(key);
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

    @JsonGetter
    @JsonSerialize(keyUsing = MapPairKeySerializer.class)
    public LinkedHashMap<Pair<String, Integer>, String> getAdjacentTileOverrides() {
        return adjacencyOverrides;
    }

    public void addAdjacentTileOverride(String primaryTile, int direction, String secondaryTile) {
        Pair<String, Integer> primary = new ImmutablePair<>(primaryTile, direction);
        Pair<String, Integer> secondary = new ImmutablePair<>(secondaryTile, (direction + 3) % 6);

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
                adjacencyOverrides.remove(new ImmutablePair<>(primary, i));
                adjacencyOverrides.remove(new ImmutablePair<>(secondary, j));
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
        Pair<String, Integer> primary = new ImmutablePair<>(position, direction);
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
        this.agendas = Mapper.getDecks().get(getAgendaDeckID()).getShuffledCardList();
        Collections.shuffle(this.agendas);
        discardAgendas = new LinkedHashMap<>();
    }

    public void resetDrawStateAgendas() {
        sentAgendas.clear();
    }

    @JsonSetter
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

    public boolean reviseLaw(Integer idNumber, String optionalText) {

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
            idNumber = addDiscardAgenda(id);
        }
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

    public boolean putBackIntoDeckOnTop(Integer idNumber) {
        String id = "";
        for (java.util.Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardAgendas.remove(id);
            agendas.add(0, id);
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

    public boolean discardSpecificAgenda(String agendaID) {

        boolean succeeded = agendas.remove(agendaID);
        if (succeeded) {
            addDiscardAgenda(agendaID);
        }
        return succeeded;
    }

    public boolean putSpecificAgendaOnTop(String agendaID) {

        boolean succeeded = agendas.remove(agendaID);
        addDiscardAgenda(agendaID);
        return succeeded;
    }

    public String getNextAgenda(boolean revealFromBottom) {
        int index = revealFromBottom ? agendas.size() - 1 : 0;
        return agendas.get(index);
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
        ArrayList<String> deck = new ArrayList<>();
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
        String result = null;

        //MIGRATION CODE TODO: Remove this once we are fairly certain no exising games have an existing empty deck - implemented 2023-07
        if (deck.isEmpty()) {
            shuffleDiscardsIntoExploreDeck(reqType);
            deck = getExplores(reqType, explore);
            BotLogger.log("Map: `" + getName() + "` MIGRATION CODE TRIGGERED: Explore " + reqType + " deck was empty, shuffling discards into deck.");
        } //end of migration code

        if (!deck.isEmpty()) {
            String id = deck.get(0);
            discardExplore(id);
            result = id;
        }

        // If deck is empty after draw, auto refresh deck from discard
        if (getExplores(reqType, explore).isEmpty()) {
            shuffleDiscardsIntoExploreDeck(reqType);
        }
        return result;
    }

    public void shuffleDiscardsIntoExploreDeck(String reqType) {
        List<String> discardsOfType = getExplores(reqType, discardExplore);
        explore.addAll(discardsOfType);
        Collections.shuffle(explore);
        discardExplore.removeAll(discardsOfType);
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

    public void triplicateExplores() {
        this.explore = Mapper.getDecks().get("explores_pok").getShuffledCardList();
        Collections.shuffle(this.explore);
        for (String relic : Mapper.getDecks().get("explores_pok").getShuffledCardList()) {
            String copy1 = relic + "extra1";
            String copy2 = relic + "extra2";
            explore.add(copy1);
            explore.add(copy2);
        }
        Collections.shuffle(this.explore);
    }

    public void triplicateACs() {
        this.actionCards = Mapper.getDecks().get("action_cards_pok").getShuffledCardList();
        Collections.shuffle(this.actionCards);
        for (String relic : Mapper.getDecks().get("action_cards_pok").getShuffledCardList()) {
            String copy1 = relic + "extra1";
            String copy2 = relic + "extra2";
            actionCards.add(copy1);
            actionCards.add(copy2);
        }
        Collections.shuffle(this.actionCards);
    }

    public void triplicateSOs() {
        this.secretObjectives = Mapper.getDecks().get("secret_objectives_pok").getShuffledCardList();
        Collections.shuffle(this.secretObjectives);
        for (String relic : Mapper.getDecks().get("secret_objectives_pok").getShuffledCardList()) {
            String copy1 = relic + "extra1";
            String copy2 = relic + "extra2";
            secretObjectives.add(copy1);
            secretObjectives.add(copy2);
        }
        Collections.shuffle(this.secretObjectives);
    }

    public String drawRelic() {
        ArrayList<String> relics_ = new ArrayList<>(relics);
        relics_.remove(Constants.ENIGMATIC_DEVICE); //Legacy, deck no longer includes this - can be removed once all games before pbd682 are finished
        if (relics_.isEmpty()) {
            return "";
        }
        String remove = relics_.remove(0);
        relics.remove(remove);
        return remove;
    }

    public boolean shuffleRelicBack(String relicID) {
        if (!relics.contains(relicID)) {
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

    public boolean purgeSpecificSecretObjective(String soID) {
        return secretObjectives.remove(soID);
    }

    @Nullable
    public LinkedHashMap<String, Integer> drawSpecificActionCard(String acID, String userID) {
        if (!actionCards.isEmpty()) {
            int tries = 0;
            while (tries < 3) {
                if (actionCards.indexOf(acID) > -1) {
                    Player player = getPlayer(userID);
                    if (player != null) {
                        actionCards.remove(acID);
                        player.setActionCard(acID);
                        return player.getActionCards();
                    }
                    tries = 12;
                }
                tries++;
                if (acID.contains("extra1")) {
                    acID = acID.replace("extra1", "extra2");
                } else {
                    acID = acID + "extra1";
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

    @JsonIgnore
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

    public boolean scoreSecretObjective(String userID, Integer soIDNumber, Game activeGame) {
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
                player.setSecretScored(soID, activeGame);
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
        deck = new ArrayList<>(deck);
        relics = deck;
    }

    public void resetRelics() {
        this.relics = Mapper.getDecks().get(getRelicDeckID()).getShuffledCardList();
        Collections.shuffle(this.relics);
    }

    public void triplicateRelics() {
        if (this.absolMode) {
            this.relics = Mapper.getDecks().get("relics_absol").getShuffledCardList();
            for (String relic : Mapper.getDecks().get("relics_absol").getShuffledCardList()) {
                String copy1 = relic + "extra1";
                String copy2 = relic + "extra2";
                relics.add(copy1);
                relics.add(copy2);
            }
        } else {
            this.relics = Mapper.getDecks().get("relics_pok").getShuffledCardList();
            for (String relic : Mapper.getDecks().get("relics_pok").getShuffledCardList()) {
                String copy1 = relic + "extra1";
                String copy2 = relic + "extra2";
                relics.add(copy1);
                relics.add(copy2);
            }
        }

        Collections.shuffle(this.relics);
    }

    public void setSecretObjectives(List<String> secretObjectives) {
        this.secretObjectives = secretObjectives;
    }

    public void setActionCards(List<String> actionCards) {
        this.actionCards = actionCards;
    }

    public boolean validateAndSetActionCardDeck(SlashCommandInteractionEvent event, DeckModel deck) {
        if (this.getDiscardActionCards().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change action card deck while there are action cards in the discard pile.");
            return false;
        }
        for (Player player : this.getPlayers().values()) {
            if (player.getActionCards().size() > 0) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change action card deck while there are action cards in player hands.");
                return false;
            }
        }
        setAcDeckID(deck.getAlias());
        this.setActionCards(deck.getShuffledCardList());
        return true;
    }

    public boolean validateAndSetRelicDeck(SlashCommandInteractionEvent event, DeckModel deck) {
        for (Player player : this.getPlayers().values()) {
            if (player.getRelics().size() > 0) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change relic deck while there are relics in player hands.");
                return false;
            }
        }
        setRelicDeckID(deck.getAlias());
        this.setRelics(new ArrayList<>(deck.getShuffledCardList()));
        return true;
    }

    public boolean validateAndSetSecretObjectiveDeck(SlashCommandInteractionEvent event, DeckModel deck) {
        for (Player player : this.getPlayers().values()) {
            if (player.getSecrets().size() > 0) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change secret objective deck while there are secret objectives in player hands.");
                return false;
            }
        }
        setSoDeckID(deck.getAlias());
        this.setSecretObjectives(deck.getShuffledCardList());
        return true;
    }

    public boolean validateAndSetAgendaDeck(SlashCommandInteractionEvent event, DeckModel deck) {
        if (this.getDiscardAgendas().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change agenda deck while there are agendas in the discard pile.");
            return false;
        }
        setAgendaDeckID(deck.getAlias());
        this.setAgendas(deck.getShuffledCardList());
        return true;
    }

    @JsonSetter
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
        if (tileID != null && tileID.equalsIgnoreCase("mirage")) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder uh : tile.getUnitHolders().values()) {
                    if (uh.getTokenList() != null && (uh.getTokenList().contains("mirage") || uh.getTokenList().contains("token_mirage.png"))) {
                        return tile;
                    }
                }
            }
        }

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
        if (GameStatus.open.equals(gameStatus)) {
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

    @JsonIgnore
    public List<Player> getRealPlayers() {
        return getPlayers().values().stream().filter(Player::isRealPlayer).collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getNotRealPlayers() {
        return getPlayers().values().stream().filter(Player::isNotRealPlayer).collect(Collectors.toList());
    }

    @JsonIgnore
    public Set<String> getFactions() {
        return getRealPlayers().stream().map(Player::getFaction).collect(Collectors.toSet());
    }

    public void setPlayers(LinkedHashMap<String, Player> players) {
        this.players = players;
    }

    public void setCCNPlasticLimit(boolean limit) {
        ccNPlasticLimit = limit;
    }

    public boolean getCCNPlasticLimit() {
        return ccNPlasticLimit;
    }

    public void setPlayer(String playerID, Player player) {
        players.put(playerID, player);
    }

    public Player getPlayer(String userID) {
        return players.get(userID);
    }

    @JsonIgnore
    public Set<String> getPlayerIDs() {
        return players.keySet();
    }

    public void removePlayer(String playerID) {
        if (GameStatus.open.equals(gameStatus)) {
            players.remove(playerID);
        }
    }

    public void removePlayerForced(String playerID) {
        players.remove(playerID);
    }

    public void setGameStatus(GameStatus status) {
        gameStatus = status;
    }

    @JsonIgnore
    public boolean isMapOpen() {
        return gameStatus == GameStatus.open;
    }

    public String getGameStatus() {
        return gameStatus.value;
    }

    public void setOwnerID(String ownerID) {
        if (ownerID.length() > 18) {
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

    public void clearPlanetsCache() {
        planets.clear();
    }

    @JsonIgnore
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
            planets.put("ghoti", new Planet("ghoti", new Point(0, 0)));
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
        if (isHasEnded()) return;

        LocalDate currentDate = LocalDate.now();
        LocalDate lastModifiedDate = (new Date(this.lastModifiedDate)).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        Period period = Period.ofMonths(2); //TODO: CANDIDATE FOR GLOBAL VARIABLE
        LocalDate oldestLastModifiedDateBeforeEnding = currentDate.minus(period);

        if (lastModifiedDate.compareTo(oldestLastModifiedDateBeforeEnding) < 0) {
            BotLogger.log("Game: " + getName() + " has not been modified since ~" + lastModifiedDate.toString() + " - the game flag `hasEnded` has been set to true");
            setHasEnded(true);
            GameSaveLoadManager.saveMap(this);
        }
    }

    public void rebuildTilePositionAutoCompleteList() {
        setTileNameAutocompleteOptionsCache(getTileMap().entrySet().stream()
            .map(e -> new AbstractMap.SimpleEntry<>(e.getValue().getRepresentationForAutoComplete(), e.getValue().getPosition()))
            .filter(e -> !e.getKey().toLowerCase().contains("hyperlane"))
            .toList());
    }

    @JsonIgnore
    public List<SimpleEntry<String, String>> getTileNameAutocompleteOptionsCache() {
        if (tileNameAutocompleteOptionsCache != null) {
            return this.tileNameAutocompleteOptionsCache;
        }
        rebuildTilePositionAutoCompleteList();
        return this.tileNameAutocompleteOptionsCache;
    }

    public void setTileNameAutocompleteOptionsCache(List<SimpleEntry<String, String>> tileNameAutocompleteOptionsCache) {
        this.tileNameAutocompleteOptionsCache = tileNameAutocompleteOptionsCache;
    }

    public Player getPNOwner(String pnID) {
        for (Player player : getPlayers().values()) {
            if (player.ownsPromissoryNote(pnID)) {
                return player;
            }
        }
        return null;
    }

    public void checkPromissoryNotes() {
        List<String> allPromissoryNotes = new ArrayList<>();
        List<String> allPlayerHandPromissoryNotes = new ArrayList<>();
        Set<String> allOwnedPromissoryNotes = new HashSet<>();

        for (Player player : getPlayers().values()) {
            allPromissoryNotes.addAll(player.getPromissoryNotes().keySet());
            allPlayerHandPromissoryNotes.addAll(player.getPromissoryNotes().keySet());
            allPromissoryNotes.addAll(player.getPromissoryNotesInPlayArea());
            allOwnedPromissoryNotes.addAll(player.getPromissoryNotesOwned());
        }

        // Find duplicate PNs - PNs that are in multiple players' hands or play areas
        if (Helper.findDuplicateInList(allPlayerHandPromissoryNotes).size() > 0) {
            BotLogger.log("`" + getName() + "`: there are duplicate promissory notes in the game:\n> `" + Helper.findDuplicateInList(allPlayerHandPromissoryNotes) + "`");
        }

        allPromissoryNotes.addAll(getPurgedPN());

        // Find PNs that are extra - players have them but nobody "owns" them
        List<String> unOwnedPromissoryNotes = new ArrayList<>(allPromissoryNotes);
        unOwnedPromissoryNotes.removeAll(allOwnedPromissoryNotes);
        if (unOwnedPromissoryNotes.size() > 0) {
            BotLogger.log("`" + getName() + "`: there are promissory notes in the game that no player owns:\n> `" + unOwnedPromissoryNotes + "`");
            getPurgedPN().removeAll(unOwnedPromissoryNotes);
        }

        // Remove unowned PNs from all players hands
        for (Player player : getPlayers().values()) {
            List<String> pns = new ArrayList<String>(player.getPromissoryNotes().keySet());
            for (String pnID : pns) {
                if (unOwnedPromissoryNotes.contains(pnID)) {
                    player.removePromissoryNote(pnID);
                    BotLogger.log("`" + getName() + "`: removed promissory note `" + pnID + "` from player `" + player.getUserName() + "` because nobody 'owned' it");
                }
            }
        }

        // Report PNs that are missing from the game
        List<String> missingPromissoryNotes = new ArrayList<>(allOwnedPromissoryNotes);
        missingPromissoryNotes.removeAll(allPromissoryNotes);
        if (missingPromissoryNotes.size() > 0) {
            BotLogger.log("`" + getName() + "`: there are promissory notes that should be in the game but are not:\n> `" + missingPromissoryNotes + "`");
        }
    }

    public boolean playerHasLeaderUnlockedOrAlliance(Player player, String leaderID) {
        if (player.hasLeaderUnlocked(leaderID)) return true;
        if (!leaderID.contains("commander")) return false;

        // check if player has any allainces with players that have the commander unlocked
        for (String pnID : player.getPromissoryNotesInPlayArea()) {
            if (pnID.contains("_an") || "dspnceld".equals(pnID)) { //dspnceld = Celdauri Trade Alliance
                Player pnOwner = getPNOwner(pnID);
                if (pnOwner != null && !pnOwner.equals(player) && pnOwner.hasLeaderUnlocked(leaderID)) {
                    return true;
                }
            }
        }

        // check if player has Imperia and if any of the stolen CCs are owned by players that have the leader unlocked
        if (player.hasAbility("imperia")) {
            for (Player player_ : getRealPlayers()) {
                if (player_.equals(player)) continue;
                if (player.getMahactCC().contains(player_.getColor()) && player_.hasLeaderUnlocked(leaderID)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<Leader> playerUnlockedLeadersOrAlliance(Player player) {
        List<Leader> leaders = new ArrayList<>(player.getLeaders());
        // check if player has any allainces with players that have the commander unlocked
        for (String pnID : player.getPromissoryNotesInPlayArea()) {
            if (pnID.contains("_an") || "dspnceld".equals(pnID)) { //dspnceld = Celdauri Trade Alliance
                Player pnOwner = getPNOwner(pnID);
                if (pnOwner != null && !pnOwner.equals(player)) {
                    Leader playerLeader = pnOwner.getLeaderByType(Constants.COMMANDER).orElse(null);
                    leaders.add(playerLeader);
                }
            }
        }

        // check if player has Imperia and if any of the stolen CCs are owned by players that have the leader unlocked
        if (player.hasAbility("imperia")) {
            for (Player otherPlayer : getRealPlayers()) {
                if (otherPlayer.equals(player)) continue;
                if (player.getMahactCC().contains(otherPlayer.getColor())) {
                    Leader playerLeader = otherPlayer.getLeaderByType(Constants.COMMANDER).orElse(null);
                    leaders.add(playerLeader);
                }
            }
        }
        leaders = leaders.stream().filter(leader -> leader != null && !leader.isLocked()).collect(Collectors.toList());
        return leaders;
    }

    public int getMapImageGenerationCount() {
        return mapImageGenerationCount;
    }

    public int setMapImageGenerationCount(int mapImageGenerationCount) {
        return this.mapImageGenerationCount = mapImageGenerationCount;
    }

    public void incrementMapImageGenerationCount() {
        this.mapImageGenerationCount++;
    }

    public boolean hasRunMigration(String string) {
        return this.runDataMigrations.contains(string);
    }

    public void addMigration(String string) {
        this.runDataMigrations.add(string);
    }

    public ArrayList<String> getRunMigrations() {
        return this.runDataMigrations;
    }

    public StrategyCardModel getStrategyCardSet() {
        return Mapper.getStrategyCardSets().get(getScSetID());
    }

    public int getActionCardDeckSize() {
        return getActionCards().size();
    }

    public int getActionCardFullDeckSize() {
        DeckModel acDeckModel = Mapper.getDeck(getAcDeckID());
        if (acDeckModel != null) return acDeckModel.getCardCount();
        return -1;
    }

    public int getAgendaDeckSize() {
        return getAgendas().size();
    }

    public int getAgendaFullDeckSize() {
        DeckModel agendaDeckModel = Mapper.getDeck(getAgendaDeckID());
        if (agendaDeckModel != null) return agendaDeckModel.getCardCount();
        return -1;
    }

    public int getPublicObjectives1DeckSize() {
        return getPublicObjectives1().size();
    }

    public int getPublicObjectives1FullDeckSize() {
        DeckModel po1DeckModel = Mapper.getDeck(getStage1PublicDeckID());
        if (po1DeckModel != null) return po1DeckModel.getCardCount();
        return -1;
    }

    public int getPublicObjectives2DeckSize() {
        return getPublicObjectives2().size();
    }

    public int getPublicObjectives2FullDeckSize() {
        DeckModel po2DeckModel = Mapper.getDeck(getStage2PublicDeckID());
        if (po2DeckModel != null) return po2DeckModel.getCardCount();
        return -1;
    }

    public int getRelicDeckSize() {
        return getAllRelics().size();
    }

    public int getRelicFullDeckSize() {
        DeckModel relicDeckModel = Mapper.getDeck(getRelicDeckID());
        if (relicDeckModel != null) return relicDeckModel.getCardCount();
        return -1;
    }

    public int getSecretObjectiveDeckSize() {
        return getSecretObjectives().size();
    }

    public int getSecretObjectiveFullDeckSize() {
        DeckModel soDeckModel = Mapper.getDeck(getSoDeckID());
        if (soDeckModel != null) return soDeckModel.getCardCount();
        return -1;
    }

    private int getExploreDeckSize(String exploreDeckID) {
        return getExploreDeck(exploreDeckID).size();
    }

    private int getExploreDeckFullSize(String exploreDeckID) {
        DeckModel exploreDeckModel = Mapper.getDeck(getExplorationDeckID());
        if (exploreDeckModel == null) return -1;
        List<String> exploreDeck = new ArrayList<>();
        for (String exploreCardID : exploreDeckModel.getCardIDs()) {
            String exploreCard = Mapper.getExplore(exploreCardID);
            if (StringUtils.substringAfter(exploreCard, ";").toLowerCase().startsWith(exploreDeckID)) {
                exploreDeck.add(exploreCard);
            }
        }
        return exploreDeck.size();
    }

    public int getHazardousExploreDeckSize() {
        return getExploreDeckSize(Constants.HAZARDOUS);
    }

    public int getHazardousExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.HAZARDOUS);
    }

    public int getCulturalExploreDeckSize() {
        return getExploreDeckSize(Constants.CULTURAL);
    }

    public int getCulturalExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.CULTURAL);
    }

    public int getIndustrialExploreDeckSize() {
        return getExploreDeckSize(Constants.INDUSTRIAL);
    }

    public int getIndustrialExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.INDUSTRIAL);
    }

    public int getFrontierExploreDeckSize() {
        return getExploreDeckSize(Constants.FRONTIER);
    }

    public int getFrontierExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.FRONTIER);
    }
}
