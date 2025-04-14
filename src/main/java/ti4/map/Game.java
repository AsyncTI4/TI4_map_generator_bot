package ti4.map;

import java.awt.*;
import java.lang.reflect.Field;
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
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.planet.PlanetRemove;
import ti4.draft.BagDraft;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.settingsFramework.menus.DeckSettings;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.json.ObjectMapperFactory;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.model.ColorModel;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.option.FOWOptionService.FOWOption;

import static java.util.function.Predicate.not;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class Game extends GameProperties {

    // TODO (Jazz): Sort through these and add to GameProperties
    private Map<String, Tile> tileMap = new HashMap<>(); // Position, Tile
    private Map<String, Player> players = new LinkedHashMap<>();

    private final @JsonIgnore Map<String, Planet> planets = new HashMap<>();
    private final Map<FOWOption, Boolean> fowOptions = new HashMap<>();
    private final Map<Integer, Boolean> scPlayed = new HashMap<>();
    private final Map<String, String> checkingForAllReacts = new HashMap<>();
    private List<String> listOfTilePinged = new ArrayList<>();

    // TODO (Jazz): These should be easily added to GameProperties
    private Map<String, Integer> discardActionCards = new LinkedHashMap<>();
    private Map<String, Integer> purgedActionCards = new LinkedHashMap<>();
    private Map<String, Integer> displacedUnitsFrom1System = new HashMap<>();
    private Map<String, Integer> thalnosUnits = new HashMap<>();
    private Map<String, Integer> slashCommandsUsed = new HashMap<>();
    private Map<String, Integer> actionCardsSabotaged = new HashMap<>();
    private Map<String, Integer> displacedUnitsFromEntireTacticalAction = new HashMap<>();
    private Map<String, String> currentAgendaVotes = new HashMap<>();

    @Setter
    @Getter
    private DisplayType displayTypeForced;
    private @Getter @Setter List<BorderAnomalyHolder> borderAnomalies = new ArrayList<>();
    private Date lastActivePlayerChange = new Date(0);
    @JsonProperty("autoPingStatus")
    private boolean autoPingEnabled;

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
    private final Set<String> runDataMigrations = new HashSet<>();
    private BagDraft activeDraft;
    @JsonIgnore
    @Getter
    @Setter
    private Map<String, Integer> tileDistances = new HashMap<>();
    private MiltyDraftManager miltyDraftManager;
    @Setter
    @Getter
    private String miltyDraftString;
    @Setter
    private MiltySettings miltySettings;
    @Getter
    @Setter
    private String miltyJson;
    @Getter
    @Setter
    private TIGLRank minimumTIGLRankAtGameStart;

    public Game() {
        setCreationDate(Helper.getDateRepresentation(System.currentTimeMillis()));
        setLastModifiedDate(System.currentTimeMillis());
    }

    public void newGameSetup() {
        // Normal Decks
        setPublicObjectives1(Mapper.getShuffledDeck("public_stage_1_objectives_pok"));
        setPublicObjectives2(Mapper.getShuffledDeck("public_stage_2_objectives_pok"));
        setSecretObjectives(Mapper.getShuffledDeck("secret_objectives_pok"));
        setActionCards(Mapper.getShuffledDeck("action_cards_pok"));
        setAgendas(Mapper.getShuffledDeck("agendas_pok"));
        setExploreDeck(Mapper.getShuffledDeck("explores_pok"));
        setRelics(Mapper.getShuffledDeck("relics_pok"));
        setStrategyCardSet("pok");

        // OTHER
        setEvents(new ArrayList<>()); // ignis_aurora
        addCustomPO(Constants.CUSTODIAN, 1);
        setUpPeakableObjectives(5, 1);
        setUpPeakableObjectives(5, 2);
    }

    public void fixScrewedSOs() {
        MessageHelper.sendMessageToChannel(getActionsChannel(),
            "The number of SOs in the deck before this operation is " + getNumberOfSOsInTheDeck()
                + ". The number in players hands is " + getNumberOfSOsInPlayersHands());

        List<String> defaultSecrets = Mapper.getDecks().get("secret_objectives_pok").getNewShuffledDeck();
        List<String> currentSecrets = new ArrayList<>(getSecretObjectives());
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

                getSecretObjectives().add(defaultSO);
            }
        }
        MessageHelper.sendMessageToChannel(getActionsChannel(),
            "Fixed the SOs, the total amount of SOs in deck is " + getNumberOfSOsInTheDeck()
                + ". The number in players hands is " + getNumberOfSOsInPlayersHands());
    }

    @JsonIgnore
    public Player setupNeutralPlayer(String color) {
        Player neutral = players.get(Constants.dicecordId);
        if (neutral != null) {
            ColorChangeHelper.changePlayerColor(this, neutral, neutral.getColor(), color);
            return players.get(Constants.dicecordId);
        }
        addPlayer(Constants.dicecordId, "Dicecord"); //Dicecord
        neutral = getPlayer(Constants.dicecordId);
        neutral.setColor(color);
        neutral.setFaction("neutral");
        neutral.setDummy(true);
        FactionModel setupInfo = neutral.getFactionSetupInfo();
        Set<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        neutral.setUnitsOwned(playerOwnedUnits);
        return neutral;
    }

    public int getNumberOfSOsInTheDeck() {
        return getSecretObjectives().size();
    }

    public String getEndedDateString() {
        return Helper.getDateRepresentation(getEndedDate());
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

            soNum += player.getSo();
            soNum += player.getSoScored();

        }
        return soNum;
    }

    public Map<String, Object> getExportableFieldMap() {
        Class<GameProperties> aClass = GameProperties.class;
        Field[] fields = aClass.getDeclaredFields();
        HashMap<String, Object> returnValue = new HashMap<>();

        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getDeclaredAnnotation(ExportableField.class) != null) {
                try {
                    returnValue.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    // This shouldn't really happen since we can even see private fields.
                    BotLogger.log("Unknown error exporting fields from Game.", e);
                }
            }
        }
        return returnValue;
    }

    @JsonIgnore
    public MiltyDraftManager getMiltyDraftManagerUnsafe() {
        return miltyDraftManager;
    }

    @NotNull
    @JsonIgnore
    public MiltyDraftManager getMiltyDraftManager() {
        if (miltyDraftManager == null) {
            miltyDraftManager = new MiltyDraftManager();
            if (StringUtils.isNotBlank(miltyDraftString)) {
                try {
                    miltyDraftManager.loadSuperSaveString(miltyDraftString);
                } catch (Exception e) {
                    miltyDraftManager = new MiltyDraftManager();
                }
            }
        }
        return miltyDraftManager;
    }

    public void setMiltyDraftManager(MiltyDraftManager miltyDraftManager) {
        this.miltyDraftManager = miltyDraftManager;
    }

    @Nullable
    @JsonProperty("miltySettings")
    public MiltySettings getMiltySettingsUnsafe() {
        return miltySettings;
    }

    public MiltySettings initializeMiltySettings() {
        if (miltySettings == null) {
            if (miltyJson != null) {
                try {
                    JsonNode json = ObjectMapperFactory.build().readTree(miltyJson);
                    miltySettings = new MiltySettings(this, json);
                } catch (Exception e) {
                    BotLogger.log("Failed loading milty draft settings for `" + getName() + "` " + Constants.jazzPing(), e);
                    MessageHelper.sendMessageToChannel(getActionsChannel(), "Milty draft settings failed to load. Resetting to default.");
                    miltySettings = new MiltySettings(this, null);
                }
            } else {
                miltySettings = new MiltySettings(this, null);
            }
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
        getActionCards().addAll(acIDs);
        Collections.shuffle(getActionCards());
    }

    public void addSecretDuplicates(List<String> soIDs) {
        getSecretObjectives().addAll(soIDs);
        Collections.shuffle(getSecretObjectives());
    }

    public void setPurgedPNs(List<String> purgedPN) {
        this.purgedPN = purgedPN;
    }

    public List<String> getPurgedPN() {
        return purgedPN;
    }

    @JsonIgnore
    public boolean hasWinner() {
        return getWinner().isPresent();
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

    @JsonIgnore
    public List<Player> getWinners() {
        List<Player> winners = new ArrayList<>();
        Player winner = getWinner().orElse(null);
        if (winner != null) {
            winners.add(winner);
            if (winner.getAllianceMembers() != null) {
                for (Player player : getRealPlayers()) {
                    if (player.getAllianceMembers() != null && player.getAllianceMembers().contains(winner.getFaction())) {
                        winners.add(player);
                    }
                }
            }
        }
        return winners;
    }

    private static Player getLowestInitiativePlayer(Player player1, Player player2) {
        if (Collections.min(player1.getSCs()) < Collections.min(player2.getSCs())) {
            return player1;
        }
        return player2;
    }

    public void increaseButtonPressCount() {
        setButtonPressCount(getButtonPressCount() + 1);
    }

    public int getSlashCommandsRunCount() {
        return getAllSlashCommandsUsed().values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isACInDiscard(String name) {
        return discardActionCards.keySet().stream()
            .map(Mapper::getActionCard)
            .anyMatch(ac -> ac.getName() != null &&
                ac.getName().toLowerCase().contains(name.toLowerCase()));
    }

    public List<String> getListOfTilesPinged() {
        return listOfTilePinged;
    }

    public void resetListOfTilesPinged() {
        listOfTilePinged = new ArrayList<>();
    }

    public void setListOfTilesPinged(List<String> listOfTile) {
        listOfTilePinged = listOfTile;
    }

    public void setTileAsPinged(String tileName) {
        listOfTilePinged.add(tileName);
    }

    @Override
    public void setRound(int round) {
        super.setRound(Math.max(1, round));
    }

    @Override
    public void setCompetitiveTIGLGame(boolean competitiveTIGLGame) {
        if (isAbsolMode() || isMiltyModMode() || isDiscordantStarsMode() || isHomebrewSCMode() || isFowMode() || isAllianceMode() || isCommunityMode())
            competitiveTIGLGame = false;
        super.setCompetitiveTIGLGame(competitiveTIGLGame);
    }

    @Override
    public boolean isAllianceMode() {
        for (Player player : getRealPlayers()) {
            if (player.getAllianceMembers() != null && !player.getAllianceMembers().replace(player.getFaction(), "").isEmpty()) {
                super.setAllianceMode(true);
            }
        }
        return super.isAllianceMode();
    }

    @Override
    public void setOutputVerbosity(String outputVerbosity) {
        if (Constants.VERBOSITY_OPTIONS.contains(outputVerbosity)) {
            super.setOutputVerbosity(outputVerbosity);
        }
    }

    @Override
    public String getActiveSystem() {
        if (super.getActiveSystem() == null || super.getActiveSystem().isEmpty())
            return getStoredValue("lastActiveSystem");
        return super.getActiveSystem();
    }

    public String getCurrentActiveSystem() {
        return super.getActiveSystem();
    }

    public Map<FOWOption, Boolean> getFowOptions() {
        return fowOptions;
    }

    public boolean getFowOption(FOWOption option) {
        return fowOptions.getOrDefault(option, false);
    }

    public void setFowOption(FOWOption option, boolean value) {
        fowOptions.put(option, value);
    }

    public boolean hideUserNames() {
        return getFowOption(FOWOption.HIDE_PLAYER_NAMES);
    }

    @JsonIgnore
    public String getGameModesText() {
        boolean isNormalGame = isNormalGame();
        Map<String, Boolean> gameModes = new HashMap<>();
        gameModes.put(SourceEmojis.TI4PoK + "Normal", isNormalGame);
        gameModes.put(SourceEmojis.TI4BaseGame + "Base Game", isBaseGameMode());
        gameModes.put(SourceEmojis.MiltyMod + "MiltyMod", isMiltyModMode());
        gameModes.put(MiscEmojis.TIGL + "TIGL", isCompetitiveTIGLGame());
        gameModes.put("Community", isCommunityMode());
        gameModes.put("Minor Factions", isMinorFactionsMode());
        gameModes.put("Age of Exploration", isAgeOfExplorationMode());
        gameModes.put("Alliance", isAllianceMode());
        gameModes.put("FoW", isFowMode());
        gameModes.put("Franken", isFrankenGame());
        gameModes.put(SourceEmojis.Absol + "Absol", isAbsolMode());
        gameModes.put("VotC", isVotcMode());
        gameModes.put(SourceEmojis.DiscordantStars + "DiscordantStars", isDiscordantStarsMode());
        gameModes.put("HomebrewSC", isHomebrewSCMode());
        gameModes.put("Little Omega", isLittleOmega());
        gameModes.put("AC Deck 2", "action_deck_2".equals(getAcDeckID()));
        gameModes.put("Homebrew", !isNormalGame);

        for (String tag : getTags()) {
            gameModes.put(tag, true);
        }
        return gameModes.entrySet().stream().filter(Entry::getValue).map(Entry::getKey)
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
                .filter(not(c -> c.getName().contains(Constants.ACTIONS_CHANNEL_SUFFIX)))
                .toList();
            if (gameChannels.size() == 1) {
                tableTalkChannel = gameChannels.getFirst();
                setTableTalkChannelID(tableTalkChannel.getId());
                return tableTalkChannel;
            }
            // BotLogger.log("Could not retrieve TableTalkChannel for " + getName(), e);
        }
        return null;
    }

    @JsonIgnore
    public TextChannel getMainGameChannel() {
        try {
            return AsyncTI4DiscordBot.jda.getTextChannelById(getMainChannelID());
        } catch (Exception e) {
            List<TextChannel> gameChannels = AsyncTI4DiscordBot.jda
                .getTextChannelsByName(getName() + Constants.ACTIONS_CHANNEL_SUFFIX, true);
            if (gameChannels.size() == 1) {
                TextChannel mainGameChannel = gameChannels.getFirst();
                setMainChannelID(mainGameChannel.getId());
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

    @JsonIgnore
    public TextChannel getActionsChannel() {
        return getMainGameChannel();
    }

    @JsonIgnore
    public ThreadChannel getBotMapUpdatesThread() {
        if (isFowMode()) {
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
            return botChannels.getFirst();
        } else if (botChannels.size() > 1) {
            BotLogger.log(getName() + " appears to have more than one bot-map-updates channel:\n" + botChannels.stream().map(ThreadChannel::getJumpUrl).collect(Collectors.joining("\n")));
            return botChannels.getFirst();
        }

        // CHECK IF ARCHIVED
        if (getActionsChannel() == null) {
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

    public ThreadChannel getLaunchPostThread() {
        if (StringUtils.isNumeric(getLaunchPostThreadID())) {
            return AsyncTI4DiscordBot.guildPrimary.getThreadChannelById(getLaunchPostThreadID());
        }
        return null;
    }

    /**
     * @return Guild that the ActionsChannel or MainGameChannel resides
     */
    @Nullable
    @JsonIgnore
    public Guild getGuild() {
        return getActionsChannel() == null ? null : getActionsChannel().getGuild();
    }

    @Nullable
    @JsonIgnore
    public String getGuildId() {
        if (getGuild() == null) {
            return null;
        }
        setGuildID(getGuild().getId());
        return getGuild().getId();
    }

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
        }
        return "";
    }

    public void clearAllEmptyStoredValues() {
        // Remove the entry if the value is empty
        checkingForAllReacts.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    }

    public void setStoredValue(String key, String value) {
        value = StringHelper.escape(value);
        checkingForAllReacts.put(key, value);
    }

    public String getStoredValue(String key) {
        String value = getFactionsThatReactedToThis(key);
        return StringHelper.unescape(value);
    }

    public void removeStoredValue(String key) {
        checkingForAllReacts.remove(key);
    }

    public void resetCurrentAgendaVotes() {
        currentAgendaVotes = new HashMap<>();
    }

    @JsonIgnore
    public Set<Integer> getPlayedSCs() {
        return getScPlayed().entrySet().stream().filter(Entry::getValue).map(Entry::getKey)
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
            if (holder != null && !scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
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
            if (holder != null && !scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
                judger = 0;
            }
            if (judger == 0) {
                orderedSCs.add(sc);
            }
        }
        for (int sc : orderedSCsBasic) {
            Player holder = getPlayerFromSC(sc);
            String scT = sc + "";
            int judger = sc;
            if (holder != null && !scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
                judger = 0;
            }
            if (judger < playerSC && judger != 0) {
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

    public int getActionPhaseTurnOrder(String userId) {
        return getActionPhaseTurnOrder().stream()
            .map(Player::getUserID)
            .toList()
            .indexOf(userId);
    }

    public List<Player> getActionPhaseTurnOrder() {
        return new ArrayList<>(players.values().stream()
            .filter(player -> !player.getSCs().isEmpty())
            .sorted(Player.comparingInitiative())
            .toList());
    }

    public int getRingCount() {
        if (getTileMap().isEmpty()) {
            return 0;
        }
        String highestPosition = getTileMap().keySet().stream()
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

    public void setSCPlayed(Integer scNumber, Boolean playedStatus) {
        scPlayed.put(scNumber, playedStatus);
    }

    public void setCurrentAgendaVote(String outcome, String voteInfo) {
        currentAgendaVotes.put(outcome, voteInfo);
    }

    public void removeOutcomeAgendaVote(String outcome) {
        currentAgendaVotes.remove(outcome);
    }

    public void addPlayersWhoHitPersistentNoAfter(String faction) {
        String existing = getPlayersWhoHitPersistentNoAfter();
        if (existing != null && !existing.isEmpty()) existing += "_";
        setPlayersWhoHitPersistentNoAfter(existing + faction);
    }

    public void addPlayersWhoHitPersistentNoWhen(String faction) {
        String existing = getPlayersWhoHitPersistentNoWhen();
        if (existing != null && !existing.isEmpty()) existing += "_";
        setPlayersWhoHitPersistentNoWhen(existing + faction);
    }

    public void removePlayersWhoHitPersistentNoAfter(String faction) {
        String existing = getPlayersWhoHitPersistentNoAfter();
        if (existing != null && !existing.isEmpty()) {
            if (existing.contains(faction + "_")) {
                faction += "_";
            }
            existing = existing.replace(faction, "");
        }
        setPlayersWhoHitPersistentNoAfter(existing);
    }

    public void removePlayersWhoHitPersistentNoWhen(String faction) {
        String existing = getPlayersWhoHitPersistentNoWhen();
        if (existing != null && !existing.isEmpty()) {
            if (existing.contains(faction + "_")) {
                faction += "_";
            }
            existing = existing.replace(faction, "");
        }
        setPlayersWhoHitPersistentNoWhen(existing);
    }

    @JsonIgnore
    public Player getActivePlayer() {
        return getPlayer(getActivePlayerID());
    }

    @JsonIgnore
    public Player getSpeaker() {
        return getPlayer(getSpeakerUserID());
    }

    public void setSpeaker(Player speaker) {
        setSpeakerUserID(speaker.getUserID());
    }

    public Map<String, Integer> getCurrentMovedUnitsFrom1System() {
        return displacedUnitsFrom1System;
    }

    public Map<String, Integer> getThalnosUnits() {
        return thalnosUnits;
    }

    public int getSpecificThalnosUnit(String unit) {
        return thalnosUnits.getOrDefault(unit, 0);
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
        String factionsInCombat = getStoredValue("factionsInCombat");
        Player prevPlayer = getActivePlayer();
        String prevFaction = (prevPlayer != null && prevPlayer.getFaction() != null) ? prevPlayer.getFaction() : "jazzwuzhere&p1too";
        long elapsedTime = newTime.getTime() - lastActivePlayerChange.getTime();
        if (prevPlayer != null && !factionsInCombat.contains(prevFaction) && !isTemporaryPingDisable()) {
            prevPlayer.updateTurnStats(elapsedTime);
        } else {
            if (prevPlayer != null) {
                prevPlayer.updateTurnStatsWithAverage(elapsedTime);
            }
        }
        setStoredValue("factionsInCombat", "");
        setTemporaryPingDisable(false);
        // reset timers for ping and stats
        setActivePlayerID(player == null ? null : player.getUserID());
        setLastActivePlayerChange(newTime);
        AutoPingMetadataManager.setupAutoPing(getName());
    }

    public void setAutoPing(boolean status) {
        autoPingEnabled = status;
    }

    public boolean getAutoPingStatus() {
        return autoPingEnabled;
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

    /**
     * @return Map of (scInitiativeNum, tradeGoodCount)
     */
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
                ButtonHelperAgents.resolveArtunoCheck(player, tradeGoodCount);
                tradeGoodCount = 0;
                MessageHelper.sendMessageToChannel(getActionsChannel(), "The " + tradeGoodCount + " trade good" + (tradeGoodCount == 1 ? "" : "s")
                    + " that would be placed on **" + Helper.getSCName(sc, this) + "** have instead been given to the Kyro "
                    + (isFrankenGame() ? "hero " : "") + "player, as per the text on Speygh, the Kyro Hero.");
            }
        }
        scTradeGoods.put(sc, tradeGoodCount);
    }

    public void incrementScTradeGoods() {
        Set<Integer> scPickedList = new HashSet<>();
        for (Player player_ : getRealPlayers()) {
            scPickedList.addAll(player_.getSCs());
            if (!player_.getSCs().isEmpty()) {
                StringBuilder scs = new StringBuilder();
                for (int SC : player_.getSCs()) {
                    scs.append(SC).append("_");
                }
                scs = new StringBuilder(scs.substring(0, scs.length() - 1));
                setStoredValue("Round" + getRound() + "SCPickFor" + player_.getFaction(), scs.toString());
            }
        }

        //ADD A TG TO UNPICKED SC
        if (!islandMode()) {
            for (Integer scNumber : scTradeGoods.keySet()) {
                if (!scPickedList.contains(scNumber) && scNumber != 0) {
                    Integer tgCount = scTradeGoods.get(scNumber);
                    tgCount = tgCount == null ? 1 : tgCount + 1;
                    setScTradeGood(scNumber, tgCount);
                }
            }
        }
    }

    public boolean addSC(Integer sc) {
        if (!scTradeGoods.containsKey(sc)) {
            setScTradeGood(sc, 0);
            return true;
        }
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
        if (publicObjectives1Peakable.isEmpty() || getPhaseOfGame().contains("agenda")) {
            return revealObjective(publicObjectives1);
        } else {
            return revealObjective(publicObjectives1Peakable);
        }
    }

    public Map.Entry<String, Integer> revealStage2() {
        if (publicObjectives2Peakable.isEmpty() || getPhaseOfGame().contains("agenda")) {
            return revealObjective(publicObjectives2);
        } else {
            return revealObjective(publicObjectives2Peakable);
        }
    }

    public void setUpPeakableObjectives(int num, int type) {
        if (type == 1) {
            while (publicObjectives1Peakable.size() != num) {
                if (publicObjectives1Peakable.size() > num) {
                    String id = publicObjectives1Peakable.removeLast();
                    publicObjectives1.add(id);
                    Collections.shuffle(publicObjectives1);
                } else {
                    Collections.shuffle(publicObjectives1);
                    String id = publicObjectives1.getFirst();
                    publicObjectives1.remove(id);
                    publicObjectives1Peakable.add(id);
                }
            }
        } else {
            while (publicObjectives2Peakable.size() != num) {
                if (publicObjectives2Peakable.size() > num) {
                    String id = publicObjectives2Peakable.removeLast();
                    publicObjectives2.add(id);
                    Collections.shuffle(publicObjectives2);
                } else {
                    Collections.shuffle(publicObjectives2);
                    String id = publicObjectives2.getFirst();
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
                    String id = publicObjectives1.getFirst();
                    publicObjectives1.remove(id);
                    publicObjectives1Peakable.add(id);
                }
                if (!publicObjectives2.isEmpty()) {
                    Collections.shuffle(publicObjectives2);
                    String id = publicObjectives2.getFirst();
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

    public Entry<String, Integer> revealSpecificStage1(String id) {
        return revealSpecificObjective(publicObjectives1, id);
    }

    public Entry<String, Integer> revealSpecificStage2(String id) {
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
            place1 -= 1;
            place2 -= 1;
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
            place -= 1;
            return objectiveList.get(place);
        }
        return null;
    }

    public String getTopObjective(int stage1Or2) {
        if (stage1Or2 == 1) {
            String id = publicObjectives1.getFirst();
            publicObjectives1.remove(id);
            return id;
        } else {
            String id = publicObjectives2.getFirst();
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

    public Entry<String, Integer> revealObjective(List<String> objectiveList) {
        if (!objectiveList.isEmpty()) {
            String id = objectiveList.getFirst();
            objectiveList.remove(id);
            int counter = 20;
            while (revealedPublicObjectives.containsKey(id) && objectiveList.size() > 1 && counter > 0) {
                id = objectiveList.getFirst();
                objectiveList.remove(id);
                counter -= 1;
            }
            addRevealedPublicObjective(id);
            for (Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public Entry<String, Integer> revealSpecificObjective(List<String> objectiveList, String id) {
        if (objectiveList.contains(id)) {
            objectiveList.remove(id);
            addRevealedPublicObjective(id);
            for (Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public Entry<String, Integer> addSpecificStage1(String objective) {
        return addSpecificObjective(publicObjectives1, objective);
    }

    public Entry<String, Integer> addSpecificStage2(String objective) {
        return addSpecificObjective(publicObjectives2, objective);
    }

    public Entry<String, Integer> addSpecificObjective(List<String> objectiveList, String objective) {
        if (!objectiveList.isEmpty()) {
            objectiveList.remove(objective);
            addRevealedPublicObjective(objective);
            for (Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
                if (entry.getKey().equals(objective)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public boolean shuffleObjectiveBackIntoDeck(Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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

    public String getCustodiansTaker() {
        if (!isCustodiansScored()) {
            return null;
        }
        String idC = "";
        for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(0)) {
                idC = po.getKey();
                break;
            }
        }
        if (!idC.isEmpty()) {
            List<String> scoredPlayerList = scoredPublicObjectives.computeIfAbsent(idC, key -> new ArrayList<>());
            if (!scoredPlayerList.isEmpty()) {
                String playerID = scoredPlayerList.getFirst();
                for (Player player : getRealAndEliminatedPlayers()) {
                    if (player.getUserID().equalsIgnoreCase(playerID)) {
                        return player.getFaction();
                    }
                }
            }
        }

        return null;
    }

    public boolean isCustodiansScored() {
        boolean custodiansTaken = false;
        String idC = "";
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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

        for (Player p : getRealPlayers()) {
            if (p.controlsMecatol(false)) {
                return true;
            }
        }
        return custodiansTaken;
    }

    public boolean scorePublicObjective(String userID, Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
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
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }
        return removeCustomPO(id);
    }

    public boolean removeCustomPO(String id) {
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
        return getSecretObjectives().add(id);
    }

    public boolean removeSOFromGame(String id) {
        return getSecretObjectives().remove(id);
    }

    public boolean removePOFromGame(String id) {
        if (publicObjectives1.remove(id))
            return true;
        if (publicObjectives2.remove(id))
            return true;
        return revealedPublicObjectives.remove(id) != null;
    }

    public boolean removeACFromGame(String id) {
        return getActionCards().remove(id);
    }

    public boolean removeAgendaFromGame(String id) {
        return getAgendas().remove(id);
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

    /**
     * @param soToPoList - a list of Secret Objective IDs that have been turned into Public Objectives (typically via Classified Document Leaks)
     */
    public void setSoToPoList(List<String> soToPoList) {
        this.soToPoList = soToPoList;
    }

    public void addToSoToPoList(String id) {
        soToPoList.add(id);
    }

    public void removeFromSoToPoList(String id) {
        soToPoList.remove(id);
    }

    /**
     * @return Map of (ObjectiveModelID or ProperName if Custom, List of ({@link Player#getUserID}))
     */
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

    public int getScoredSecrets() {
        int count = 0;
        for (Player player : getRealPlayers()) {
            count += player.getSoScored();
        }
        return count;
    }

    public Map<String, String> getLawsInfo() {
        return lawsInfo;
    }

    public void shuffleAgendas() {
        Collections.shuffle(getAgendas());
    }

    public void shuffleEvents() {
        Collections.shuffle(getEvents());
    }

    public void resetAgendas() {
        setAgendas(Mapper.getShuffledDeck(getAgendaDeckID()));
        setDiscardAgendas(new LinkedHashMap<>());
    }

    public void resetEvents() {
        if (Mapper.getDeck(getEventDeckID()) == null)
            return;
        setEvents(Mapper.getShuffledDeck(getEventDeckID()));
        setDiscardedEvents(new LinkedHashMap<>());
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

    public Map<String, Integer> getSentAgendas() {
        return sentAgendas;
    }

    public Map<String, Integer> getDiscardAgendas() {
        return discardAgendas;
    }

    public boolean addEventInEffect(Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> event : discardedEvents.entrySet()) {
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
        for (Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        return addLaw(id, optionalText);
    }

    public boolean addLaw(String id, String optionalText) {
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
            if (getLaws().size() > 2) {
                for (Player p : getRealPlayers()) {
                    if (p.getSecretsUnscored().containsKey("dp")) {
                        MessageHelper.sendMessageToChannel(p.getCardsInfoThread(), p.getRepresentationUnfogged()
                            + ", a reminder that you have _Dictate Policy_, and a 3rd law just got put into play.");
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean reviseLaw(Integer idNumber, String optionalText) {
        String id = "";
        for (Entry<String, Integer> ac : laws.entrySet()) {
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
        for (Entry<String, Integer> agendas : discardAgendas.entrySet()) {
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
        for (Entry<String, Integer> event : discardedEvents.entrySet()) {
            if (event.getValue().equals(idNumber)) {
                id = event.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardedEvents.remove(id);
            getEvents().add(id);
            shuffleEvents();
            return true;
        }
        return false;
    }

    public void shuffleAllAgendasBackIntoDeck() {
        List<String> discardedAgendasIDs = new ArrayList<>(discardAgendas.keySet());
        for (String id : discardedAgendasIDs) {
            discardAgendas.remove(id);
            getAgendas().add(id);
            shuffleAgendas();
        }
    }

    public boolean shuffleAgendaBackIntoDeck(Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            if (agendas.getValue().equals(idNumber)) {
                id = agendas.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardAgendas.remove(id);
            getAgendas().add(id);
            shuffleAgendas();
            return true;
        }
        return false;
    }

    public boolean putEventBackIntoDeckOnTop(Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> event : discardedEvents.entrySet()) {
            if (event.getValue().equals(idNumber)) {
                id = event.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardedEvents.remove(id);
            getEvents().addFirst(id);
            return true;
        }
        return false;
    }

    public boolean putAgendaBackIntoDeckOnTop(Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> entry : discardAgendas.entrySet()) {
            if (entry.getValue().equals(idNumber)) {
                id = entry.getKey();
                break;
            }
        }
        if (!id.isEmpty()) {
            discardAgendas.remove(id);
            getAgendas().addFirst(id);
            return true;
        }
        return false;
    }

    public boolean putAgendaBackIntoDeckOnTop(String id) {
        if (!id.isEmpty()) {
            discardAgendas.remove(id);
            getAgendas().addFirst(id);
            return true;
        }
        return false;
    }

    public boolean removeEventInEffect(Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> event : eventsInEffect.entrySet()) {
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
        for (Entry<String, Integer> ac : laws.entrySet()) {
            if (ac.getValue().equals(idNumber)) {
                id = ac.getKey();
                break;
            }
        }
        if (id.isEmpty()) {
            return false;
        }
        if ("warrant".equalsIgnoreCase(id)) {
            for (Player p2 : getRealPlayers()) {
                if (ButtonHelper.isPlayerElected(this, p2, id)) {
                    p2.setSearchWarrant(false);
                }
            }
        }
        if ("censure".equalsIgnoreCase(id)) {
            Map<String, Integer> customPOs = new HashMap<>(getRevealedPublicObjectives());
            for (String customPO : customPOs.keySet()) {
                if (customPO.toLowerCase().contains("political censure")) {
                    removeCustomPO(customPOs.get(customPO));
                }
            }
        }
        laws.remove(id);
        lawsInfo.remove(id);
        addDiscardAgenda(id);
        return true;
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
            if ("censure".equalsIgnoreCase(id)) {
                if (getCustomPublicVP().get("Political Censure") != null) {
                    Map<String, Integer> customPOs = new HashMap<>(getRevealedPublicObjectives());
                    for (String customPO : customPOs.keySet()) {
                        if (customPO.toLowerCase().contains("political censure")) {
                            removeCustomPO(customPOs.get(customPO));
                        }
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
            for (Entry<String, Integer> event : player.getEvents().entrySet()) {
                if (event.getValue().equals(idNumber)) {
                    id = event.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                getEvents().addFirst(id);
                player.removeEvent(id);
                return true;
            }
        }
        return false;
    }

    public boolean putEventBottom(Integer idNumber, Player player) {
        if (player.getEvents().containsValue(idNumber)) {
            String id = "";
            for (Entry<String, Integer> event : player.getEvents().entrySet()) {
                if (event.getValue().equals(idNumber)) {
                    id = event.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                getEvents().add(id);
                player.removeEvent(id);
                return true;
            }
        }
        return false;
    }

    public boolean putAgendaTop(Integer idNumber) {
        if (sentAgendas.containsValue(idNumber)) {
            String id = "";
            for (Entry<String, Integer> entry : sentAgendas.entrySet()) {
                if (entry.getValue().equals(idNumber)) {
                    id = entry.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                getAgendas().remove(id);
                getAgendas().addFirst(id);
                sentAgendas.remove(id);
                return true;
            }
        }
        return false;
    }

    public boolean putAgendaBottom(Integer idNumber) {
        if (sentAgendas.containsValue(idNumber)) {
            String id = "";
            for (Entry<String, Integer> ac : sentAgendas.entrySet()) {
                if (ac.getValue().equals(idNumber)) {
                    id = ac.getKey();
                    break;
                }
            }
            if (!id.isEmpty()) {
                getAgendas().remove(id);
                getAgendas().add(id);
                sentAgendas.remove(id);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Entry<String, Integer> drawAgenda() {
        if (!getAgendas().isEmpty()) {
            for (String id : getAgendas()) {
                if (!sentAgendas.containsKey(id)) {
                    setSentAgenda(id);
                    for (Entry<String, Integer> entry : sentAgendas.entrySet()) {
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
        if (!getAgendas().isEmpty()) {
            for (String id : getAgendas()) {
                if (agendaID.equalsIgnoreCase(id)) {
                    setSentAgenda(id);
                    for (Entry<String, Integer> entry : sentAgendas.entrySet()) {
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
    public Entry<String, Integer> drawBottomAgenda() {
        if (!getAgendas().isEmpty()) {
            for (int i = getAgendas().size() - 1; i >= 0; i--) {
                String id = getAgendas().get(i);
                if (!sentAgendas.containsKey(id)) {
                    setSentAgenda(id);
                    for (Entry<String, Integer> entry : sentAgendas.entrySet()) {
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
        return getAgendas().get(index);
    }

    public String lookAtBottomAgenda(int indexFromEnd) {
        return getAgendas().get(getAgendas().size() - 1 - indexFromEnd);
    }

    public String lookAtTopEvent(int index) {
        return getEvents().get(index);
    }

    public String lookAtBottomEvent(int indexFromEnd) {
        return getEvents().get(getEvents().size() - 1 - indexFromEnd);
    }

    public String revealEvent(boolean revealFromBottom) {
        int index = revealFromBottom ? getEvents().size() - 1 : 0;
        String id = getEvents().remove(index);
        discardEvent(id);
        return id;
    }

    public boolean revealEvent(String eventID, boolean force) {
        if (getEvents().remove(eventID) || force) {
            discardEvent(eventID);
            return true;
        }
        return false;
    }

    public String revealAgenda(boolean revealFromBottom) {
        int index = revealFromBottom ? getAgendas().size() - 1 : 0;
        String id = getAgendas().remove(index);
        addDiscardAgenda(id);
        return id;
    }

    public boolean revealAgenda(String agendaID, boolean force) {
        if (getAgendas().remove(agendaID) || force) {
            addDiscardAgenda(agendaID);
            return true;
        }
        return false;
    }

    public boolean discardSpecificAgenda(String agendaID) {
        boolean succeeded = getAgendas().remove(agendaID);
        if (succeeded) {
            addDiscardAgenda(agendaID);
        }
        return succeeded;
    }

    public String getNextAgenda(boolean revealFromBottom) {
        int index = revealFromBottom ? getAgendas().size() - 1 : 0;
        return getAgendas().get(index);
    }

    public void drawActionCard(String userID, int count) {
        for (int x = 0; x < count; x++) {
            drawActionCard(userID);
        }
    }

    // Don't shuffle back cards with a status
    private void reshuffleActionCardDiscard() {
        List<String> acsToShuffle = discardActionCards.keySet().stream().toList();
        getActionCards().addAll(acsToShuffle);
        Collections.shuffle(getActionCards());
        acsToShuffle.forEach(ac -> discardActionCards.remove(ac)); //clear out the shuffled back cards
        String msg = "# " + getPing() + ", the action card deck has run out of cards, and so the discard pile has been shuffled to form a new action card deck.";
        MessageHelper.sendMessageToChannel(getMainGameChannel(), msg);
    }

    @Nullable
    public Map<String, Integer> drawActionCard(String userID) {
        if (!getActionCards().isEmpty()) {
            String id = getActionCards().getFirst();
            Player player = getPlayer(userID);
            if (player != null) {
                getActionCards().remove(id);
                player.setActionCard(id);
                return player.getActionCards();
            }
        } else if (!discardActionCards.isEmpty()) {
            reshuffleActionCardDiscard();
            return drawActionCard(userID);
        }
        return null;
    }

    @Nullable
    public Map<String, Integer> drawEvent(String userID) {
        if (!getEvents().isEmpty()) {
            String id = getEvents().getFirst();
            Player player = getPlayer(userID);
            if (player != null) {
                getEvents().remove(id);
                player.setEvent(id);
                return player.getActionCards();
            }
        } else {
            getEvents().addAll(discardedEvents.keySet());
            discardedEvents.clear();
            Collections.shuffle(getEvents());
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

    @JsonIgnore
    public List<TechnologyModel> getUnitUpgradeTechDeck() {
        return getTechnologyDeck().stream()
            .map(Mapper::getTech)
            .filter(TechnologyModel::isUnitUpgrade)
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
            String id = deck.getFirst();
            discardExplore(id);
            result = id;
        }

        // If deck is empty after draw, auto refresh deck from discard
        if (getExplores(reqType, explore).isEmpty()) {
            if (getName().equalsIgnoreCase("pbd1000")) {
                resetExploresOfCertainType(reqType);
            } else {
                shuffleDiscardsIntoExploreDeck(reqType);
            }
        }
        return result;
    }

    public void shuffleDiscardsIntoExploreDeck(String reqType) {
        List<String> discardsOfType = getExplores(reqType, discardExplore);
        List<String> anotherList = new ArrayList<>(discardsOfType);
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
        List<String> exp = Mapper.getDecks().get(getExplorationDeckID()).getNewShuffledDeck();
        explore.addAll(exp);
    }

    public void resetExploresOfCertainType(String reqType) {
        List<String> deck = new ArrayList<>(explore);
        for (String id : deck) {
            ExploreModel card = Mapper.getExplore(id);
            if (card != null) {
                String type = card.getType();
                if (reqType.equalsIgnoreCase(type)) {
                    explore.remove(id);
                }
            }
        }
        deck = new ArrayList<>(discardExplore);
        for (String id : deck) {
            ExploreModel card = Mapper.getExplore(id);
            if (card != null) {
                String type = card.getType();
                if (reqType.equalsIgnoreCase(type)) {
                    discardExplore.remove(id);
                }
            }
        }
        deck = new ArrayList<>(Mapper.getDecks().get(getExplorationDeckID()).getNewShuffledDeck());
        List<String> deck2 = new ArrayList<>(Mapper.getDecks().get(getExplorationDeckID()).getNewShuffledDeck());
        for (String id : deck2) {
            ExploreModel card = Mapper.getExplore(id);
            if (card != null) {
                String type = card.getType();
                if (!reqType.equalsIgnoreCase(type) || id.contains("starchart") || id.contains("mirage")) {
                    deck.remove(id);
                }
            }
        }
        Collections.shuffle(deck);
        explore.addAll(deck);

    }

    public void triplicateExplores() {
        explore = Mapper.getDecks().get("explores_pok").getNewDeck();
        List<String> exp2 = new ArrayList<>(explore);
        for (String card : exp2) {
            explore.add(card + "extra1");
            explore.add(card + "extra2");
        }
        Collections.shuffle(explore);
    }

    public void pbd1000decks() {
        setActionCards(multiplyDeck(2, "action_cards_pok", "action_deck_2"));
        setSecretObjectives(multiplyDeck(3, "pbd100_secret_objectives"));
    }

    public void triplicateACs() {
        setActionCards(multiplyDeck(3, "action_cards_pok"));
    }

    public void triplicateSOs() {
        setSecretObjectives(multiplyDeck(3, "secret_objectives_pok"));
    }

    private List<String> multiplyDeck(int totalCopies, String... deckIDs) {
        List<String> newDeck = Arrays.stream(deckIDs).flatMap(deckID -> Mapper.getDecks().get(deckID).getNewDeck().stream()).toList();
        List<String> newDeck2 = new ArrayList<>(newDeck);
        for (String card : newDeck)
            for (int i = 1; i < totalCopies; i++)
                newDeck2.add(card + "extra" + i);
        Collections.shuffle(newDeck2);
        return newDeck2;
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
        if (!getActionCards().isEmpty()) {
            String id = getActionCards().getFirst();
            getActionCards().remove(id);
            setDiscardActionCard(id);
            return id;
        } else {
            reshuffleActionCardDiscard();
            return drawActionCardAndDiscard();
        }
    }

    public void checkSOLimit(Player player) {
        if (player.getSecretsScored().size() + player.getSecretsUnscored().size() > player.getMaxSOCount()) {
            String msg = player.getRepresentationUnfogged() + " you have more secret objectives than the limit ("
                + player.getMaxSOCount()
                + ") and should discard one. If your game is playing with a higher secret objective limit, you may change that in `/game setup`.";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            String secretScoreMsg = "Click a button below to discard your secret objective.";
            List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveDiscardButtons(player);
            if (!soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), secretScoreMsg,
                    soButtons);
            } else {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Something went wrong. Please report to Fin.");
            }
        }
    }

    public String drawSecretObjective(String userID) {
        if (!getSecretObjectives().isEmpty()) {
            String id = getSecretObjectives().getFirst();
            Player player = getPlayer(userID);
            if (player != null) {
                removeSOFromGame(id);
                player.setSecret(id);
                checkSOLimit(player);
            }
            return id;
        }
        return null;
    }

    @Nullable
    public Map<String, Integer> drawSpecificSecretObjective(String soID, String userID) {
        if (!getSecretObjectives().isEmpty()) {
            boolean remove = removeSOFromGame(soID);
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

    public void drawSpecificActionCard(String acID, String userID) {
        if (getActionCards().isEmpty()) {
            return;
        }
        int tries = 0;
        while (tries < 3) {
            if (getActionCards().contains(acID)) {
                Player player = getPlayer(userID);
                if (player != null) {
                    getActionCards().remove(acID);
                    player.setActionCard(acID);
                    return;
                }
                tries = 12;
            }
            tries++;
            if (acID.contains("extra1")) {
                acID = acID.replace("extra1", "extra2");
            } else {
                acID += "extra1";
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

    public void setDiscardActionCard(String id, int oldNum) {
        Collection<Integer> values = discardActionCards.values();
        int identifier = oldNum;
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
            for (Entry<String, Integer> ac : actionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                player.removeActionCard(acIDNumber);
                setDiscardActionCard(acID, acIDNumber);
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
            for (Entry<String, Integer> ac : actionCards.entrySet()) {
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
        Collections.shuffle(getActionCards());
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
            for (Entry<String, Integer> ac : discardActionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                discardActionCards.remove(acID);
                player.setActionCard(acID, acIDNumber);
                return true;
            }
        }
        return false;
    }

    public boolean pickActionCardFromPurged(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            String acID = "";
            for (Entry<String, Integer> ac : purgedActionCards.entrySet()) {
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
        for (Entry<String, Integer> ac : discardActionCards.entrySet()) {
            if (ac.getValue().equals(acIDNumber)) {
                acID = ac.getKey();
                break;
            }
        }
        if (!acID.isEmpty()) {
            discardActionCards.remove(acID);
            getActionCards().add(acID);
            Collections.shuffle(getActionCards());
            return true;
        }
        return false;
    }

    public boolean scoreSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            Map<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (Entry<String, Integer> so : secrets.entrySet()) {
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
            for (Entry<String, Integer> so : secrets.entrySet()) {
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
            for (Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecretScored(soIDNumber);
                getSecretObjectives().add(soID);
                Collections.shuffle(getSecretObjectives());
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
            for (Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecret(soIDNumber);
                getSecretObjectives().add(soID);
                Collections.shuffle(getSecretObjectives());
                return true;
            }
        }
        return false;
    }

    public void addSecretObjective(String id) {
        if (!getSecretObjectives().contains(id)) {
            getSecretObjectives().add(id);
            Collections.shuffle(getSecretObjectives());
        }
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

    @JsonIgnore
    public boolean islandMode() {
        boolean otherThings = getName().contains("island") || (getMapTemplateID() != null && getMapTemplateID().equals("1pIsland"));
        if (otherThings) setStoredValue("IslandMode", "true");
        return getStoredValue("IslandMode").equals("true");
    }

    public boolean loadGameSettingsFromSettings(GenericInteractionCreateEvent event, MiltySettings miltySettings) {
        SourceSettings sources = miltySettings.getSourceSettings();
        if (sources.getAbsol().isVal()) setAbsolMode(true);

        GameSettings settings = miltySettings.getGameSettings();
        setVp(settings.getPointTotal().getVal());
        setMaxSOCountPerPlayer(settings.getSecrets().getVal());
        if (settings.getTigl().isVal()) {
            TIGLHelper.initializeTIGLGame(this);
        }
        setAllianceMode(settings.getAlliance().isVal());

        if (settings.getMapTemplate().getValue().getAlias().equals("1pIsland")) {
            setStoredValue("IslandMode", "true");
        }

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
        setStrategyCardSet(deckSettings.getStratCards().getChosenKey());

        // Setup peakable objectives
        setUpPeakableObjectives(miltySettings.getGameSettings().getStage1s().getVal(), 1);
        setUpPeakableObjectives(miltySettings.getGameSettings().getStage2s().getVal(), 2);

        if (isAbsolMode() && !deckSettings.getAgendas().getChosenKey().contains("absol")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game seems to be using absol mode, so the agenda deck you chose will be overridden.");
            success &= validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"));
        } else {
            success &= validateAndSetAgendaDeck(event, deckSettings.getAgendas().getValue());
        }

        if (isAbsolMode() && !deckSettings.getRelics().getChosenKey().contains("absol")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game seems to be using absol mode, so the relic deck you chose will be overridden.");
            success &= validateAndSetRelicDeck(Mapper.getDeck("relics_absol"));
        } else {
            success &= validateAndSetRelicDeck(deckSettings.getRelics().getValue());
        }

        return success;
    }

    public boolean validateAndSetPublicObjectivesStage1Deck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getStage1PublicDeckID().equals(deck.getAlias())) return true;

        int peekableStageOneCount = getPublicObjectives1Peakable().size();
        setUpPeakableObjectives(0, 1);
        if (getRevealedPublicObjectives().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change public objective deck to **" + deck.getName() + "** while there are revealed public objectives.");
            return false;
        }

        setStage1PublicDeckID(deck.getAlias());
        setPublicObjectives1(deck.getNewShuffledDeck());
        setUpPeakableObjectives(peekableStageOneCount, 1);
        return true;
    }

    public boolean validateAndSetPublicObjectivesStage2Deck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getStage2PublicDeckID().equals(deck.getAlias())) return true;

        int peekableStageTwoCount = getPublicObjectives2Peakable().size();
        setUpPeakableObjectives(0, 2);
        if (getRevealedPublicObjectives().size() > 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change public objective deck to **" + deck.getName() + "** while there are revealed public objectives.");
            return false;
        }

        setStage2PublicDeckID(deck.getAlias());
        setPublicObjectives2(deck.getNewShuffledDeck());
        setUpPeakableObjectives(peekableStageTwoCount, 2);
        return true;
    }

    public void resetActionCardDeck(DeckModel deck) {
        setActionCards(deck.getNewShuffledDeck());
        getDiscardActionCards().clear();
        for (Player player : getPlayers().values()) {
            player.getActionCards().clear();
        }
    }

    public boolean validateAndSetActionCardDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getAcDeckID().equals(deck.getAlias())) return true;

        boolean shuffledExtrasIn = false;
        List<String> oldDeck = new ArrayList<>(Mapper.getDeck(getAcDeckID()).getNewShuffledDeck());
        setAcDeckID(deck.getAlias());
        List<String> newDeck = new ArrayList<>(deck.getNewShuffledDeck());
        for (String ac : oldDeck) {
            newDeck.remove(ac);
        }
        if (!getDiscardActionCards().isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Since there were action cards in the discard pile, will just shuffle any new action cards into the existing deck.");
            shuffledExtrasIn = true;
        } else {
            for (Player player : getPlayers().values()) {
                if (!player.getActionCards().isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Since there were action cards in players hands, will just shuffle any new action cards into the existing deck.");
                    shuffledExtrasIn = true;
                    break;
                }
            }
        }
        if (!shuffledExtrasIn) {
            setActionCards(deck.getNewShuffledDeck());
        } else {
            for (String acID : newDeck) {
                getActionCards().add(acID);
            }
            Collections.shuffle(getActionCards());
        }
        return true;
    }

    public boolean validateAndSetRelicDeck(DeckModel deck) {
        if (getRelicDeckID().equals(deck.getAlias())) return true;

        setRelicDeckID(deck.getAlias());
        setRelics(deck.getNewShuffledDeck());
        return true;
    }

    public void shuffleDecks() {
        Collections.shuffle(relics);
        Collections.shuffle(getActionCards());
        Collections.shuffle(getSecretObjectives());
        Collections.shuffle(explore);
    }

    public boolean validateAndSetSecretObjectiveDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getSoDeckID().equals(deck.getAlias())) return true;

        for (Player player : getPlayers().values()) {
            if (!player.getSecrets().isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change secret objective deck to **" + deck.getName() + "** while there are secret objectives in player hands.");
                return false;
            }
        }
        setSoDeckID(deck.getAlias());
        setSecretObjectives(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetExploreDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getExplorationDeckID().equals(deck.getAlias())) return true;

        if (!getAllExploreDiscard().isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change exploration deck to **" + deck.getName() + "** while there are exploration cards in the discard piles.");
            return false;
        }
        setExplorationDeckID(deck.getAlias());
        setExploreDeck(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetAgendaDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getAgendaDeckID().equals(deck.getAlias())) return true;

        if (!getDiscardAgendas().isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change agenda deck to **" + deck.getName() + "** while there are agendas in the discard pile.");
            return false;
        }
        setAgendaDeckID(deck.getAlias());
        setAgendas(deck.getNewShuffledDeck());
        return true;
    }

    public boolean validateAndSetTechnologyDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getTechnologyDeckID().equals(deck.getAlias())) return true;

        swapOutVariantTechs();
        setTechnologyDeckID(deck.getAlias());
        swapInVariantTechs();
        return true;
    }

    public boolean validateAndSetEventDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getEventDeckID().equals(deck.getAlias())) return true;

        if (!getDiscardedEvents().isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot change event deck to **" + deck.getName() + "** while there are events in the discard pile.");
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
    public String getPing() {
        Role role = getGameRole();
        if (role != null) {
            return role.getAsMention();
        }
        StringBuilder sb = new StringBuilder(getName()).append(" ");
        for (String playerID : getPlayerIDs()) {
            User user = AsyncTI4DiscordBot.jda.getUserById(playerID);
            if (user != null)
                sb.append(user.getAsMention()).append(" ");
        }
        return sb.toString();
    }

    @JsonIgnore
    public Role getGameRole() {
        if (getGuild() != null) {
            for (Role role : getGuild().getRoles()) {
                if (getName().equals(role.getName().toLowerCase())) {
                    return role;
                }
            }
        }
        return null;
    }

    public Map<String, Tile> getTileMap() {
        return tileMap;
    }

    public Tile getTileFromPositionOrAlias(String positionOrAlias) {
        if (getTileByPosition(positionOrAlias) != null)
            return getTileByPosition(positionOrAlias);
        return getTile(AliasHandler.resolveTile(positionOrAlias));
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
        if (position == null) return null;
        return tileMap.get(position);
    }

    public boolean isTileDuplicated(String tileID) {
        return tileMap.values().stream()
            .filter(tile -> tile.getTileID().equals(tileID))
            .count() > 1;
    }

    public Player getPlayerThatControlsTile(String tileId) {
        return getPlayerThatControlsTile(tileMap.get(tileId));
    }

    public Player getPlayerThatControlsTile(Tile tile) {
        if (tile == null) {
            return null;
        }
        for (Player player : getRealPlayers()) {
            if (FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                return player;
            }
        }
        return null;
    }

    public Player addPlayer(String id, String name) {
        Player player = new Player(id, name, this);
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
    public List<Player> getRealPlayersNNeutral() {
        return getPlayers().values().stream()
            .filter(p -> p.isRealPlayer() || (p.getFaction() != null && p.getFaction().equals("neutral")))
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getRealPlayersNDummies() {
        return getPlayers().values().stream().filter(player -> player.isRealPlayer() || player.isDummy() && player.getColor() != null && !"null".equals(player.getColor()))
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
        return getPlayers().values().stream().filter(not(Player::isRealPlayer)).collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Player> getPlayersWithGMRole() {
        if (getGuild() == null) return Collections.emptyList();
        List<Role> roles = getGuild().getRolesByName(getName() + " GM", true);
        Role gmRole = roles.isEmpty() ? null : roles.getFirst();
        List<Player> gmPlayers = getPlayers().values().stream()
            .filter(player -> {
                Member user = getGuild().getMemberById(player.getUserID());
                return user != null && user.getRoles().contains(gmRole);
            }).toList();
        setFogOfWarGMIDs(gmPlayers.stream().map(Player::getUserID).toList()); // For @ExportableField (Website)
        return gmPlayers;
    }

    @JsonIgnore
    public List<Player> getPassedPlayers() {
        List<Player> passedPlayers = new ArrayList<>();
        for (Player player : getRealPlayers()) {
            if (player.isPassed()) {
                passedPlayers.add(player);
            }
        }
        return passedPlayers;
    }

    @JsonIgnore
    public Set<String> getFactions() {
        return getRealAndEliminatedAndDummyPlayers().stream().map(Player::getFaction).collect(Collectors.toSet());
    }

    public void setPlayers(Map<String, Player> players) {
        this.players = players;
    }

    public void setPlayer(String playerID, Player player) {
        players.put(playerID, player);
    }

    public Player getPlayer(String userID) {
        if (userID == null) return null;
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

    @Override
    public void setOwnerID(String ownerID) {
        if (ownerID.length() > 18)
            ownerID = ownerID.substring(0, 18);
        super.setOwnerID(ownerID);
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
                for (Entry<String, UnitHolder> unitHolderEntry : tile.getUnitHolders().entrySet()) {
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
        if (!Helper.findDuplicateInList(allPlayerHandPromissoryNotes).isEmpty()) {
            BotLogger.log("`" + getName() + "`: there are duplicate promissory notes in the game:\n> `"
                + Helper.findDuplicateInList(allPlayerHandPromissoryNotes) + "`");
        }

        allPromissoryNotes.addAll(getPurgedPN());

        // Find PNs that are extra - players have them but nobody "owns" them
        List<String> unOwnedPromissoryNotes = new ArrayList<>(allPromissoryNotes);
        unOwnedPromissoryNotes.removeAll(allOwnedPromissoryNotes);
        if (!unOwnedPromissoryNotes.isEmpty()) {
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
        if (!missingPromissoryNotes.isEmpty()) {
            BotLogger.log("`" + getName() + "`: there are promissory notes that should be in the game but are not:\n> `" + missingPromissoryNotes + "`");
            for (Player player : getPlayers().values()) {
                PromissoryNoteHelper.checkAndAddPNs(this, player);
            }
            GameManager.save(this, "Added missing promissory notes to players' hands: " + missingPromissoryNotes);
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
                Set<String> leaders = new HashSet<>(Arrays.asList(fakeString.split("\\|")));
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
        if (player.hasAbility("imperia")) {
            for (Player otherPlayer : getRealPlayers()) {
                if (otherPlayer.equals(player))
                    continue;
                if (player.getMahactCC().contains(otherPlayer.getColor())) {

                    if (otherPlayer.hasLeaderUnlocked(leaderID)) {
                        if (isAllianceMode() && "mahact".equalsIgnoreCase(player.getFaction())) {
                            return leaderID.contains(otherPlayer.getFaction());
                        }
                    }
                }
            }
        }

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
            for (Player player_ : getRealPlayersNDummies()) {
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
        setMapImageGenerationCount(getMapImageGenerationCount() + 1);
    }

    public boolean hasRunMigration(String string) {
        return runDataMigrations.contains(string);
    }

    public void addMigration(String string) {
        runDataMigrations.add(string);
    }

    public Set<String> getRunMigrations() {
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
    public int getHazardousExploreDiscardSize() {
        return getExploreDiscard(Constants.HAZARDOUS).size();
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
    public int getCulturalExploreDiscardSize() {
        return getExploreDiscard(Constants.CULTURAL).size();
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
    public int getIndustrialExploreDiscardSize() {
        return getExploreDiscard(Constants.INDUSTRIAL).size();
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
    public int getFrontierExploreDiscardSize() {
        return getExploreDiscard(Constants.FRONTIER).size();
    }

    @JsonIgnore
    public int getFrontierExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.FRONTIER);
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

    public Optional<UnitModel> getPriorityUnitByUnitKey(UnitKey uk, UnitHolder uh) {
        return getPlayerByUnitKey(uk).map(p -> p.getPriorityUnitByAsyncID(uk.asyncID(), uh));
    }

    public Optional<Player> getPlayerByUnitKey(UnitKey unit) {
        return getRealPlayersNDummies().stream()
            .filter(otherPlayer -> Mapper.getColorID(otherPlayer.getColor()).equals(unit.getColorID()))
            .findFirst();
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

    @JsonIgnore
    public Tile getMecatolTile() {
        for (String mr : Constants.MECATOL_SYSTEMS) {
            Tile tile = getTile(mr);
            if (tile != null)
                return tile;
        }
        return null;
    }

    @Nullable
    public Tile getTileFromPlanet(String planetName) {
        for (Tile tile_ : getTileMap().values()) {
            for (Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    return tile_;
                }
            }
        }
        return null;
    }

    @Nullable
    public Planet getUnitHolderFromPlanet(String planetName) {
        Tile tile_ = getTileFromPlanet(planetName);
        if (tile_ == null) {
            return null;
        }
        return tile_.getUnitHolderFromPlanet(planetName);
    }

    @Nullable
    public Player getPlayerFromColorOrFaction(String factionOrColor) {
        if (factionOrColor == null) {
            return null;
        }
        String factionColor = AliasHandler.resolveColor(factionOrColor.toLowerCase());
        factionColor = StringUtils.substringBefore(factionColor, " "); // TO HANDLE UNRESOLVED AUTOCOMPLETE
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player : getPlayers().values()) {
            if ("keleres".equalsIgnoreCase(factionColor)) {
                if (Objects.equals(factionColor + "a", player.getFaction())) {
                    return player;
                }
                if (Objects.equals(factionColor + "x", player.getFaction())) {
                    return player;
                }
                if (Objects.equals(factionColor + "m", player.getFaction())) {
                    return player;
                }
            }
            if (Objects.equals(factionColor, player.getFaction()) ||
                Objects.equals(factionColor, player.getColor()) ||
                Objects.equals(factionColor, player.getColorID())) {
                return player;
            }
        }
        return null;
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
                    boolean variantReplacesPok = variantUnit.getHomebrewReplacesID().orElse("-").equals(playerUnit.getId());
                    boolean pokReplacesVariant = playerUnit.getHomebrewReplacesID().orElse("-").equals(variantUnit.getId());
                    if (variantReplacesPok || pokReplacesVariant) {
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
        if (player.hasTheZeroToken()) // naalu 0 token ability
            scText = "0/" + scText;
        return scText;
    }

    @JsonIgnore
    public boolean isLittleOmega() {
        return getStage1PublicDeckID().contains("little_omega") || getStage2PublicDeckID().contains("little_omega")
            || getAgendaDeckID().contains("little_omega");
    }

    // Currently unused
    // TODO (Jazz): parse this better
    public List<ComponentSource> getComponentSources() {
        List<ComponentSource> sources = new ArrayList<>();
        sources.add(ComponentSource.base);
        sources.add(ComponentSource.codex1);
        sources.add(ComponentSource.codex2);
        sources.add(ComponentSource.codex3);

        if (!isBaseGameMode())
            sources.add(ComponentSource.pok);
        if (isAbsolMode())
            sources.add(ComponentSource.absol);
        if (isVotcMode())
            sources.add(ComponentSource.cryypter);
        if (isMiltyModMode())
            sources.add(ComponentSource.miltymod);
        if (isDiscordantStarsMode())
            sources.add(ComponentSource.ds);
        return sources;
    }

    @JsonIgnore
    public boolean hasHomebrew() {
        return isHomebrew()
            || isExtraSecretMode()
            || isFowMode()
            || isAgeOfExplorationMode()
            || isMinorFactionsMode()
            || isLightFogMode()
            || isRedTapeMode()
            || isDiscordantStarsMode()
            || isFrankenGame()
            || isMiltyModMode()
            || isAbsolMode()
            || isVotcMode()
            || isPromisesPromisesMode()
            || isFlagshippingMode()
            || isAllianceMode()
            || getSpinMode() != null && !"OFF".equalsIgnoreCase(getSpinMode())
            || isHomebrewSCMode()
            || isCommunityMode()
            || !checkAllDecksAreOfficial()
            || !checkAllTilesAreOfficial()
            || getFactions().stream()
                .map(Mapper::getFaction)
                .filter(Objects::nonNull)
                .anyMatch(faction -> !faction.getSource().isOfficial())
            || getRealAndEliminatedAndDummyPlayers().stream()
                .map(Player::getLeaderIDs)
                .flatMap(Collection::stream)
                .map(Mapper::getLeader)
                .filter(Objects::nonNull)
                .anyMatch(leader -> !leader.getSource().isOfficial())
            || (publicObjectives1 != null && publicObjectives1.size() < 5 && getRound() >= 4)
            || (publicObjectives2 != null && publicObjectives2.size() < (getRound() - 4))
            || getRealPlayers().stream()
                .anyMatch(player -> player.getSecretVictoryPoints() > 3
                    && !player.getRelics().contains("obsidian"))
            || getPlayerCountForMap() < 3
            || getRealAndEliminatedAndDummyPlayers().size() < 3
            || getPlayerCountForMap() > 8
            || getRealAndEliminatedAndDummyPlayers().size() > 8;
    }

    private boolean checkAllDecksAreOfficial() {
        // needs to check for homebrew tiles still
        // Decks
        List<String> deckIDs = new ArrayList<>();
        deckIDs.add(getAcDeckID());
        deckIDs.add(getSoDeckID());
        deckIDs.add(getStage1PublicDeckID());
        deckIDs.add(getStage2PublicDeckID());
        deckIDs.add(getRelicDeckID());
        deckIDs.add(getAgendaDeckID());
        deckIDs.add(getExplorationDeckID());
        deckIDs.add(getTechnologyDeckID());
        deckIDs.add(getEventDeckID());
        boolean allDecksOfficial = deckIDs.stream().allMatch(id -> {
            DeckModel deck = Mapper.getDeck(id);
            if ("null".equals(id)) return true;
            if (deck == null) return true;
            return deck.getSource().isOfficial();
        });
        StrategyCardSetModel scset = Mapper.getStrategyCardSets().get(getScSetID());
        if (scset == null || !scset.getSource().isOfficial()) {
            allDecksOfficial = false;
        }
        return allDecksOfficial;
    }

    private boolean checkAllTilesAreOfficial() {
        // Tiles
        return getTileMap().values().stream().allMatch(tile -> {
            if (tile == null || tile.getTileModel() == null) {
                return true;
            }
            ComponentSource tileSource = tile.getTileModel().getSource();
            if (tile.getTileModel().getImagePath().endsWith("_Hyperlane.png")) {
                return true; //official hyperlane
            }
            return tileSource != null && tileSource.isOfficial();
        });
    }

    public void setStrategyCardSet(String scSetID) {
        StrategyCardSetModel strategyCardModel = Mapper.getStrategyCardSets().get(scSetID);
        setHomebrewSCMode(!"pok".equals(scSetID) && !"base_game".equals(scSetID));

        Map<Integer, Integer> oldTGs = getScTradeGoods();
        setScTradeGoods(new LinkedHashMap<>());
        setScSetID(strategyCardModel.getAlias());
        strategyCardModel.getStrategyCardModels().forEach(scModel -> setScTradeGood(scModel.getInitiative(), oldTGs.getOrDefault(scModel.getInitiative(), 0)));
    }

    @JsonIgnore
    public List<ColorModel> getUnusedColorsPreferringBase() {
        List<String> priorityColourIDs = List.of("red", "blue", "yellow", "purple", "green", "orange", "pink", "black");
        List<ColorModel> priorityColours = priorityColourIDs.stream()
            .map(Mapper::getColor)
            .filter(color -> getPlayers().values().stream().noneMatch(player -> player.getColor().equals(color.getName())))
            .toList();
        if (!priorityColours.isEmpty()) {
            return priorityColours;
        }
        return getUnusedColors();
    }

    public List<ColorModel> getUnusedColors() {
        return Mapper.getColors().stream()
            .filter(color -> getPlayers().values().stream().noneMatch(player -> player.getColor().equals(color.getName())))
            .toList();
    }

    public boolean addTag(String tag) {
        return getTags().add(tag);
    }

    public boolean removeTag(String tag) {
        return getTags().remove(tag);
    }

    public void checkCommanderUnlocks(String factionToCheck) {
        CommanderUnlockCheckService.checkAllPlayersInGame(this, factionToCheck);
    }

    /**
     * @return String : TTS/TTPG Map String
     */
    @JsonIgnore
    public String getMapString() {
        List<String> tilePositions = new ArrayList<>();
        tilePositions.add("000");

        int ringCountMax = getRingCount();
        int ringCount = 1;
        int tileCount = 1;
        while (ringCount <= ringCountMax) {
            String position = "" + ringCount + (tileCount < 10 ? "0" + tileCount : tileCount);
            tilePositions.add(position);
            tileCount++;
            if (tileCount > ringCount * 6) {
                tileCount = 1;
                ringCount++;
            }
        }

        List<String> sortedTilePositions = tilePositions.stream()
            .sorted(Comparator.comparingInt(Integer::parseInt))
            .toList();
        Map<String, Tile> tileMap = new HashMap<>(getTileMap());
        StringBuilder sb = new StringBuilder();
        for (String position : sortedTilePositions) {
            boolean missingTile = true;
            for (Tile tile : tileMap.values()) {
                if (tile.getPosition().equals(position)) {
                    String tileID = AliasHandler.resolveStandardTile(tile.getTileID()).toUpperCase();
                    if ("000".equalsIgnoreCase(position) && "18".equalsIgnoreCase(tileID)) {
                        // Mecatol Rex in Centre Position
                        sb.append("{18}");
                    } else if ("000".equalsIgnoreCase(position) && !"18".equalsIgnoreCase(tileID)) {
                        // Something else is in the Centre Position
                        sb.append("{").append(tileID).append("}");
                    } else {
                        sb.append(tileID);
                    }
                    missingTile = false;
                    break;
                }
            }
            if (missingTile && "000".equalsIgnoreCase(position)) {
                sb.append("{-1}");
            } else if (missingTile) {
                sb.append("-1");
            }
            sb.append(" ");
        }
        setMapString(sb.toString().trim());
        return sb.toString().trim();
    }

    public String getHexSummary() {
        // 18+0+0*b;Bio,71+0+2Rct;Ro;Ri,36+1+1Kcf;Km*I;Ki,76+1-1;;;,72+0-2; ......
        // CSV of {tileID}{+x+yCoords}??{list;of;tokens} ?? 
        // See ConvertTTPGtoAsync.ConvertTTPGHexToAsyncTile() and reverse it!
        return getTileMap().values().stream()
            .map(Tile::getHexTileSummary)
            .collect(Collectors.joining(","));
    }

    public boolean hasUser(User user) {
        if (user == null) return false;
        String id = user.getId();
        for (Player player : getPlayers().values()) {
            if (player.getUserID().equals(id)) {
                return true;
            }
            if (player.getTeamMateIDs().contains(id)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getAllTeamMateIDs() {
        List<String> teamMateIDs = new ArrayList<>();
        for (Player player : getPlayers().values()) {
            teamMateIDs.addAll(player.getTeamMateIDs());
            teamMateIDs.remove(player.getUserID());
        }
        return teamMateIDs;
    }
}
