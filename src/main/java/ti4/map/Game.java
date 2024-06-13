package ti4.map;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.awt.Point;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.planet.PlanetRemove;
import ti4.draft.BagDraft;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.settingsFramework.menus.DeckSettings;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.model.ColorModel;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.Source.ComponentSource;

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
    @Getter
    @Setter
    private int mapImageGenerationCount;
    private final MiltyDraftManager miltyDraftManager;
    private boolean ccNPlasticLimit = true;
    private boolean botFactionReacts;
    private boolean hasHadAStatusPhase;
    private boolean botShushing = true;
    @JsonIgnore
    private final Map<String, Planet> planets = new HashMap<>();
    @Nullable
    private DisplayType displayTypeForced;
    @ExportableField
    private int playerCountForMap = 6;
    @ExportableField
    @Getter
    @Setter
    private int strategyCardsPerPlayer = 1;
    @Getter
    @Setter
    private boolean reverseSpeakerOrder;
    @ExportableField
    private int activationCount;
    @ExportableField
    private int vp = 10;
    @ExportableField
    @Getter
    @Setter
    private int maxSOCountPerPlayer = 3;
    @ExportableField
    private boolean competitiveTIGLGame;
    @ExportableField
    private boolean communityMode;
    @ExportableField
    private boolean allianceMode;
    @ExportableField
    private boolean homeBrew;
    @ExportableField
    private boolean fowMode;
    private final Map<String, String> fowOptions = new HashMap<>();
    @ExportableField
    private boolean naaluAgent;
    @ExportableField
    private boolean l1Hero;
    @ExportableField
    private boolean nomadCoin;
    @ExportableField
    private boolean undoButtonOffered = true;
    @ExportableField
    private boolean fastSCFollowMode;
    @ExportableField
    private boolean queueSO = true;
    @ExportableField
    private boolean showBubbles = true;
    @ExportableField
    private boolean showGears = true;
    @ExportableField
    private boolean temporaryPingDisable;
    @ExportableField
    private boolean dominusOrb;
    @ExportableField
    private boolean componentAction;
    @ExportableField
    private boolean justPlayedComponentAC;
    @ExportableField
    private boolean baseGameMode;
    @ExportableField
    private boolean lightFogMode;
    @ExportableField
    private boolean redTapeMode;
    @ExportableField
    private boolean homebrewSCMode;
    @ExportableField
    private boolean spinMode;
    @ExportableField
    private boolean stratPings = true;
    @Getter
    @Setter
    private boolean injectRulesLinks = true;
    @ExportableField
    @Getter
    @Setter
    private String textSize = "medium";
    @ExportableField
    @Getter
    @Setter
    private boolean absolMode;
    @Getter
    @Setter
    private boolean miltyModMode;
    @Getter
    @Setter
    private boolean showUnitTags;
    @Getter
    @Setter
    private boolean extraSecretMode;
    @Getter
    @Setter
    private boolean showMapSetup;
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
    private String eventDeckID;
    @Getter
    @Setter
    private String explorationDeckID = "explores_pok";
    @Getter
    @Setter
    private String technologyDeckID = "techs_pok";
    @Getter
    @Setter
    private String mapTemplateID;
    @Getter
    @Setter
    @ExportableField
    private String scSetID = "pok";
    @ExportableField
    @Getter
    @Setter
    private boolean discordantStarsMode;
    private String outputVerbosity = Constants.VERBOSITY_VERBOSE;
    private boolean testBetaFeaturesMode;
    private boolean ageOfExplorationMode;
    private boolean minorFactionsMode;
    @Getter
    @Setter
    private boolean showFullComponentTextEmbeds;
    private boolean hasEnded;
    private long endedDate;
    @Getter
    @Setter
    private List<BorderAnomalyHolder> borderAnomalies = new ArrayList<>();
    @Nullable
    private String tableTalkChannelID;
    @Nullable
    private String mainChannelID;
    private String savedChannelID;
    private String savedMessage;
    @Nullable
    private String botMapUpdatesThreadID;
    @Getter
    @Setter
    private String bagDraftStatusMessageID;
    @Getter
    @Setter
    private String launchPostThreadID;
    private Map<String, Player> players = new LinkedHashMap<>();
    private final Map<Integer, Boolean> scPlayed = new HashMap<>();
    private Map<String, String> currentAgendaVotes = new HashMap<>();
    private final Map<String, String> checkingForAllReacts = new HashMap<>();
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
    private int buttonPress;
    @ExportableField
    private int slashCommandsRun;
    @ExportableField
    private int pingSystemCounter;
    @ExportableField
    private String playersWhoHitPersistentNoAfter = "";
    private String playersWhoHitPersistentNoWhen = "";
    @ExportableField
    private final String[] listOfTilePinged = new String[10];
    @ExportableField
    private String activePlayer;
    @ExportableField
    private String activeSystem;
    private Date lastActivePlayerPing = new Date(0);
    private Date lastActivePlayerChange = new Date(0);
    private Date lastTimeGamesChecked = new Date(0);
    @JsonProperty("autoPingStatus")
    private boolean autoPingEnabled;
    private long autoPingSpacer;
    private List<String> secretObjectives;
    private List<String> actionCards;
    private Map<String, Integer> discardActionCards = new LinkedHashMap<>();
    private Map<String, Integer> purgedActionCards = new LinkedHashMap<>();
    private Map<String, Integer> displacedUnitsFrom1System = new HashMap<>();
    private Map<String, Integer> thalnosUnits = new HashMap<>();
    private Map<String, Integer> slashCommandsUsed = new HashMap<>();
    private Map<String, Integer> actionCardsSabotaged = new HashMap<>();
    private Map<String, Integer> displacedUnitsFromEntireTacticalAction = new HashMap<>();
    @ExportableField
    private String phaseOfGame = "";
    private String currentAgendaInfo = "";
    private String currentACDrawStatusInfo = "";
    private boolean hasHackElectionBeenPlayed;
    private List<String> agendas;
    @Getter
    private List<String> events = new ArrayList<>();
    @Getter
    private Map<String, Integer> discardedEvents = new LinkedHashMap<>();
    @Getter
    @Setter
    private Map<String, Integer> eventsInEffect = new LinkedHashMap<>();
    private Map<Integer, Integer> scTradeGoods = new LinkedHashMap<>();
    private Map<String, Integer> discardAgendas = new LinkedHashMap<>();
    private Map<String, Integer> sentAgendas = new LinkedHashMap<>();
    private Map<String, Integer> laws = new LinkedHashMap<>();
    private Map<String, String> lawsInfo = new LinkedHashMap<>();
    private List<String> messageIDsForSaboReacts = new ArrayList<>();
    @ExportableField
    private Map<String, Integer> revealedPublicObjectives = new LinkedHashMap<>();
    private Map<String, Integer> customPublicVP = new LinkedHashMap<>();
    private Map<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>();
    private Map<String, List<String>> customAdjacentTiles = new LinkedHashMap<>();
    @JsonProperty("adjacentTileOverrides")
    @JsonDeserialize(keyUsing = MapPairKeyDeserializer.class)
    private LinkedHashMap<Pair<String, Integer>, String> adjacencyOverrides = new LinkedHashMap<>();
    private List<String> publicObjectives1;
    private List<String> publicObjectives2;
    private List<String> publicObjectives1Peakable = new ArrayList<>();
    private List<String> publicObjectives2Peakable = new ArrayList<>();
    @Getter
    @Setter
    private Map<String, List<String>> publicObjectives1Peeked = new LinkedHashMap<>();
    @Getter
    @Setter
    private Map<String, List<String>> publicObjectives2Peeked = new LinkedHashMap<>();
    private List<String> savedButtons = new ArrayList<>();
    private List<String> soToPoList = new ArrayList<>();
    @JsonIgnore
    private List<String> purgedPN = new ArrayList<>();
    private List<String> explore;
    private List<String> discardExplore = new ArrayList<>();
    private List<String> relics;
    @JsonIgnore
    private List<SimpleEntry<String, String>> tileNameAutocompleteOptionsCache;
    private final List<String> runDataMigrations = new ArrayList<>();
    private BagDraft activeDraft;
    @JsonIgnore
    @Getter
    @Setter
    private Map<String, Integer> tileDistances = new HashMap<>();
    @Getter
    @Setter
    private int numberOfPurgedFragments;
    @Getter
    @Setter
    private MiltySettings miltySettings = null;

    public Game() {
        creationDate = Helper.getDateRepresentation(new Date().getTime());
        lastModifiedDate = new Date().getTime();

        miltyDraftManager = new MiltyDraftManager();
    }

    public void finishImport() {
        if (miltySettings != null) {
            miltySettings.finishInitialization(this, null);
        }
    }

    public void newGameSetup() {
        secretObjectives = Mapper.getDecks().get("secret_objectives_pok").getNewShuffledDeck();
        actionCards = Mapper.getDecks().get("action_cards_pok").getNewShuffledDeck();
        explore = Mapper.getDecks().get("explores_pok").getNewShuffledDeck();
        publicObjectives1 = Mapper.getDecks().get("public_stage_1_objectives_pok").getNewShuffledDeck();
        publicObjectives2 = Mapper.getDecks().get("public_stage_2_objectives_pok").getNewShuffledDeck();
        agendas = Mapper.getDecks().get(getAgendaDeckID()).getNewShuffledDeck();
        events = new ArrayList<>();
        relics = Mapper.getDecks().get(getRelicDeckID()).getNewShuffledDeck();
        addCustomPO(Constants.CUSTODIAN, 1);
        setStrategyCardSet("pok");
    }

    public void fixScrewedSOs() {
        MessageHelper.sendMessageToChannel(getActionsChannel(),
            "The number of SOs in the deck before this operation is " + getNumberOfSOsInTheDeck()
                + ". The number in players hands is " + getNumberOfSOsInPlayersHands());

        List<String> defaultSecrets = Mapper.getDecks().get("secret_objectives_pok").getNewShuffledDeck();
        List<String> currentSecrets = new ArrayList<>(secretObjectives);
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
            "Fixed the SOs, the total amount of SOs in deck is " + getNumberOfSOsInTheDeck()
                + ". The number in players hands is " + getNumberOfSOsInPlayersHands());
    }

    @JsonIgnore
    public Player setupNeutralPlayer(String color) {
        addPlayer("572698679618568193", "Dicecord"); //Dicecord
        Player neutral = getPlayer("572698679618568193");
        neutral.setColor(color);
        neutral.setFaction("neutral");
        neutral.setDummy(true);
        FactionModel setupInfo = neutral.getFactionSetupInfo();
        Set<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        neutral.setUnitsOwned(playerOwnedUnits);

        return neutral;
    }

    @JsonIgnore
    public Player getNeutralPlayer(String fallbackColor) {
        if (players.get("572698679618568193") != null)
            return players.get("572698679618568193");
        return setupNeutralPlayer(fallbackColor);
    }

    @JsonIgnore
    public Player getNeutralPlayer() {
        if (players.get("572698679618568193") != null)
            return players.get("572698679618568193");
        return null;
    }

    public String pickNeutralColorID(List<String> exclusions) {
        // Start with the preferred colors, but then add all the colors to the list anyway
        List<String> colorPriority = new ArrayList<>(List.of("gray", "red", "blue", "green", "orange", "yellow", "black", "pink", "purple", "rose", "lime", "brown", "teal", "spring", "petrol", "lightgray"));
        colorPriority.addAll(Mapper.getColorNames());
        List<String> preferredNeutralColors = new ArrayList<>(colorPriority.stream().map(Mapper::getColorID).toList());

        // Build the full set of exclusions based on the argument plus the list of players
        Set<String> excludedColorIDs = new HashSet<>(exclusions);
        excludedColorIDs.addAll(getPlayers().values().stream().map(Player::getColorID).toList());

        // Finally, pick a color
        String neutralColorID = preferredNeutralColors.stream().filter(colorID -> !excludedColorIDs.contains(colorID)).findFirst().orElse(null);
        if (neutralColorID == null) {
            MessageHelper.sendMessageToChannel(getActionsChannel(), "Could not determine a good neutral unit color " + Constants.jazzPing());
        }
        return neutralColorID;
    }

    public int getNumberOfSOsInTheDeck() {
        return secretObjectives.size();
    }

    public String getEndedDateString() {
        return Helper.getDateRepresentation(endedDate);
    }

    public boolean hasBorderAnomalyOn(String tile, Integer direction) {
        List<BorderAnomalyHolder> anomaliesOnBorder = borderAnomalies.stream()
            .filter(anomaly -> anomaly.getType() != BorderAnomalyModel.BorderAnomalyType.ARROW)
            .filter(anomaly -> anomaly.getTile().equals(tile))
            .filter(anomaly -> anomaly.getDirection() == direction)
            .collect(Collectors.toList());
        return isNotEmpty(anomaliesOnBorder);
    }

    public void addBorderAnomaly(String tile, Integer direction, BorderAnomalyModel.BorderAnomalyType anomalyType) {
        borderAnomalies.add(new BorderAnomalyHolder(tile, direction, anomalyType));
    }

    public void removeBorderAnomaly(String tile, Integer direction) {
        borderAnomalies.removeIf(anom -> anom.getTile().equals(tile) && anom.getDirection() == direction);
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

    public Map<String, Object> getExportableFieldMap() {
        Class<? extends Game> aClass = getClass();
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

    public MiltySettings initializeMiltySettings() {
        if (miltySettings == null) {
            miltySettings = new MiltySettings();
            miltySettings.finishInitialization(this, null);
        }
        return miltySettings;
    }

    public void setPurgedPN(String purgedPN) {
        this.purgedPN.add(purgedPN);
    }

    public void removePurgedPN(String purgedPN) {
        this.purgedPN.remove(purgedPN);
    }

    public BagDraft getActiveBagDraft() {
        return activeDraft;
    }

    public void setBagDraft(BagDraft draft) {
        activeDraft = draft;
    }

    public void addActionCardDuplicates(List<String> acIDs) {
        actionCards.addAll(acIDs);
        Collections.shuffle(actionCards);
    }

    public void addSecretDuplicates(List<String> soIDs) {
        secretObjectives.addAll(soIDs);
        Collections.shuffle(secretObjectives);
    }

    public void setPurgedPNs(List<String> purgedPN) {
        this.purgedPN = purgedPN;
    }

    public List<String> getPurgedPN() {
        return purgedPN;
    }

    public int getVp() {
        return vp;
    }

    public void setVp(int vp) {
        this.vp = vp;
    }

    @JsonIgnore
    public Optional<Player> getWinner() {
        Player winner = null;
        for (Player player : getRealPlayersNDummies()) {
            if (player.getTotalVictoryPoints() >= getVp()) {
                if (winner == null) {
                    winner = player;
                } else if (isNotEmpty(player.getSCs()) && isNotEmpty(winner.getSCs())) {
                    winner = getLowestInitiativePlayer(player, winner);
                } else {
                    return Optional.empty();
                }
            }
        }
        return Optional.ofNullable(winner);
    }

    private static Player getLowestInitiativePlayer(Player player1, Player player2) {
        if (Collections.min(player1.getSCs()) < Collections.min(player2.getSCs())) {
            return player1;
        }
        return player2;
    }

    public int getRound() {
        return round;
    }

    public int getButtonPressCount() {
        return buttonPress;
    }

    public void increaseButtonPressCount() {
        buttonPress++;
    }

    public void setButtonPressCount(int count) {
        buttonPress = count;
    }

    public int getSlashCommandsRunCount() {
        return getAllSlashCommandsUsed().values().stream().mapToInt(Integer::intValue).sum();
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
        for (Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {

            if (Mapper.getActionCard(ac.getKey()) != null
                && Mapper.getActionCard(ac.getKey()).getName().contains(name)) {
                return true;
            } else {
                if (Mapper.getActionCard(ac.getKey()) == null) {
                    BotLogger.log(ac.getKey() + " is returning a null AC when sent to Mapper in game " + getName());
                }
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

    // GAME MODES
    public boolean isCompetitiveTIGLGame() {
        return competitiveTIGLGame;
    }

    public void setCompetitiveTIGLGame(boolean competitiveTIGLGame) {
        if (absolMode || miltyModMode || discordantStarsMode || homebrewSCMode || fowMode || allianceMode || communityMode)
            competitiveTIGLGame = false;
        this.competitiveTIGLGame = competitiveTIGLGame;
    }

    public boolean isCommunityMode() {
        return communityMode;
    }

    public void setCommunityMode(boolean communityMode) {
        this.communityMode = communityMode;
    }

    public boolean isAllianceMode() {
        for (Player player : getRealPlayers()) {
            if (player.getAllianceMembers() != null && !player.getAllianceMembers().replace(player.getFaction(), "").isEmpty()) {
                allianceMode = true;
            }
        }
        return allianceMode;
    }

    public void setAllianceMode(boolean allianceMode) {
        this.allianceMode = allianceMode;
    }

    public boolean isHomeBrew() {
        return homeBrew;
    }

    public void setHomeBrew(boolean homebrew) {
        homeBrew = homebrew;
    }

    public boolean isFoWMode() {
        return fowMode;
    }

    public void setFoWMode(boolean fowMode) {
        this.fowMode = fowMode;
    }

    public Map<String, String> getFowOptions() {
        return fowOptions;
    }

    public String getFowOption(String optionName) {
        return fowOptions.get(optionName);
    }

    public void setFowOption(String optionName, String value) {
        fowOptions.put(optionName, value);
    }

    public boolean isLightFogMode() {
        return lightFogMode;
    }

    public void setLightFogMode(boolean lightFogMode) {
        this.lightFogMode = lightFogMode;
    }

    public boolean isRedTapeMode() {
        return redTapeMode;
    }

    public void setRedTapeMode(boolean redTape) {
        redTapeMode = redTape;
    }

    public boolean isBaseGameMode() {
        return baseGameMode;
    }

    public void setBaseGameMode(boolean baseGameMode) {
        this.baseGameMode = baseGameMode;
    }

    public boolean isHomeBrewSCMode() {
        return homebrewSCMode;
    }

    public void setHomeBrewSCMode(boolean homeBrewSCMode) {
        homebrewSCMode = homeBrewSCMode;
    }

    public boolean isSpinMode() {
        return spinMode;
    }

    public void setSpinMode(boolean homeBrewSCMode) {
        spinMode = homeBrewSCMode;
    }

    public boolean isStratPings() {
        return stratPings;
    }

    public void setStratPings(boolean stratPings) {
        this.stratPings = stratPings;
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

    public boolean isAgeOfExplorationMode() {
        return ageOfExplorationMode;
    }

    public boolean isMinorFactionsMode() {
        return minorFactionsMode;
    }

    public void setTestBetaFeaturesMode(boolean testBetaFeaturesMode) {
        this.testBetaFeaturesMode = testBetaFeaturesMode;
    }

    public void setAgeOfExplorationMode(boolean ageOfExplorationMode) {
        this.ageOfExplorationMode = ageOfExplorationMode;
    }

    public void setMinorFactionsMode(boolean minorMode) {
        this.minorFactionsMode = minorMode;
    }

    @JsonIgnore
    public String getGameModesText() {
        Map<String, Boolean> gameModes = new HashMap<>() {
            {
                put(Emojis.TI4PoK + "Normal", isNormalGame());
                put(Emojis.TI4BaseGame + "Base Game", isBaseGameMode());
                put(Emojis.MiltyMod + "MiltyMod", isMiltyModMode());
                put(Emojis.TIGL + "TIGL", isCompetitiveTIGLGame());
                put("Community", isCommunityMode());
                put("Minor Factions", isMinorFactionsMode());
                put("Age of Exploration", isAgeOfExplorationMode());
                put("Alliance", isAllianceMode());
                put("FoW", isFoWMode());
                put("Franken", isFrankenGame());
                put(Emojis.Absol + "Absol", isAbsolMode());
                put(Emojis.DiscordantStars + "DiscordantStars", isDiscordantStarsMode());
                put("HomebrewSC", isHomeBrewSCMode());
                put("Little Omega", isLittleOmega());
                put("AC Deck 2", "action_deck_2".equals(getAcDeckID()));
                put("Homebrew", hasHomebrew());
            }
        };
        return gameModes.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
            .collect(Collectors.joining(", "));
    }

    @JsonIgnore
    public boolean isNormalGame() {
        return !hasHomebrew();
    }

    public boolean isFrankenGame() {
        return getRealPlayers().stream().anyMatch(p -> p.getFaction().toLowerCase().contains("franken"));
    }

    @JsonIgnore
    public TextChannel getTableTalkChannel() {
        try {
            return AsyncTI4DiscordBot.jda.getTextChannelById(getTableTalkChannelID());
        } catch (Exception e) {
            TextChannel tableTalkChannel;
            List<TextChannel> gameChannels = AsyncTI4DiscordBot.jda.getTextChannels().stream()
                .filter(c -> c.getName().startsWith(getName()))
                .filter(Predicate.not(c -> c.getName().contains(Constants.ACTIONS_CHANNEL_SUFFIX)))
                .toList();
            if (gameChannels.size() == 1) {
                tableTalkChannel = gameChannels.get(0);
                setTableTalkChannelID(tableTalkChannel.getId());
                return tableTalkChannel;
            }
            // BotLogger.log("Could not retrieve TableTalkChannel for " + getName(), e);
        }
        return null;
    }

    public void setTableTalkChannelID(String channelID) {
        tableTalkChannelID = channelID;
    }

    public String getTableTalkChannelID() {
        return tableTalkChannelID;
    }

    @JsonIgnore
    public TextChannel getMainGameChannel() {
        try {
            return AsyncTI4DiscordBot.jda.getTextChannelById(getMainGameChannelID());
        } catch (Exception e) {
            List<TextChannel> gameChannels = AsyncTI4DiscordBot.jda
                .getTextChannelsByName(getName() + Constants.ACTIONS_CHANNEL_SUFFIX, true);
            if (gameChannels.size() == 1) {
                TextChannel mainGameChannel = gameChannels.get(0);
                setMainGameChannelID(mainGameChannel.getId());
                return mainGameChannel;
            }
            // BotLogger.log("Could not retrieve MainGameChannel for " + getName(), e);
        }
        return null;
    }

    @JsonIgnore
    public TextChannel getSavedChannel() {
        try {
            return AsyncTI4DiscordBot.jda.getTextChannelById(getSavedChannelID());
        } catch (Exception e) {
            return getMainGameChannel();
        }
    }

    public void setMainGameChannelID(String channelID) {
        mainChannelID = channelID;
    }

    public void setSavedChannelID(String channelID) {
        savedChannelID = channelID;
    }

    public void setSavedMessage(String msg) {
        savedMessage = msg;
    }

    public String getMainGameChannelID() {
        return mainChannelID;
    }

    public String getSavedChannelID() {
        return savedChannelID;
    }

    public String getSavedMessage() {
        return savedMessage;
    }

    @JsonIgnore
    public TextChannel getActionsChannel() {
        return getMainGameChannel();
    }

    @JsonIgnore
    public ThreadChannel getBotMapUpdatesThread() {
        if (isFoWMode()) {
            return null;
        }

        // FIND BY ID
        if (StringUtils.isNumeric(getBotMapUpdatesThreadID())) {
            ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(getBotMapUpdatesThreadID());
            if (threadChannel != null) {
                return threadChannel;
            }
        }

        // FIND BY NAME
        List<ThreadChannel> botChannels = AsyncTI4DiscordBot.jda.getThreadChannelsByName(getName() + Constants.BOT_CHANNEL_SUFFIX, true);
        if (botChannels.size() == 1) {
            return botChannels.get(0);
        } else if (botChannels.size() > 1) {
            BotLogger.log(getName() + " appears to have more than one bot-map-updates channel:\n" + botChannels.stream().map(ThreadChannel::getJumpUrl).collect(Collectors.joining("\n")));
            return botChannels.get(0);
        }

        // CHECK IF ARCHIVED
        if (getActionsChannel() == null) {
            BotLogger.log(getName() + " does not have an actions channel and therefore can't find the bot-map-updates channel");
            return null;
        }
        for (ThreadChannel archivedChannel : getActionsChannel().retrieveArchivedPublicThreadChannels()) {
            if (archivedChannel.getId().equals(getBotMapUpdatesThreadID()) || archivedChannel.getName().equals(getName() + Constants.BOT_CHANNEL_SUFFIX)) {
                setBotMapUpdatesThreadID(archivedChannel.getId());
                return archivedChannel;
            }
        }
        setBotMapUpdatesThreadID(null);
        return null;
    }

    public void setBotMapUpdatesThreadID(String threadID) {
        botMapUpdatesThreadID = threadID;
    }

    public String getBotMapUpdatesThreadID() {
        return botMapUpdatesThreadID;
    }

    /**
     * @return Guild that the ActionsChannel or MainGameChannel resides
     */
    @Nullable
    @JsonIgnore
    public Guild getGuild() {
        return getActionsChannel() == null ? null : getActionsChannel().getGuild();
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
        endedDate = dateEnded;
    }

    // Position, Tile
    private Map<String, Tile> tileMap = new HashMap<>();

    public Map<Integer, Boolean> getScPlayed() {
        return scPlayed;
    }

    public Map<String, String> getCurrentAgendaVotes() {
        return currentAgendaVotes;
    }

    public void setCurrentReacts(String messageID, String factionsWhoReacted) {
        checkingForAllReacts.put(messageID, factionsWhoReacted);
    }

    public void removeMessageIDFromCurrentReacts(String messageID) {
        checkingForAllReacts.remove(messageID);
    }

    public Map<String, String> getMessagesThatICheckedForAllReacts() {
        return checkingForAllReacts;
    }

    public String getFactionsThatReactedToThis(String messageID) {
        if (checkingForAllReacts.get(messageID) != null) {
            return checkingForAllReacts.get(messageID);
        } else {
            return "";
        }
    }

    public void setStoredValue(String key, String value) {
        checkingForAllReacts.put(key, value);
    }

    public String getStoredValue(String key) {
        return getFactionsThatReactedToThis(key);
    }

    public void removeStoredValue(String key) {
        checkingForAllReacts.remove(key);
    }

    public void resetCurrentAgendaVotes() {
        currentAgendaVotes = new HashMap<>();
    }

    @JsonIgnore
    public Set<Integer> getPlayedSCs() {
        return getScPlayed().entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    public List<Integer> getPlayedSCsInOrder(Player player) {
        Set<Integer> playedSCs = getPlayedSCs();

        List<Integer> orderedSCsBasic = new ArrayList<>(playedSCs);
        Collections.sort(orderedSCsBasic);
        List<Integer> orderedSCs = new ArrayList<>();
        int playerSC = player.getLowestSC();
        String scText = playerSC + "";
        if (!scText.equalsIgnoreCase(getSCNumberIfNaaluInPlay(player, scText))) {
            playerSC = 0;
        }
        for (int sc : orderedSCsBasic) {
            Player holder = getPlayerFromSC(sc);
            String scT = sc + "";
            int judger = sc;
            if (!scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
                judger = 0;
            }
            if (judger > playerSC) {
                orderedSCs.add(sc);
            }
        }
        for (int sc : orderedSCsBasic) {
            Player holder = getPlayerFromSC(sc);
            String scT = sc + "";
            int judger = sc;
            if (!scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
                judger = 0;
            }
            if (judger < playerSC) {
                orderedSCs.add(sc);
            }
        }

        return orderedSCs;
    }

    public Player getPlayerFromSC(int sc) {
        for (Player player : getRealPlayersNDummies()) {
            if (player.getSCs().contains(sc)) {
                return player;
            }
        }
        return null;
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
        if (getTileMap().isEmpty()) {
            return 0;
        }
        Map<String, Tile> tileMap = new HashMap<>(getTileMap());
        String highestPosition = tileMap.keySet().stream()
            .filter(Helper::isInteger)
            .max(Comparator.comparingInt(Integer::parseInt))
            .orElse(null);
        if (highestPosition == null) {
            return 0;
        }
        String firstTwoDigits = StringUtils.left(highestPosition, highestPosition.length() - 2);
        if (!Helper.isInteger(firstTwoDigits)) {
            return 0;
        }
        return Integer.parseInt(firstTwoDigits);
    }

    public int getActivationCount() {
        return activationCount;
    }

    public boolean getNaaluAgent() {
        return naaluAgent;
    }

    public boolean getL1Hero() {
        return l1Hero;
    }

    public boolean getNomadCoin() {
        return nomadCoin;
    }

    public boolean getUndoButton() {
        return undoButtonOffered;
    }

    public boolean isFastSCFollowMode() {
        return fastSCFollowMode;
    }

    public boolean getQueueSO() {
        return queueSO;
    }

    public boolean getShowBubbles() {
        return showBubbles;
    }

    public boolean getShowGears() {
        return showGears;
    }

    public boolean getTemporaryPingDisable() {
        return temporaryPingDisable;
    }

    public boolean getDominusOrbStatus() {
        return dominusOrb;
    }

    public boolean getComponentAction() {
        return componentAction;
    }

    public boolean getJustPlayedComponentAC() {
        return justPlayedComponentAC;
    }

    public void setNaaluAgent(boolean onStatus) {
        naaluAgent = onStatus;
    }

    public void setL1Hero(boolean onStatus) {
        l1Hero = onStatus;
    }

    public void setNomadCoin(boolean onStatus) {
        nomadCoin = onStatus;
    }

    public void setUndoButton(boolean onStatus) {
        undoButtonOffered = onStatus;
    }

    public void setFastSCFollowMode(boolean onStatus) {
        fastSCFollowMode = onStatus;
    }

    public void setQueueSO(boolean onStatus) {
        queueSO = onStatus;
    }

    public void setShowBubbles(boolean onStatus) {
        showBubbles = onStatus;
    }

    public void setShowGears(boolean onStatus) {
        showGears = onStatus;
    }

    public void setTemporaryPingDisable(boolean onStatus) {
        temporaryPingDisable = onStatus;
    }

    public void setDominusOrb(boolean onStatus) {
        dominusOrb = onStatus;
    }

    public void setComponentAction(boolean onStatus) {
        componentAction = onStatus;
    }

    public void setJustPlayedComponentAC(boolean onStatus) {
        justPlayedComponentAC = onStatus;
    }

    public void setActivationCount(int count) {
        activationCount = count;
    }

    public void setSCPlayed(Integer scNumber, Boolean playedStatus) {
        scPlayed.put(scNumber, playedStatus);
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

    public String getActivePlayerID() {
        return activePlayer;
    }

    @JsonIgnore
    public Player getActivePlayer() {
        return getPlayer(activePlayer);
    }

    public String getActiveSystem() {
        if (activeSystem == null || activeSystem.isEmpty()) {
            return getStoredValue("lastActiveSystem");
        }
        return activeSystem;
    }

    public void setActiveSystem(String system) {
        activeSystem = system;
    }

    public Map<String, Integer> getCurrentMovedUnitsFrom1System() {
        return displacedUnitsFrom1System;
    }

    public Map<String, Integer> getThalnosUnits() {
        return thalnosUnits;
    }

    public int getSpecificThalnosUnit(String unit) {
        if (thalnosUnits.containsKey(unit)) {
            return thalnosUnits.get(unit);
        } else {
            return 0;
        }
    }

    public Map<String, Integer> getAllSlashCommandsUsed() {
        return slashCommandsUsed;
    }

    public Map<String, Integer> getAllActionCardsSabod() {
        return actionCardsSabotaged;
    }

    public Map<String, Integer> getMovedUnitsFromCurrentActivation() {
        return displacedUnitsFromEntireTacticalAction;
    }

    public void setSpecificCurrentMovedUnitsFrom1System(String unit, int count) {
        displacedUnitsFrom1System.put(unit, count);
    }

    public void setSpecificThalnosUnit(String unit, int count) {
        thalnosUnits.put(unit, count);
    }

    public void incrementSpecificSlashCommandCount(String fullCommandName) {
        slashCommandsUsed.merge(fullCommandName, 1, (oldValue, newValue) -> oldValue + 1);
    }

    public void setSpecificSlashCommandCount(String command, int count) {
        slashCommandsUsed.put(command, count);
    }

    public void setSpecificActionCardSaboCount(String acName, int count) {
        actionCardsSabotaged.put(acName, count);
    }

    public void setCurrentMovedUnitsFrom1System(Map<String, Integer> displacedUnits) {
        displacedUnitsFrom1System = displacedUnits;
    }

    public void setThalnosUnits(Map<String, Integer> displacedUnits) {
        thalnosUnits = displacedUnits;
    }

    public void setSlashCommandsUsed(Map<String, Integer> commands) {
        slashCommandsUsed = commands;
    }

    public void setACSabod(Map<String, Integer> acs) {
        actionCardsSabotaged = acs;
    }

    public void setSpecificCurrentMovedUnitsFrom1TacticalAction(String unit, int count) {
        displacedUnitsFromEntireTacticalAction.put(unit, count);
    }

    public void setCurrentMovedUnitsFrom1TacticalAction(Map<String, Integer> displacedUnits) {
        displacedUnitsFromEntireTacticalAction = displacedUnits;
    }

    public void resetCurrentMovedUnitsFrom1System() {
        displacedUnitsFrom1System = new HashMap<>();
    }

    public void resetThalnosUnits() {
        thalnosUnits = new HashMap<>();
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
        activePlayer = player;
    }

    public Date getLastActivePlayerPing() {
        return lastActivePlayerPing;
    }

    public void setCurrentAgendaInfo(String agendaInfo) {
        currentAgendaInfo = agendaInfo;
    }

    public String getACDrawStatusInfo() {
        return currentACDrawStatusInfo;
    }

    public void setACDrawStatusInfo(String agendaInfo) {
        currentACDrawStatusInfo = agendaInfo;
    }

    public String getCurrentAgendaInfo() {
        return currentAgendaInfo;
    }

    public Date getLastTimeGamesChecked() {
        return lastTimeGamesChecked;
    }

    public void setLastTimeGamesChecked(Date time) {
        lastTimeGamesChecked = time;
    }

    public void setAutoPing(boolean status) {
        autoPingEnabled = status;
    }

    public boolean getAutoPingStatus() {
        return autoPingEnabled;
    }

    public long getAutoPingSpacer() {
        return autoPingSpacer;
    }

    public void setAutoPingSpacer(long spacer) {
        autoPingSpacer = spacer;
    }

    public void setLastActivePlayerPing(Date time) {
        lastActivePlayerPing = time;
    }

    public Date getLastActivePlayerChange() {
        return lastActivePlayerChange;
    }

    public void setLastActivePlayerChange(Date time) {
        lastActivePlayerChange = time;
    }

    public void setSentAgenda(String id) {
        Collection<Integer> values = sentAgendas.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        sentAgendas.put(id, identifier);
    }

    public int addDiscardAgenda(String id) {
        Collection<Integer> values = discardAgendas.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        discardAgendas.put(id, identifier);
        return identifier;
    }

    public int discardEvent(String eventID) {
        Collection<Integer> values = discardedEvents.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        discardedEvents.put(eventID, identifier);
        return identifier;
    }

    public void addMessageIDForSabo(String messageID) {
        messageIDsForSaboReacts.add(messageID);
    }

    public void removeMessageIDForSabo(String messageID) {
        messageIDsForSaboReacts.remove(messageID);
    }

    public void setMessageIDForSabo(List<String> messageIDs) {
        messageIDsForSaboReacts = messageIDs;
    }

    public List<String> getMessageIDsForSabo() {
        return messageIDsForSaboReacts;
    }

    public void addRevealedPublicObjective(String id) {
        Collection<Integer> values = revealedPublicObjectives.values();
        int identifier = 0;
        while (values.contains(identifier)) {
            identifier++;
        }
        revealedPublicObjectives.put(id, identifier);

        publicObjectives1Peeked.remove(id);
        publicObjectives2Peeked.remove(id);
    }

    public Map<Integer, Integer> getScTradeGoods() {
        return scTradeGoods;
    }

    public void setScTradeGoods(Map<Integer, Integer> scTradeGoods) {
        this.scTradeGoods = scTradeGoods;
    }

    public void setScTradeGood(Integer sc, Integer tradeGoodCount) {
        if (Objects.isNull(tradeGoodCount))
            tradeGoodCount = 0;
        if (tradeGoodCount > 0 && sc == ButtonHelper.getKyroHeroSC(this)) {
            Player player = getPlayerFromColorOrFaction(getStoredValue("kyroHeroPlayer"));
            if (player != null) {
                player.setTg(player.getTg() + tradeGoodCount);
                ButtonHelperAbilities.pillageCheck(player, this);
                ButtonHelperAgents.resolveArtunoCheck(player, this, tradeGoodCount);
                tradeGoodCount = 0;
                MessageHelper.sendMessageToChannel(getActionsChannel(), "The tgs that would be placed on the SC " + sc
                    + " have instead been given to the Kyro Hero player, as per Kyro Hero text");
            }
        }
        scTradeGoods.put(sc, tradeGoodCount);
    }

    public boolean addSC(Integer sc) {
        if (!scTradeGoods.containsKey(sc)) {
            setScTradeGood(sc, 0);
            return true;
        } else
            return false;
    }

    public boolean removeSC(Integer sc) {
        if (scTradeGoods.containsKey(sc)) {
            scTradeGoods.remove(sc);
            return true;
        } else
            return false;
    }

    @JsonIgnore
    public List<Integer> getSCList() {
        return (new ArrayList<>(getScTradeGoods().keySet()));
    }

    public Map<String, Integer> getRevealedPublicObjectives() {
        return revealedPublicObjectives;
    }

    public List<String> getPublicObjectives1() {
        return publicObjectives1;
    }

    public List<String> getSavedButtons() {
        return savedButtons;
    }

    public void saveButton(String button) {
        savedButtons.add(button);
    }

    public void setSavedButtons(List<String> savedButtonsPassed) {
        savedButtons = savedButtonsPassed;
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

    public Map.Entry<String, Integer> revealStage1() {
        if (publicObjectives1Peakable.isEmpty() || getCurrentPhase().contains("agenda")) {
            return revealObjective(publicObjectives1);
        } else {
            return revealObjective(publicObjectives1Peakable);
        }
    }

    public Map.Entry<String, Integer> revealStage2() {
        if (publicObjectives2Peakable.isEmpty() || getCurrentPhase().contains("agenda")) {
            return revealObjective(publicObjectives2);
        } else {
            return revealObjective(publicObjectives2Peakable);
        }
    }

    public void setUpPeakableObjectives(int num, int type) {
        if (type == 1) {
            while (publicObjectives1Peakable.size() != num) {
                if (publicObjectives1Peakable.size() > num) {
                    String id = publicObjectives1Peakable.remove(publicObjectives1Peakable.size() - 1);
                    publicObjectives1.add(id);
                    Collections.shuffle(publicObjectives1);
                } else {
                    Collections.shuffle(publicObjectives1);
                    String id = publicObjectives1.get(0);
                    publicObjectives1.remove(id);
                    publicObjectives1Peakable.add(id);
                }
            }
        } else {
            while (publicObjectives2Peakable.size() != num) {
                if (publicObjectives2Peakable.size() > num) {
                    String id = publicObjectives2Peakable.remove(publicObjectives2Peakable.size() - 1);
                    publicObjectives2.add(id);
                    Collections.shuffle(publicObjectives2);
                } else {
                    Collections.shuffle(publicObjectives2);
                    String id = publicObjectives2.get(0);
                    publicObjectives2.remove(id);
                    publicObjectives2Peakable.add(id);
                }
            }
        }
    }

    public void setUpPeakableObjectives(int num) {
        if (publicObjectives1Peakable != null && publicObjectives1Peakable.size() < num - 1) {
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
    }

    public String peekAtStage1(int place, Player player) {
        String objective = peekAtObjective(publicObjectives1Peakable, place);

        if (publicObjectives1Peeked.containsKey(objective) && !publicObjectives1Peeked.get(objective).contains(player.getUserID())) {
            publicObjectives1Peeked.get(objective).add(player.getUserID());
        } else {
            List<String> list = new ArrayList<>();
            list.add(player.getUserID());
            publicObjectives1Peeked.put(objective, list);
        }

        return objective;
    }

    public String peekAtStage2(int place, Player player) {
        String objective = peekAtObjective(publicObjectives2Peakable, place);

        if (publicObjectives2Peeked.containsKey(objective) && !publicObjectives2Peeked.get(objective).contains(player.getUserID())) {
            publicObjectives2Peeked.get(objective).add(player.getUserID());
        } else {
            List<String> list = new ArrayList<>();
            list.add(player.getUserID());
            publicObjectives2Peeked.put(objective, list);
        }

        return objective;
    }

    public Map.Entry<String, Integer> revealSpecificStage1(String id) {
        return revealSpecificObjective(publicObjectives1, id);
    }

    public Map.Entry<String, Integer> revealSpecificStage2(String id) {
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

    public void swapObjectiveOut(int stage1Or2, int place, String id) {
        if (stage1Or2 == 1) {
            String removed = publicObjectives1Peakable.remove(place);
            publicObjectives1Peakable.add(place, id);
            addObjectiveToDeck(removed);
        } else {
            String removed = publicObjectives2Peakable.remove(place);
            publicObjectives2Peakable.add(place, id);
            addObjectiveToDeck(removed);
        }
    }

    public String peekAtObjective(List<String> objectiveList, int place) {
        if (!objectiveList.isEmpty()) {
            place = place - 1;
            return objectiveList.get(place);
        }
        return null;
    }

    public String getTopObjective(int stage1Or2) {
        if (stage1Or2 == 1) {
            String id = publicObjectives1.get(0);
            publicObjectives1.remove(id);
            return id;
        } else {
            String id = publicObjectives2.get(0);
            publicObjectives2.remove(id);
            return id;
        }
    }

    public void addObjectiveToDeck(String id) {
        PublicObjectiveModel obj = Mapper.getPublicObjective(id);
        if (obj != null) {
            if (obj.getPoints() == 1) {
                publicObjectives1.add(id);
                Collections.shuffle(publicObjectives1);
            } else {
                publicObjectives2.add(id);
                Collections.shuffle(publicObjectives2);
            }
        }
    }

    public Map.Entry<String, Integer> revealObjective(List<String> objectiveList) {
        if (!objectiveList.isEmpty()) {
            String id = objectiveList.get(0);
            objectiveList.remove(id);
            addRevealedPublicObjective(id);
            for (Map.Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public Map.Entry<String, Integer> revealSpecificObjective(List<String> objectiveList, String id) {
        if (objectiveList.contains(id)) {
            objectiveList.remove(id);
            addRevealedPublicObjective(id);
            for (Map.Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public Map.Entry<String, Integer> addSpecificStage1(String objective) {
        return addSpecificObjective(publicObjectives1, objective);
    }

    public Map.Entry<String, Integer> addSpecificStage2(String objective) {
        return addSpecificObjective(publicObjectives2, objective);
    }

    public Map.Entry<String, Integer> addSpecificObjective(List<String> objectiveList, String objective) {
        if (!objectiveList.isEmpty()) {
            objectiveList.remove(objective);
            addRevealedPublicObjective(objective);
            for (Map.Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(objective)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public boolean shuffleObjectiveBackIntoDeck(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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
                publicObjectives1Peeked.remove(id);
                publicObjectives1.add(id);
                Collections.shuffle(publicObjectives1);
            } else if (po2.contains(id)) {
                publicObjectives2Peeked.remove(id);
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
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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

        if (round > 1 && discardAgendas.size() > 0) {
            custodiansTaken = true;
        }
        for (Player p : getRealPlayers()) {
            if (p.getPlanets().contains("mr")) {
                return true;
            }
        }
        return custodiansTaken;
    }

    public boolean scorePublicObjective(String userID, Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
            if (!Constants.CUSTODIAN.equals(id) && !Constants.IMPERIAL_RIDER.equals(id) && scoredPlayerList.contains(userID)) {
                return false;
            }
            scoredPlayerList.add(userID);
            scoredPublicObjectives.put(id, scoredPlayerList);
            return true;
        }
        return false;
    }

    public boolean didPlayerScoreThisAlready(String userID, String id) {
        List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
        return scoredPlayerList.contains(userID);
    }

    public boolean scorePublicObjectiveEvenIfAlreadyScored(String userID, Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
            scoredPlayerList.add(userID);
            scoredPublicObjectives.put(id, scoredPlayerList);
            return true;
        }
        return false;
    }

    public boolean unscorePublicObjective(String userID, Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
            return scoredPlayerList.remove(userID);
        }
        return false;
    }

    public boolean unscorePublicObjective(String userID, String id) {
        if (!id.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(id, key -> new ArrayList<>());
            return scoredPlayerList.remove(userID);
        }
        return false;
    }

    public Integer addCustomPO(String poName, int vp) {
        customPublicVP.put(poName, vp);
        addRevealedPublicObjective(poName);
        return revealedPublicObjectives.get(poName);
    }

    public int getHighestScore() {
        int most = 0;
        for (Player p : getRealPlayers()) {
            if (p.getTotalVictoryPoints() > most) {
                most = p.getTotalVictoryPoints();
            }
        }
        return most;
    }

    public boolean removeCustomPO(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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
        if (publicObjectives1.remove(id))
            return true;
        if (publicObjectives2.remove(id))
            return true;
        return revealedPublicObjectives.remove(id) != null;
    }

    public boolean removeACFromGame(String id) {
        return actionCards.remove(id);
    }

    public boolean removeAgendaFromGame(String id) {
        return agendas.remove(id);
    }

    public Map<String, Integer> getCustomPublicVP() {
        return customPublicVP;
    }

    public void setCustomPublicVP(Map<String, Integer> customPublicVP) {
        this.customPublicVP = customPublicVP;
    }

    public void setRevealedPublicObjectives(Map<String, Integer> revealedPublicObjectives) {
        this.revealedPublicObjectives = revealedPublicObjectives;
    }

    public void setScoredPublicObjectives(Map<String, List<String>> scoredPublicObjectives) {
        this.scoredPublicObjectives = scoredPublicObjectives;
    }

    public void setCustomAdjacentTiles(Map<String, List<String>> customAdjacentTiles) {
        this.customAdjacentTiles = customAdjacentTiles;
    }

    public void addCustomAdjacentTiles(String primaryTile, List<String> customAdjacentTiles) {
        this.customAdjacentTiles.put(primaryTile, customAdjacentTiles);
    }

    public void removeCustomAdjacentTiles(String primaryTile) {
        customAdjacentTiles.remove(primaryTile);
    }

    public void clearCustomAdjacentTiles() {
        customAdjacentTiles.clear();
    }

    public void setPublicObjectives1(List<String> publicObjectives1) {
        this.publicObjectives1 = publicObjectives1;
    }

    public void setPublicObjectives2(List<String> publicObjectives2) {
        this.publicObjectives2 = publicObjectives2;
    }

    public void setPublicObjectives1Peakable(List<String> publicObjectives1) {
        publicObjectives1Peakable = publicObjectives1;
    }

    public void setPublicObjectives2Peakable(List<String> publicObjectives2) {
        publicObjectives2Peakable = publicObjectives2;
    }

    public void removePublicObjective1(String key) {
        publicObjectives1.remove(key);
    }

    public void removePublicObjective2(String key) {
        publicObjectives2.remove(key);
    }

    public List<String> getSoToPoList() {
        return soToPoList;
    }

    public void setSoToPoList(List<String> soToPoList) {
        this.soToPoList = soToPoList;
    }

    public void addToSoToPoList(String id) {
        soToPoList.add(id);
    }

    public void removeFromSoToPoList(String id) {
        soToPoList.remove(id);
    }

    public Map<String, List<String>> getScoredPublicObjectives() {
        return scoredPublicObjectives;
    }

    public Map<String, List<String>> getCustomAdjacentTiles() {
        return customAdjacentTiles;
    }

    @JsonGetter
    @JsonSerialize(keyUsing = MapPairKeySerializer.class)
    public Map<Pair<String, Integer>, String> getAdjacentTileOverrides() {
        return adjacencyOverrides;
    }

    public void addAdjacentTileOverride(String primaryTile, int direction, String secondaryTile) {
        Pair<String, Integer> primary = new ImmutablePair<>(primaryTile, direction);
        Pair<String, Integer> secondary = new ImmutablePair<>(secondaryTile, (direction + 3) % 6);

        adjacencyOverrides.put(primary, secondaryTile);
        adjacencyOverrides.put(secondary, primaryTile);
    }

    @JsonIgnore
    public void setAdjacentTileOverride(Map<Pair<String, Integer>, String> overrides) {
        adjacencyOverrides = new LinkedHashMap<>(overrides);
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

    public Map<String, Integer> getLaws() {
        return laws;
    }

    public Map<String, String> getLawsInfo() {
        return lawsInfo;
    }

    public void setAgendas(List<String> agendas) {
        this.agendas = agendas;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    public void shuffleAgendas() {
        Collections.shuffle(agendas);
    }

    public void shuffleEvents() {
        Collections.shuffle(events);
    }

    public void resetAgendas() {
        agendas = Mapper.getDecks().get(getAgendaDeckID()).getNewShuffledDeck();
        discardAgendas = new LinkedHashMap<>();
    }

    public void resetEvents() {
        DeckModel eventDeckModel = Mapper.getDecks().get(getEventDeckID());
        if (eventDeckModel == null) {
            events = new ArrayList<>();
        } else {
            events = eventDeckModel.getNewShuffledDeck();
        }
        discardedEvents = new LinkedHashMap<>();
    }

    public void resetDrawStateAgendas() {
        sentAgendas.clear();
    }

    @JsonSetter
    public void setDiscardAgendas(Map<String, Integer> discardAgendas) {
        this.discardAgendas = discardAgendas;
    }

    public void setDiscardedEvents(Map<String, Integer> discardedEvents) {
        this.discardedEvents = discardedEvents;
    }

    public void setDiscardAgendas(List<String> discardAgendasList) {
        Map<String, Integer> discardAgendas = new LinkedHashMap<>();
        for (String card : discardAgendasList) {
            Collection<Integer> values = discardAgendas.values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
            }
            discardAgendas.put(card, identifier);
        }
        this.discardAgendas = discardAgendas;
    }

    public void setSentAgendas(Map<String, Integer> sentAgendas) {
        this.sentAgendas = sentAgendas;
    }

    public void setLaws(Map<String, Integer> laws) {
        this.laws = laws;
    }

    public void setLawsInfo(Map<String, String> lawsInfo) {
        this.lawsInfo = lawsInfo;
    }

    public List<String> getAgendas() {
        return agendas;
    }

    public Map<String, Integer> getSentAgendas() {
        return sentAgendas;
    }

    public Map<String, Integer> getDiscardAgendas() {
        return discardAgendas;
    }

    public boolean addEventInEffect(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> event : discardedEvents.entrySet()) {
            if (event.getValue().equals(idNumber)) {
                id = event.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            Collection<Integer> values = eventsInEffect.values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
            }
            discardedEvents.remove(id);
            eventsInEffect.put(id, identifier);
            return true;
        }
        return false;
    }

    public boolean addLaw(Integer idNumber, String optionalText) {
        String id = "";
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {

            Collection<Integer> values = laws.values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
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
        for (Map.Entry<String, Integer> ac : laws.entrySet()) {
            if (ac.getValue().equals(idNumber)) {
                id = ac.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            if ("warrant".equalsIgnoreCase(id)) {
                for (Player p2 : getRealPlayers()) {
                    if (ButtonHelper.isPlayerElected(this, p2, id)) {
                        p2.setSearchWarrant(false);
                    }
                }
            }
            laws.remove(id);
            lawsInfo.remove(id);
            idNumber = addDiscardAgenda(id);
        }
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            Collection<Integer> values = laws.values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
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

    public boolean shuffleEventBackIntoDeck(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> event : discardedEvents.entrySet()) {
            if (event.getValue().equals(idNumber)) {
                id = event.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardedEvents.remove(id);
            events.add(id);
            shuffleEvents();
            return true;
        }
        return false;
    }

    public void shuffleAllAgendasBackIntoDeck() {
        List<String> discardedAgendasIDs = new ArrayList<>();
        discardedAgendasIDs.addAll(discardAgendas.keySet());
        for (String id : discardedAgendasIDs) {
            discardAgendas.remove(id);
            agendas.add(id);
            shuffleAgendas();
        }
    }

    public boolean shuffleAgendaBackIntoDeck(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
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

    public boolean putEventBackIntoDeckOnTop(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> event : discardedEvents.entrySet()) {
            if (event.getValue().equals(idNumber)) {
                id = event.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardedEvents.remove(id);
            events.add(0, id);
            return true;
        }
        return false;
    }

    public boolean putAgendaBackIntoDeckOnTop(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
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

    public boolean putAgendaBackIntoDeckOnTop(String id) {
        if (!id.isEmpty()) {
            discardAgendas.remove(id);
            agendas.add(0, id);
            return true;
        }
        return false;
    }

    public boolean removeEventInEffect(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> event : eventsInEffect.entrySet()) {
            if (event.getValue().equals(idNumber)) {
                id = event.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            eventsInEffect.remove(id);
            discardEvent(id);
            return true;
        }
        return false;
    }

    public boolean removeLaw(Integer idNumber) {
        String id = "";
        for (Map.Entry<String, Integer> ac : laws.entrySet()) {
            if (ac.getValue().equals(idNumber)) {
                id = ac.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            if ("warrant".equalsIgnoreCase(id)) {
                for (Player p2 : getRealPlayers()) {
                    if (ButtonHelper.isPlayerElected(this, p2, id)) {
                        p2.setSearchWarrant(false);
                    }
                }
            }
            laws.remove(id);
            lawsInfo.remove(id);
            addDiscardAgenda(id);
            return true;
        }
        return false;
    }

    public boolean removeLaw(String id) {
        if (!id.isEmpty()) {
            if ("warrant".equalsIgnoreCase(id)) {
                for (Player p2 : getRealPlayers()) {
                    if (ButtonHelper.isPlayerElected(this, p2, id)) {
                        p2.setSearchWarrant(false);
                    }
                }
            }
            laws.remove(id);
            lawsInfo.remove(id);
            addDiscardAgenda(id);

            return true;
        }
        return false;
    }

    public boolean putEventTop(Integer idNumber, Player player) {
        if (player.getEvents().containsValue(idNumber)) {
            String id = "";
            for (Map.Entry<String, Integer> event : player.getEvents().entrySet()) {
                if (event.getValue().equals(idNumber)) {
                    id = event.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                events.add(0, id);
                player.removeEvent(id);
                return true;
            }
        }
        return false;
    }

    public boolean putEventBottom(Integer idNumber, Player player) {
        if (player.getEvents().containsValue(idNumber)) {
            String id = "";
            for (Map.Entry<String, Integer> event : player.getEvents().entrySet()) {
                if (event.getValue().equals(idNumber)) {
                    id = event.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                events.add(id);
                player.removeEvent(id);
                return true;
            }
        }
        return false;
    }

    public boolean putAgendaTop(Integer idNumber) {
        if (sentAgendas.containsValue(idNumber)) {
            String id = "";
            for (Map.Entry<String, Integer> ac : sentAgendas.entrySet()) {
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
            for (Map.Entry<String, Integer> ac : sentAgendas.entrySet()) {
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
    public Map.Entry<String, Integer> drawAgenda() {
        if (!agendas.isEmpty()) {
            for (String id : agendas) {
                if (!sentAgendas.containsKey(id)) {
                    setSentAgenda(id);
                    for (Map.Entry<String, Integer> entry : sentAgendas.entrySet()) {
                        if (entry.getKey().equals(id)) {
                            return entry;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public Map.Entry<String, Integer> drawSpecificAgenda(String agendaID) {
        if (!agendas.isEmpty()) {
            for (String id : agendas) {
                if (agendaID.equalsIgnoreCase(id)) {
                    setSentAgenda(id);
                    for (Map.Entry<String, Integer> entry : sentAgendas.entrySet()) {
                        if (entry.getKey().equals(id)) {
                            return entry;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public Map.Entry<String, Integer> drawBottomAgenda() {
        if (!agendas.isEmpty()) {
            for (int i = agendas.size() - 1; i >= 0; i--) {
                String id = agendas.get(i);
                if (!sentAgendas.containsKey(id)) {
                    setSentAgenda(id);
                    for (Map.Entry<String, Integer> entry : sentAgendas.entrySet()) {
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

    public String lookAtTopEvent(int index) {
        return events.get(index);
    }

    public String lookAtBottomEvent(int indexFromEnd) {
        return events.get(events.size() - 1 - indexFromEnd);
    }

    public String revealEvent(boolean revealFromBottom) {
        int index = revealFromBottom ? events.size() - 1 : 0;
        String id = events.remove(index);
        discardEvent(id);
        return id;
    }

    public boolean revealEvent(String eventID, boolean force) {
        if (events.remove(eventID) || force) {
            discardEvent(eventID);
            return true;
        }
        return false;
    }

    public String revealAgenda(boolean revealFromBottom) {
        int index = revealFromBottom ? agendas.size() - 1 : 0;
        String id = agendas.remove(index);
        addDiscardAgenda(id);
        return id;
    }

    public boolean revealAgenda(String agendaID, boolean force) {
        if (agendas.remove(agendaID) || force) {
            addDiscardAgenda(agendaID);
            return true;
        }
        return false;
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

    public void drawActionCard(String userID, int count) {
        for (int x = 0; x < count; x++) {
            drawActionCard(userID);
        }
    }

    @Nullable
    public Map<String, Integer> drawActionCard(String userID) {
        if (!actionCards.isEmpty()) {
            String id = actionCards.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                actionCards.remove(id);
                player.setActionCard(id);
                return player.getActionCards();
            }
        } else {
            if (!discardActionCards.keySet().isEmpty()) {
                actionCards.addAll(discardActionCards.keySet());
                discardActionCards.clear();
                Collections.shuffle(actionCards);
                String msg = "# " + getPing() + " shuffling the discard ACs into the action card deck because the action card deck ran out of cards";
                MessageHelper.sendMessageToChannel(getMainGameChannel(), msg);
                return drawActionCard(userID);
            }
        }
        return null;
    }

    @Nullable
    public Map<String, Integer> drawEvent(String userID) {
        if (!events.isEmpty()) {
            String id = events.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                events.remove(id);
                player.setEvent(id);
                return player.getActionCards();
            }
        } else {
            events.addAll(discardedEvents.keySet());
            discardedEvents.clear();
            Collections.shuffle(events);
            return drawEvent(userID);
        }
        return null;
    }

    private List<String> getExplores(String reqType, List<String> superDeck) {
        List<String> deck = new ArrayList<>();
        for (String id : superDeck) {
            ExploreModel card = Mapper.getExplore(id);
            if (card != null) {
                String type = card.getType();
                if (reqType.equalsIgnoreCase(type)) {
                    deck.add(id);
                }
            }
        }
        return deck;
    }

    public List<String> getExploreDeck(String reqType) {
        return getExplores(reqType, explore);
    }

    public List<String> getExploreDiscard(String reqType) {
        return getExplores(reqType, discardExplore);
    }

    @JsonIgnore
    public List<String> getTechnologyDeck() {
        return Mapper.getDecks().get(getTechnologyDeckID()).getNewDeck();
    }

    @JsonIgnore
    public List<TechnologyModel> getPropulsionTechDeck() {
        return getTechnologyDeck().stream()
            .map(Mapper::getTech)
            .filter(TechnologyModel::isPropulsionTech)
            .sorted(TechnologyModel.sortByTechRequirements)
            .toList();
    }

    @JsonIgnore
    public List<TechnologyModel> getWarfareTechDeck() {
        return getTechnologyDeck().stream()
            .map(Mapper::getTech)
            .filter(TechnologyModel::isWarfareTech)
            .sorted(TechnologyModel.sortByTechRequirements)
            .toList();
    }

    @JsonIgnore
    public List<TechnologyModel> getCyberneticTechDeck() {
        return getTechnologyDeck().stream()
            .map(Mapper::getTech)
            .filter(TechnologyModel::isCyberneticTech)
            .sorted(TechnologyModel.sortByTechRequirements)
            .toList();
    }

    @JsonIgnore
    public List<TechnologyModel> getBioticTechDeck() {
        return getTechnologyDeck().stream()
            .map(Mapper::getTech)
            .filter(TechnologyModel::isBioticTech)
            .sorted(TechnologyModel.sortByTechRequirements)
            .toList();
    }

    public String drawExplore(String reqType) {
        List<String> deck = getExplores(reqType, explore);
        String result = null;

        // MIGRATION CODE TODO: Remove this once we are fairly certain no exising games
        // have an existing empty deck - implemented 2023-07
        if (deck.isEmpty()) {
            shuffleDiscardsIntoExploreDeck(reqType);
            deck = getExplores(reqType, explore);
            BotLogger.log("Map: `" + getName() + "` MIGRATION CODE TRIGGERED: Explore " + reqType
                + " deck was empty, shuffling discards into deck.");
        } // end of migration code

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
        List<String> anotherList = new ArrayList<>();
        anotherList.addAll(discardsOfType);
        for (String explore : anotherList) {
            addExplore(explore);
        }
    }

    public void shuffleExplores() {
        Collections.shuffle(explore);
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
            int place = ThreadLocalRandom.current().nextInt(explore.size());
            explore.add(place, id);
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
        explore = Mapper.getDecks().get("explores_pok").getNewDeck();
        for (String card : explore) {
            explore.add(card + "extra1");
            explore.add(card + "extra2");
        }
        Collections.shuffle(explore);
    }

    public void triplicateACs() {
        actionCards = Mapper.getDecks().get("action_cards_pok").getNewDeck();
        for (String card : actionCards) {
            actionCards.add(card + "extra1");
            actionCards.add(card + "extra2");
        }
        Collections.shuffle(actionCards);
    }

    public void triplicateSOs() {
        secretObjectives = Mapper.getDecks().get("secret_objectives_pok").getNewDeck();
        for (String card : secretObjectives) {
            secretObjectives.add(card + "extra1");
            secretObjectives.add(card + "extra2");
        }
        Collections.shuffle(secretObjectives);
    }

    public String drawRelic() {
        return drawRelic(0);
    }

    public String drawRelic(int location) {
        if (relics.isEmpty()) {
            return "";
        }
        return relics.remove(location);
    }

    public void shuffleRelics() {
        Collections.shuffle(relics);
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

    public void checkSOLimit(Player player) {
        if (player.getSecretsScored().size() + player.getSecretsUnscored().size() > player.getMaxSOCount()) {
            String msg = player.getRepresentation(true, true) + " you have more SOs than the limit ("
                + player.getMaxSOCount()
                + ") and should discard one. If your game is playing with a higher SO limit, you can change that in /game setup.";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            String secretScoreMsg = "Click a button below to discard your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(this, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), secretScoreMsg,
                    soButtons);
            } else {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Something went wrong. Please report to Fin");
            }
        }
    }

    public void drawSecretObjective(String userID) {
        if (!secretObjectives.isEmpty()) {
            String id = secretObjectives.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                secretObjectives.remove(id);
                player.setSecret(id);
                checkSOLimit(player);
            }
        }
    }

    @Nullable
    public Map<String, Integer> drawSpecificSecretObjective(String soID, String userID) {
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

    public void drawSpecificActionCard(String acID, String userID) {
        if (actionCards.isEmpty()) {
            return;
        }
        int tries = 0;
        while (tries < 3) {
            if (actionCards.contains(acID)) {
                Player player = getPlayer(userID);
                if (player != null) {
                    actionCards.remove(acID);
                    player.setActionCard(acID);
                    return;
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

    public void setDiscardActionCard(String id) {
        Collection<Integer> values = discardActionCards.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        discardActionCards.put(id, identifier);
    }

    public void setPurgedActionCard(String id) {
        Collection<Integer> values = purgedActionCards.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        purgedActionCards.put(id, identifier);
    }

    @JsonIgnore
    public boolean discardActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> actionCards = player.getActionCards();
            String acID = "";
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
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

    public boolean purgedActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> actionCards = player.getActionCards();
            String acID = "";
            for (Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                player.removeActionCard(acIDNumber);
                setPurgedActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public void shuffleActionCards() {
        Collections.shuffle(actionCards);
    }

    public Map<String, Integer> getDiscardActionCards() {
        return discardActionCards;
    }

    public Map<String, Integer> getPurgedActionCards() {
        return purgedActionCards;
    }

    public boolean pickActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            String acID = "";
            for (Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {
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

    public boolean pickActionCardFromPurged(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            String acID = "";
            for (Map.Entry<String, Integer> ac : purgedActionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                purgedActionCards.remove(acID);
                player.setActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public boolean shuffleActionCardBackIntoDeck(Integer acIDNumber) {
        String acID = "";
        for (Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {
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

    public boolean scoreSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecret(soIDNumber);
                player.setSecretScored(soID);
                return true;
            }
        }
        return false;
    }

    public boolean unscoreSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> secrets = player.getSecretsScored();
            String soID = "";
            for (Map.Entry<String, Integer> so : secrets.entrySet()) {
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

    public boolean unscoreAndShuffleSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> secrets = player.getSecretsScored();
            String soID = "";
            for (Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecretScored(soIDNumber);
                secretObjectives.add(soID);
                Collections.shuffle(secretObjectives);
                return true;
            }
        }
        return false;
    }

    public boolean discardSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (Map.Entry<String, Integer> so : secrets.entrySet()) {
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

    public void addSecretObjective(String id) {
        if (!secretObjectives.contains(id)) {
            secretObjectives.add(id);
            Collections.shuffle(secretObjectives);
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

    public void setExploreDeck(List<String> deck) {
        explore = deck;
    }

    public void setExploreDiscard(List<String> discard) {
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

    public void setRelics(List<String> deck) {
        relics = new ArrayList<>(deck);
    }

    public void resetRelics() {
        relics = Mapper.getDecks().get(getRelicDeckID()).getNewShuffledDeck();
    }

    public void triplicateRelics() {
        if (absolMode) {
            relics = Mapper.getDecks().get("relics_absol").getNewShuffledDeck();
            for (String relic : Mapper.getDecks().get("relics_absol").getNewShuffledDeck()) {
                String copy1 = relic + "extra1";
                String copy2 = relic + "extra2";
                relics.add(copy1);
                relics.add(copy2);
            }
        } else {
            relics = Mapper.getDecks().get("relics_pok").getNewShuffledDeck();
            for (String relic : Mapper.getDecks().get("relics_pok").getNewShuffledDeck()) {
                String copy1 = relic + "extra1";
                String copy2 = relic + "extra2";
                relics.add(copy1);
                relics.add(copy2);
            }
        }
        Collections.shuffle(relics);
    }

    public void setSecretObjectives(List<String> secretObjectives) {
        this.secretObjectives = secretObjectives;
    }

    public void setActionCards(List<String> actionCards) {
        this.actionCards = actionCards;
    }

    public boolean loadGameSettingsFromSettings(GenericInteractionCreateEvent event, MiltySettings miltySettings) {
        SourceSettings sources = miltySettings.getSourceSettings();
        if (sources.getAbsol().val) setAbsolMode(true);

        GameSettings settings = miltySettings.getGameSettings();
        setVp(settings.getPointTotal().val);
        setMaxSOCountPerPlayer(settings.getSecrets().val);
        setUpPeakableObjectives(settings.getStage1s().val, 1);
        setUpPeakableObjectives(settings.getStage2s().val, 2);
        setCompetitiveTIGLGame(settings.getTigl().val);
        setAllianceMode(settings.getAlliance().val);
        return validateAndSetAllDecks(event, miltySettings);
    }

    public boolean validateAndSetAllDecks(GenericInteractionCreateEvent event, MiltySettings miltySettings) {
        DeckSettings deckSettings = miltySettings.getGameSettings().getDecks();

        boolean success = true;
        // &= is the "and operator". It will assign true to success iff success is true and the result is true. Otherwise it will propagate a false value to the end
        success &= validateAndSetPublicObjectivesStage1Deck(event, deckSettings.getStage1().getValue());
        success &= validateAndSetPublicObjectivesStage2Deck(event, deckSettings.getStage2().getValue());
        success &= validateAndSetSecretObjectiveDeck(event, deckSettings.getSecrets().getValue());
        success &= validateAndSetActionCardDeck(event, deckSettings.getActionCards().getValue());
        success &= validateAndSetExploreDeck(event, deckSettings.getExplores().getValue());
        success &= validateAndSetTechnologyDeck(event, deckSettings.getTechs().getValue());
        //success &= validateAndSetEventDeck(event, deckSettings.getEvents().getValue());
        setStrategyCardSet(deckSettings.getStratCards().getChosenKey());

        if (absolMode && !deckSettings.getAgendas().getChosenKey().contains("absol")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game seems to be using absol mode, so the agenda deck you chose will be overridden.");
            success &= validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"));
        } else {
            success &= validateAndSetAgendaDeck(event, deckSettings.getAgendas().getValue());
        }
        if (absolMode && !deckSettings.getRelics().getChosenKey().contains("absol")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game seems to be using absol mode, so the relic deck you chose will be overridden.");
            success &= validateAndSetAgendaDeck(event, Mapper.getDeck("relics_absol"));
        } else {
            success &= validateAndSetAgendaDeck(event, deckSettings.getAgendas().getValue());
        }

        return success;
    }

    public boolean validateAndSetPublicObjectivesStage1Deck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getRevealedPublicObjectives().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change public objective deck to **"
                + deck.getName() + "** while there are revealed public objectives.");
            return false;
        }

        setStage1PublicDeckID(deck.getAlias());
        setPublicObjectives1(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetPublicObjectivesStage2Deck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getRevealedPublicObjectives().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change public objective deck to **"
                + deck.getName() + "** while there are revealed public objectives.");
            return false;
        }

        setStage2PublicDeckID(deck.getAlias());
        setPublicObjectives2(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetActionCardDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        boolean shuffledExtrasIn = false;
        List<String> oldDeck = new ArrayList<>();
        oldDeck.addAll(Mapper.getDeck(getAcDeckID()).getNewShuffledDeck());
        List<String> newDeck = new ArrayList<>();
        setAcDeckID(deck.getAlias());
        newDeck.addAll(deck.getNewShuffledDeck());
        for (String ac : oldDeck) {
            newDeck.remove(ac);
        }
        if (getDiscardActionCards().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Since there were ACs in the discard pile, will just shuffle any new ACs into the existing deck");
            shuffledExtrasIn = true;
        } else {
            for (Player player : getPlayers().values()) {
                if (player.getActionCards().size() > 0) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Since there were ACs in players hands, will just shuffle any new ACs into the existing deck");
                    shuffledExtrasIn = true;
                    break;
                }
            }
        }
        if (!shuffledExtrasIn) {
            setActionCards(deck.getNewShuffledDeck());
        } else {
            for (String acID : newDeck) {
                actionCards.add(acID);
            }
            Collections.shuffle(actionCards);
        }
        return true;
    }

    public boolean validateAndSetRelicDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        for (Player player : getPlayers().values()) {
            if (player.getRelics().size() > 0) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change relic deck to **"
                    + deck.getName() + "** while there are relics in player hands.");
                return false;
            }
        }
        setRelicDeckID(deck.getAlias());
        setRelics(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetSecretObjectiveDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        for (Player player : getPlayers().values()) {
            if (player.getSecrets().size() > 0) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Cannot change secret objective deck to **" + deck.getName()
                        + "** while there are secret objectives in player hands.");
                return false;
            }
        }
        setSoDeckID(deck.getAlias());
        setSecretObjectives(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetExploreDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getAllExploreDiscard().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change explore deck to **"
                + deck.getName() + "** while there are explores in the discard pile.");
            return false;
        }
        setExplorationDeckID(deck.getAlias());
        setExploreDeck(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetAgendaDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getDiscardAgendas().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change agenda deck to **"
                + deck.getName() + "** while there are agendas in the discard pile.");
            return false;
        }
        setAgendaDeckID(deck.getAlias());
        setAgendas(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetTechnologyDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        swapOutVariantTechs();
        setTechnologyDeckID(deck.getAlias());
        swapInVariantTechs();
        return true;
    }

    public boolean validateAndSetEventDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getDiscardedEvents().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change event deck to **"
                + deck.getName() + "** while there are events in the discard pile.");
            return false;
        }
        setEventDeckID(deck.getAlias());
        setEvents(deck.getNewShuffledDeck());
        return true;
    }

    @JsonSetter
    public void setDiscardActionCards(Map<String, Integer> discardActionCards) {
        this.discardActionCards = discardActionCards;
    }

    public void setPurgedActionCards(Map<String, Integer> purgedActionCards) {
        this.purgedActionCards = purgedActionCards;
    }

    public void setDiscardActionCards(List<String> discardActionCardList) {
        Map<String, Integer> discardActionCards = new LinkedHashMap<>();
        for (String card : discardActionCardList) {
            Collection<Integer> values = discardActionCards.values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
            }
            discardActionCards.put(card, identifier);
        }
        this.discardActionCards = discardActionCards;
    }

    @JsonIgnore
    public void setPurgedActionCards(List<String> purgedActionCardList) {
        Map<String, Integer> purgedActionCards = new LinkedHashMap<>();
        for (String card : purgedActionCardList) {
            Collection<Integer> values = purgedActionCards.values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
            }
            purgedActionCards.put(card, identifier);
        }
        this.purgedActionCards = purgedActionCards;
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

    public String getGameNameForSorting() {
        if (getName().startsWith("pbd")) {
            return StringUtils.leftPad(getName(), 10, "0");
        }
        if (getName().startsWith("fow")) {
            return StringUtils.leftPad(getName(), 10, "1");
        }
        return getName();
    }

    @JsonIgnore
    public String getPing() {
        Guild guild = getGuild();
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (getName().equals(role.getName().toLowerCase())) {
                    return role.getAsMention();
                }
            }
        }
        StringBuilder sb = new StringBuilder(getName()).append(" ");
        for (String playerID : getPlayerIDs()) {
            User user = AsyncTI4DiscordBot.jda.getUserById(playerID);
            if (user != null)
                sb.append(user.getAsMention()).append(" ");
        }
        return sb.toString();
    }

    public Map<String, Tile> getTileMap() {
        return tileMap;
    }

    public Tile getTile(String tileID) {
        if ("mirage".equalsIgnoreCase(tileID)) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder uh : tile.getUnitHolders().values()) {
                    if (uh.getTokenList() != null && (uh.getTokenList().contains("mirage")
                        || uh.getTokenList().contains("token_mirage.png"))) {
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
        Player player = new Player(id, name, getName());
        players.put(id, player);
    }

    public Player addPlayerLoad(String id, String name) {
        Player player = new Player(id, name, getName());
        players.put(id, player);
        return player;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    @JsonIgnore
    public List<Player> getRealPlayers() {
        return getPlayers().values().stream().filter(Player::isRealPlayer).collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getRealPlayersNDummies() {
        return getPlayers().values().stream().filter(player -> (player.isRealPlayer() || (player != null && player.isDummy() && player.getColor() != null && !"null".equals(player.getColor()))))
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getRealAndEliminatedPlayers() {
        return getPlayers().values().stream().filter(player -> (player.isRealPlayer() || player.isEliminated()))
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getRealAndEliminatedAndDummyPlayers() {
        return getPlayers().values().stream()
            .filter(player -> (player.isRealPlayer() || player.isEliminated() || player.isDummy()))
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getDummies() {
        return getPlayers().values().stream().filter(Player::isDummy).collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getNotRealPlayers() {
        return getPlayers().values().stream().filter(Player::isNotRealPlayer).collect(Collectors.toList());
    }

    @JsonIgnore
    public Set<String> getFactions() {
        return getRealAndEliminatedAndDummyPlayers().stream().map(Player::getFaction).collect(Collectors.toSet());
    }

    public void setPlayers(Map<String, Player> players) {
        this.players = players;
    }

    public void setCCNPlasticLimit(boolean limit) {
        ccNPlasticLimit = limit;
    }

    public void setBotFactionReactions(boolean limit) {
        botFactionReacts = limit;
    }

    public void setHasHadAStatusPhase(boolean limit) {
        hasHadAStatusPhase = limit;
    }

    public void setShushing(boolean limit) {
        botShushing = limit;
    }

    public boolean getCCNPlasticLimit() {
        return ccNPlasticLimit;
    }

    public boolean getBotFactionReacts() {
        return botFactionReacts;
    }

    public boolean getHasHadAStatusPhase() {
        return hasHadAStatusPhase;
    }

    public boolean getBotShushing() {
        return botShushing;
    }

    public void setPlayer(String playerID, Player player) {
        players.put(playerID, player);
    }

    public Player getPlayer(String userID) {
        if (userID == null)
            return null;
        return players.get(userID);
    }

    @JsonIgnore
    public Set<String> getPlayerIDs() {
        return players.keySet();
    }

    @JsonIgnore
    public List<String> getRealPlayerIDs() {
        List<String> pIDs = new ArrayList<>();
        for (Player player : getRealPlayers()) {
            pIDs.add(player.getUserID());
        }
        return pIDs;
    }

    public void removePlayer(String playerID) {
        players.remove(playerID);
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

    public void setTileMap(Map<String, Tile> tileMap) {
        this.tileMap = tileMap;
        planets.clear();
    }

    public void clearTileMap() {
        tileMap.clear();
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

    public Map<String, Planet> getPlanetsInfo() {
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
                for (Map.Entry<String, UnitHolder> unitHolderEntry : tile.getUnitHolders().entrySet()) {
                    if (unitHolderEntry.getValue() instanceof Planet p) {
                        planets.put(unitHolderEntry.getKey(), p);
                    }
                }
            }
            planets.put("custodiavigilia", new Planet("custodiavigilia", new Point(0, 0)));
            if ("custodiavigilia".equalsIgnoreCase(getStoredValue("terraformedPlanet"))) {
                planets.get("custodiavigilia").addToken(Constants.ATTACHMENT_TITANSPN_PNG);
            }
            planets.put("custodiavigiliaplus", new Planet("custodiavigiliaplus", new Point(0, 0)));
            planets.put("nevermore", new Planet("nevermore", new Point(0, 0)));
            planets.put("ghoti", new Planet("ghoti", new Point(0, 0)));
            if ("ghoti".equalsIgnoreCase(getStoredValue("terraformedPlanet"))) {
                planets.get("ghoti").addToken(Constants.ATTACHMENT_TITANSPN_PNG);
            }
        }
        return planets.keySet();
    }

    public void endGameIfOld() {
        if (isHasEnded())
            return;

        LocalDate currentDate = LocalDate.now();
        LocalDate lastModifiedDate = (new Date(this.lastModifiedDate)).toInstant().atZone(ZoneId.systemDefault())
            .toLocalDate();
        Period period = Period.ofMonths(2); // TODO: CANDIDATE FOR GLOBAL VARIABLE
        LocalDate oldestLastModifiedDateBeforeEnding = currentDate.minus(period);

        if (lastModifiedDate.isBefore(oldestLastModifiedDateBeforeEnding)) {
            BotLogger.log("Game: " + getName() + " has not been modified since ~" + lastModifiedDate
                + " - the game flag `hasEnded` has been set to true");
            setHasEnded(true);
            GameSaveLoadManager.saveMap(this);
        }
    }

    public void rebuildTilePositionAutoCompleteList() {
        setTileNameAutocompleteOptionsCache(getTileMap().values().stream()
            .map(tile -> new SimpleEntry<>(tile.getAutoCompleteName(), tile.getPosition()))
            .filter(e -> !e.getKey().toLowerCase().contains("hyperlane"))
            .toList());
    }

    @JsonIgnore
    public List<SimpleEntry<String, String>> getTileNameAutocompleteOptionsCache() {
        if (tileNameAutocompleteOptionsCache != null) {
            return tileNameAutocompleteOptionsCache;
        }
        rebuildTilePositionAutoCompleteList();
        return tileNameAutocompleteOptionsCache;
    }

    public void setTileNameAutocompleteOptionsCache(
        List<SimpleEntry<String, String>> tileNameAutocompleteOptionsCache) {
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
            BotLogger.log("`" + getName() + "`: there are duplicate promissory notes in the game:\n> `"
                + Helper.findDuplicateInList(allPlayerHandPromissoryNotes) + "`");
        }

        allPromissoryNotes.addAll(getPurgedPN());

        // Find PNs that are extra - players have them but nobody "owns" them
        List<String> unOwnedPromissoryNotes = new ArrayList<>(allPromissoryNotes);
        unOwnedPromissoryNotes.removeAll(allOwnedPromissoryNotes);
        if (unOwnedPromissoryNotes.size() > 0) {
            BotLogger.log("`" + getName() + "`: there are promissory notes in the game that no player owns:\n> `"
                + unOwnedPromissoryNotes + "`");
            getPurgedPN().removeAll(unOwnedPromissoryNotes);
        }

        // Remove unowned PNs from all players hands
        for (Player player : getPlayers().values()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotes().keySet());
            for (String pnID : pns) {
                if (unOwnedPromissoryNotes.contains(pnID)) {
                    player.removePromissoryNote(pnID);
                    BotLogger.log("`" + getName() + "`: removed promissory note `" + pnID + "` from player `"
                        + player.getUserName() + "` because nobody 'owned' it");
                }
            }
        }

        // Report PNs that are missing from the game
        List<String> missingPromissoryNotes = new ArrayList<>(allOwnedPromissoryNotes);
        missingPromissoryNotes.removeAll(allPromissoryNotes);
        if (missingPromissoryNotes.size() > 0) {
            BotLogger.log("`" + getName() + "`: there are promissory notes that should be in the game but are not:\n> `"
                + missingPromissoryNotes + "`");
        }
    }

    public boolean leaderIsFake(String leaderID) {
        return (getStoredValue("fakeCommanders").contains(leaderID) || getStoredValue("minorFactionCommanders").contains(leaderID));
    }

    public void addFakeCommander(String leaderID) {
        if (leaderID.contains("commander")) {
            String fakeString = getStoredValue("fakeCommanders");
            if (StringUtils.isBlank(fakeString)) {
                setStoredValue("fakeCommanders", leaderID);
            } else {
                Set<String> leaders = new HashSet<>(Arrays.asList(fakeString.split("|")));
                leaders.add(leaderID);
                setStoredValue("fakeCommanders", String.join("|", leaders));
            }
        }
    }

    public boolean playerHasLeaderUnlockedOrAlliance(Player player, String leaderID) {
        if (player.hasLeaderUnlocked(leaderID))
            return true;
        if (!leaderID.contains("commander"))
            return false;

        // check if player has any allainces with players that have the commander
        // unlocked

        if (leaderIsFake(leaderID)) {
            return false;
        }

        for (String pnID : player.getPromissoryNotesInPlayArea()) {
            if (pnID.contains("_an") || "dspnceld".equals(pnID)) { // dspnceld = Celdauri Trade Alliance
                Player pnOwner = getPNOwner(pnID);
                if (pnOwner != null && !pnOwner.equals(player) && pnOwner.hasLeaderUnlocked(leaderID)) {
                    return true;
                }
            }
        }

        // check if player has Imperia and if any of the stolen CCs are owned by players
        // that have the leader unlocked
        if (player.hasAbility("imperia")) {
            for (Player player_ : getRealPlayers()) {
                if (player_.equals(player))
                    continue;
                if (player.getMahactCC().contains(player_.getColor()) && player_.hasLeaderUnlocked(leaderID)) {
                    if (isAllianceMode() && "mahact".equalsIgnoreCase(player.getFaction())) {
                        return leaderID.contains(player_.getFaction());
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public List<Leader> playerUnlockedLeadersOrAlliance(Player player) {
        List<Leader> leaders = new ArrayList<>(player.getLeaders());
        // check if player has any allainces with players that have the commander
        // unlocked
        for (String pnID : player.getPromissoryNotesInPlayArea()) {
            if (pnID.contains("_an") || "dspnceld".equals(pnID)) { // dspnceld = Celdauri Trade Alliance
                Player pnOwner = getPNOwner(pnID);
                if (pnOwner != null && !pnOwner.equals(player)) {

                    for (Leader playerLeader : pnOwner.getLeaders()) {
                        if (leaderIsFake(playerLeader.getId())) {
                            continue;
                        }
                        if (!playerLeader.getId().contains("commander")) {
                            continue;
                        }
                        leaders.add(playerLeader);
                    }
                }
            }
        }

        // check if player has Imperia and if any of the stolen CCs are owned by players
        // that have the leader unlocked
        if (player.hasAbility("imperia")) {
            for (Player otherPlayer : getRealPlayers()) {
                if (otherPlayer.equals(player))
                    continue;
                if (player.getMahactCC().contains(otherPlayer.getColor())) {

                    for (Leader playerLeader : otherPlayer.getLeaders()) {
                        if (leaderIsFake(playerLeader.getId())) {
                            continue;
                        }
                        if (!playerLeader.getId().contains("commander")) {
                            continue;
                        }
                        if (isAllianceMode() && "mahact".equalsIgnoreCase(player.getFaction())) {
                            if (!playerLeader.getId().contains(otherPlayer.getFaction())) {
                                continue;
                            }
                        }
                        leaders.add(playerLeader);
                    }

                }
            }
        }
        leaders = leaders.stream().filter(leader -> leader != null && !leader.isLocked()).collect(Collectors.toList());
        return leaders;
    }

    public void incrementMapImageGenerationCount() {
        mapImageGenerationCount++;
    }

    public boolean hasRunMigration(String string) {
        return runDataMigrations.contains(string);
    }

    public void addMigration(String string) {
        runDataMigrations.add(string);
    }

    public List<String> getRunMigrations() {
        return runDataMigrations;
    }

    @JsonIgnore
    public StrategyCardSetModel getStrategyCardSet() {
        return Mapper.getStrategyCardSets().get(getScSetID());
    }

    @JsonIgnore
    public Optional<StrategyCardModel> getStrategyCardModelByInitiative(int scInitiative) {
        return getStrategyCardSet().getStrategyCardModelByInitiative(scInitiative);
    }

    @JsonIgnore
    public Optional<StrategyCardModel> getStrategyCardModelByName(String name) {
        return getStrategyCardSet().getStrategyCardModelByName(name);
    }

    /**
     * @param scID
     * @return true when the Game's SC Set contains a strategt card which uses a certain automation
     */
    public boolean usesStrategyCardAutomation(String scID) {
        return getStrategyCardSet().getStrategyCardModels().stream()
            .anyMatch(sc -> scID.equals(sc.getBotSCAutomationID()));
    }

    @JsonIgnore
    public int getActionCardDeckSize() {
        return getActionCards().size();
    }

    @JsonIgnore
    public int getActionCardFullDeckSize() {
        DeckModel acDeckModel = Mapper.getDeck(getAcDeckID());
        if (acDeckModel != null)
            return acDeckModel.getCardCount();
        return -1;
    }

    @JsonIgnore
    public int getAgendaDeckSize() {
        return getAgendas().size();
    }

    @JsonIgnore
    public int getAgendaFullDeckSize() {
        DeckModel agendaDeckModel = Mapper.getDeck(getAgendaDeckID());
        if (agendaDeckModel != null)
            return agendaDeckModel.getCardCount();
        return -1;
    }

    @JsonIgnore
    public int getEventDeckSize() {
        return getEvents().size();
    }

    @JsonIgnore
    public int getEventFullDeckSize() {
        DeckModel eventDeckModel = Mapper.getDeck(getEventDeckID());
        if (eventDeckModel != null)
            return eventDeckModel.getCardCount();
        return -1;
    }

    @JsonIgnore
    public int getPublicObjectives1DeckSize() {
        return getPublicObjectives1().size();
    }

    @JsonIgnore
    public int getPublicObjectives1FullDeckSize() {
        DeckModel po1DeckModel = Mapper.getDeck(getStage1PublicDeckID());
        if (po1DeckModel != null)
            return po1DeckModel.getCardCount();
        return -1;
    }

    @JsonIgnore
    public int getPublicObjectives2DeckSize() {
        return getPublicObjectives2().size();
    }

    @JsonIgnore
    public int getPublicObjectives2FullDeckSize() {
        DeckModel po2DeckModel = Mapper.getDeck(getStage2PublicDeckID());
        if (po2DeckModel != null)
            return po2DeckModel.getCardCount();
        return -1;
    }

    @JsonIgnore
    public int getRelicDeckSize() {
        return getAllRelics().size();
    }

    @JsonIgnore
    public int getRelicFullDeckSize() {
        DeckModel relicDeckModel = Mapper.getDeck(getRelicDeckID());
        if (relicDeckModel != null)
            return relicDeckModel.getCardCount();
        return -1;
    }

    @JsonIgnore
    public int getSecretObjectiveDeckSize() {
        return getSecretObjectives().size();
    }

    @JsonIgnore
    public int getSecretObjectiveFullDeckSize() {
        DeckModel soDeckModel = Mapper.getDeck(getSoDeckID());
        if (soDeckModel != null)
            return soDeckModel.getCardCount();
        return -1;
    }

    private int getExploreDeckSize(String exploreDeckID) {
        return getExploreDeck(exploreDeckID).size();
    }

    private int getExploreDeckFullSize(String exploreDeckID) {
        DeckModel exploreDeckModel = Mapper.getDeck(getExplorationDeckID());
        if (exploreDeckModel == null)
            return -1;

        int count = 0;
        for (String exploreCardID : exploreDeckModel.getNewDeck()) {
            ExploreModel exploreCard = Mapper.getExplore(exploreCardID);
            if (exploreCard.getType().equalsIgnoreCase(exploreDeckID)) {
                count++;
            }
        }
        return count;
    }

    @JsonIgnore
    public int getHazardousExploreDeckSize() {
        return getExploreDeckSize(Constants.HAZARDOUS);
    }

    @JsonIgnore
    public int getHazardousExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.HAZARDOUS);
    }

    @JsonIgnore
    public int getCulturalExploreDeckSize() {
        return getExploreDeckSize(Constants.CULTURAL);
    }

    @JsonIgnore
    public int getCulturalExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.CULTURAL);
    }

    @JsonIgnore
    public int getIndustrialExploreDeckSize() {
        return getExploreDeckSize(Constants.INDUSTRIAL);
    }

    @JsonIgnore
    public int getIndustrialExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.INDUSTRIAL);
    }

    @JsonIgnore
    public int getFrontierExploreDeckSize() {
        return getExploreDeckSize(Constants.FRONTIER);
    }

    @JsonIgnore
    public int getFrontierExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.FRONTIER);
    }

    public int getPlayersTurnSCInitiative(Player player) {
        if ((player.hasAbility("telepathic") || player.ownsPromissoryNote("gift"))
            && (player.getPromissoryNotes().containsKey("gift") || !otherPlayerInGameHasGiftInPlayArea(player))) { // Naalu
                                                                                                                                                                                              // with
                                                                                                                                                                                              // gift
                                                                                                                                                                                              // in
                                                                                                                                                                                              // their
                                                                                                                                                                                              // hand
            return 0;
        } else if (player.getPromissoryNotesInPlayArea().contains("gift")) { // Someone with gift in their play area
            return 0;
        }

        return player.getLowestSC();
    }

    private boolean otherPlayerInGameHasGiftInPlayArea(Player player) {
        if (player.ownsPromissoryNote("gift") && player.getPromissoryNotes().containsKey("gift"))
            return false; // can't be in another play area if it's your card and in your hand
        for (Player otherPlayer : getRealPlayers()) {
            if (player.equals(otherPlayer))
                continue; // don't check yourself
            if (otherPlayer.getPromissoryNotesInPlayArea().contains("gift"))
                return true;
        }
        return false;
    }

    @JsonIgnore
    public List<String> getAllPlanetsWithSleeperTokens() {
        List<String> planetsWithSleepers = new ArrayList<>();
        for (Tile tile : getTileMap().values()) {
            planetsWithSleepers.addAll(tile.getPlanetsWithSleeperTokens());
        }
        return planetsWithSleepers;
    }

    @JsonIgnore
    public int getSleeperTokensPlacedCount() {
        return getAllPlanetsWithSleeperTokens().size();
    }

    public Optional<Player> getPlayerByColorID(String color) {
        return getRealPlayersNDummies().stream()
            .filter(otherPlayer -> Mapper.getColorID(otherPlayer.getColor()).equals(color))
            .findFirst();
    }

    public boolean isLeaderInGame(String leaderID) {
        for (Player player : getRealPlayers()) {
            if (player.getLeaderIDs().contains(leaderID))
                return true;
        }
        return false;
    }

    @Nullable
    public Tile getTileFromPlanet(String planetName) {
        for (Tile tile_ : getTileMap().values()) {
            for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    return tile_;
                }
            }
        }
        return null;
    }

    @Nullable
    public Player getPlayerFromColorOrFaction(String factionOrColor) {
        Player player = null;
        if (factionOrColor != null) {
            String factionColor = AliasHandler.resolveColor(factionOrColor.toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); // TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : getPlayers().values()) {
                if ("keleres".equalsIgnoreCase(factionColor)) {
                    if (Objects.equals(factionColor + "a", player_.getFaction())) {
                        player = player_;
                        break;
                    }
                    if (Objects.equals(factionColor + "x", player_.getFaction())) {
                        player = player_;
                        break;
                    }
                    if (Objects.equals(factionColor + "m", player_.getFaction())) {
                        player = player_;
                        break;
                    }

                }
                if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor()) ||
                    Objects.equals(factionColor, player_.getColorID())) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    @Nullable
    public Player getPlayerFromLeader(String leader) {
        Player player = null;
        if (leader != null) {
            for (Player player_ : getPlayers().values()) {
                if (player_.getLeaderIDs().contains(leader)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    @Deprecated
    public UnitModel getUnitFromImageName(String imageName) {
        String colorID = StringUtils.substringBefore(imageName, "_");
        Player player = getPlayerFromColorOrFaction(colorID);
        if (player == null)
            return null;
        return player.getUnitFromImageName(imageName);
    }

    public UnitModel getUnitFromUnitKey(UnitKey unitKey) {
        Player player = getPlayerFromColorOrFaction(unitKey.getColorID());
        if (player == null)
            return null;
        return player.getUnitFromUnitKey(unitKey);
    }

    public void swapInVariantUnits(String source) {
        List<UnitModel> variantUnits = Mapper.getUnits().values().stream()
            .filter(unit -> source.equals(unit.getSource().toString())).toList();
        for (Player player : getPlayers().values()) {
            List<UnitModel> playersUnits = player.getUnitModels().stream()
                .filter(unit -> !source.equals(unit.getSource().toString())).toList();
            for (UnitModel playerUnit : playersUnits) {
                for (UnitModel variantUnit : variantUnits) {
                    if ((variantUnit.getHomebrewReplacesID().isPresent()
                        && variantUnit.getHomebrewReplacesID().get().equals(playerUnit.getId())) // true variant
                        // unit replacing a
                        // PoK unit
                        || (playerUnit.getHomebrewReplacesID().isPresent()
                            && playerUnit.getHomebrewReplacesID().get().equals(variantUnit.getId())) // PoK
                                                                                                                                                        // "variant"
                                                                                                                                                        // replacing
                                                                                                                                                        // a true
                                                                                                                                                        // variant
                                                                                                                                                        // unit
                    ) {
                        player.removeOwnedUnitByID(playerUnit.getId());
                        player.addOwnedUnitByID(variantUnit.getId());
                        break;
                    }
                }
            }
        }
    }

    @JsonIgnore
    public void swapInVariantTechs() {
        DeckModel deckModel = Mapper.getDeck(getTechnologyDeckID());
        if (deckModel == null)
            return;
        List<TechnologyModel> techsToReplace = deckModel.getNewDeck().stream().map(Mapper::getTech)
            .filter(Objects::nonNull).filter(t -> t.getHomebrewReplacesID().isPresent()).toList();
        for (Player player : getPlayers().values()) {
            List<String> newExhaustedTechs = new ArrayList<>(player.getExhaustedTechs());

            for (TechnologyModel tech : techsToReplace) {
                String replacedTechID = tech.getHomebrewReplacesID().get();
                String replacingTechID = tech.getAlias();
                if (player.hasTech(tech.getHomebrewReplacesID().get())) {
                    if (!player.hasTechReady(replacedTechID)) {
                        player.refreshTech(replacedTechID);
                        newExhaustedTechs.add(replacingTechID);
                    }
                    player.removeTech(replacedTechID);
                    player.addTech(replacingTechID);
                }
                if (player.getFactionTechs().contains(replacedTechID)) {
                    player.removeFactionTech(replacedTechID);
                    player.addFactionTech(replacingTechID);
                }
            }
            player.setExhaustedTechs(newExhaustedTechs);
        }
    }

    @JsonIgnore
    public void swapOutVariantTechs() {
        DeckModel deckModel = Mapper.getDeck(getTechnologyDeckID());
        if (deckModel == null)
            return;
        List<TechnologyModel> techsToReplace = Mapper.getTechs().values().stream()
            .filter(t -> t.getHomebrewReplacesID().isPresent()).toList();
        for (Player player : getPlayers().values()) {
            List<String> newExhaustedTechs = new ArrayList<>(player.getExhaustedTechs());

            for (TechnologyModel tech : techsToReplace) {
                String replacedTechID = tech.getAlias();
                String replacingTechID = tech.getHomebrewReplacesID().get();
                if (player.hasTech(replacedTechID)) {
                    if (!player.hasTechReady(replacedTechID)) {
                        player.refreshTech(replacedTechID);
                        newExhaustedTechs.add(replacingTechID);
                    }
                    player.removeTech(replacedTechID);
                    player.addTech(replacingTechID);
                }
                if (player.getFactionTechs().contains(replacedTechID)) {
                    player.removeFactionTech(replacedTechID);
                    player.addFactionTech(replacingTechID);
                }
            }
            player.setExhaustedTechs(newExhaustedTechs);
        }
    }

    public String getSCNumberIfNaaluInPlay(Player player, String scText) {
        if (player.hasAbility("telepathic")) { // naalu 0 token ability
            boolean giftPlayed = false;
            Collection<Player> activePlayers = getPlayers().values().stream().filter(Player::isRealPlayer).toList();
            for (Player player_ : activePlayers) {
                if (player != player_ && player_.getPromissoryNotesInPlayArea().contains(Constants.NAALU_PN)) {
                    giftPlayed = true;
                    break;
                }
            }
            if (!giftPlayed) {
                scText = "0/" + scText;
            }
        } else if (player.getPromissoryNotesInPlayArea().contains(Constants.NAALU_PN)) {
            scText = "0/" + scText;
        }

        return scText;
    }

    @JsonIgnore
    public boolean isLittleOmega() {
        return stage1PublicDeckID.contains("little_omega") || stage2PublicDeckID.contains("little_omega")
            || agendaDeckID.contains("little_omega");
    }

    @JsonIgnore
    public boolean hasHomebrew() {
        // needs to check for homebrew tiles still
        // Decks

        List<String> deckIDs = new ArrayList<>();
        deckIDs.add(acDeckID);
        deckIDs.add(soDeckID);
        deckIDs.add(stage1PublicDeckID);
        deckIDs.add(stage2PublicDeckID);
        deckIDs.add(relicDeckID);
        deckIDs.add(agendaDeckID);
        deckIDs.add(explorationDeckID);
        deckIDs.add(technologyDeckID);
        deckIDs.add(eventDeckID);
        boolean allDecksOfficial = deckIDs.stream().allMatch(id -> {
            DeckModel deck = Mapper.getDeck(id);
            if ("null".equals(id)) return true;
            if (deck == null) return true;
            return deck.getSource().isOfficial();
        });
        StrategyCardSetModel scset = Mapper.getStrategyCardSets().get(scSetID);
        if (scset == null || !scset.getSource().isOfficial()) {
            allDecksOfficial = false;

        }

        // Tiles
        boolean allTilesOfficial = getTileMap().values().stream().allMatch(tile -> {
            if (tile == null || tile.getTileModel() == null) {
                return true;
            }
            ComponentSource tileSource = tile.getTileModel().getSource();
            if (tile.getTileModel().getImagePath().endsWith("_Hyperlane.png")) {
                return true; //official hyperlane
            }
            return tileSource != null && tileSource.isOfficial();
        });

        if (!allDecksOfficial) {
            System.out.println("here4");
        }

        return isExtraSecretMode()
            || isHomeBrew()
            || isFoWMode()
            || isLightFogMode()
            || isRedTapeMode()
            || isDiscordantStarsMode()
            || isFrankenGame()
            || isMiltyModMode()
            || isAbsolMode()
            || isAllianceMode()
            || isSpinMode()
            || isHomeBrewSCMode()
            || isCommunityMode()
            || !allDecksOfficial
            || !allTilesOfficial
            || Mapper.getFactions().stream()
                .filter(faction -> !faction.getSource().isPok())
                .anyMatch(faction -> getFactions().contains(faction.getAlias()))
            || Mapper.getLeaders().values().stream()
                .filter(leader -> !leader.getSource().isPok())
                .anyMatch(leader -> isLeaderInGame(leader.getID()))
            || publicObjectives1.size() < 5 && round >= 4
            || publicObjectives2.size() < (round - 4)
            || getRealPlayers().stream()
                .anyMatch(player -> player.getSecretVictoryPoints() > 3
                    && !player.getRelics().contains("obsidian"))
            || playerCountForMap < 3
            || getRealAndEliminatedAndDummyPlayers().size() < 3
            || playerCountForMap > 8
            || getRealAndEliminatedAndDummyPlayers().size() > 8;
    }

    public void setStrategyCardSet(String scSetID) {
        StrategyCardSetModel strategyCardModel = Mapper.getStrategyCardSets().get(scSetID);
        setHomeBrewSCMode(!"pok".equals(scSetID) && !"base_game".equals(scSetID));

        Map<Integer, Integer> oldTGs = getScTradeGoods();
        setScTradeGoods(new LinkedHashMap<>());
        setScSetID(strategyCardModel.getAlias());
        strategyCardModel.getStrategyCardModels().forEach(scModel -> {
            setScTradeGood(scModel.getInitiative(), oldTGs.getOrDefault(scModel.getInitiative(), 0));
        });
    }

    @JsonIgnore
    public List<ColorModel> getUnusedColors() {
        return Mapper.getColors().stream()
            .filter(color -> getPlayers().values().stream().noneMatch(player -> player.getColor().equals(color.getName())))
            .toList();
    }
}
