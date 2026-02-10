package ti4.map;

import static java.util.function.Predicate.not;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
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
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.commands.planet.PlanetRemove;
import ti4.draft.BagDraft;
import ti4.draft.DraftItem;
import ti4.draft.FrankenDraft;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.DistanceTool;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.omega_phase.VoiceOfTheCouncilHelper;
import ti4.helpers.settingsFramework.menus.DeckSettings;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.GameSetupSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.map.manager.BorderAnomalyManager;
import ti4.map.manager.StrategyCardManager;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.ActionCardModel;
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
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.draft.DraftLoadService;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftTileManager;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.spring.jda.JdaService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class Game extends GameProperties {

    private static final JsonMapper mapper =
            JsonMapper.builder().findAndAddModules().build();

    // TODO (Jazz): Sort through these and add to GameProperties
    private Map<String, Tile> tileMap = new HashMap<>(); // Position, Tile

    @Getter
    private Map<String, Player> players = new LinkedHashMap<>();

    private final Map<String, Planet> planets = new HashMap<>();
    private final Map<FOWOption, Boolean> fowOptions = new HashMap<>();
    private final Map<Integer, Boolean> scPlayed = new HashMap<>();
    private final Map<String, String> checkingForAllReacts = new HashMap<>();
    private List<String> listOfTilePinged = new ArrayList<>();

    // TODO (Jazz): These should be easily added to GameProperties
    private Map<String, Integer> thalnosUnits = new HashMap<>();
    private Map<String, Integer> slashCommandsUsed = new HashMap<>();
    private Map<String, Integer> actionCardsSabotaged = new HashMap<>();
    private Map<String, String> currentAgendaVotes = new HashMap<>();

    @Setter
    @Getter
    private Map<String, Map<UnitKey, List<Integer>>> tacticalActionDisplacement = new HashMap<>();

    private @Deprecated Map<String, Integer> displacedUnitsFrom1System = new HashMap<>();
    private @Deprecated Map<String, Integer> displacedUnitsFromEntireTacticalAction = new HashMap<>();

    @Setter
    @Getter
    private DisplayType displayTypeForced;

    private final BorderAnomalyManager borderAnomalyManager = new BorderAnomalyManager();
    private Date lastActivePlayerChange = new Date(0);

    private boolean autoPingEnabled;

    @Getter
    private Map<String, Integer> discardedEvents = new LinkedHashMap<>();

    @Getter
    @Setter
    private Map<String, Integer> eventsInEffect = new LinkedHashMap<>();

    private final StrategyCardManager strategyCardManager = new StrategyCardManager(this);
    private Map<String, Integer> discardAgendas = new LinkedHashMap<>();
    private Map<String, Integer> sentAgendas = new LinkedHashMap<>();
    private Map<String, Integer> laws = new LinkedHashMap<>();
    private Map<String, String> lawsInfo = new LinkedHashMap<>();
    private Map<String, Integer> revealedPublicObjectives = new LinkedHashMap<>();
    private Map<String, Integer> customPublicVP = new LinkedHashMap<>();
    private Map<String, List<String>> scoredPublicObjectives = new LinkedHashMap<>();
    private Map<String, List<String>> customAdjacentTiles = new LinkedHashMap<>();

    @Getter
    @Setter
    private Map<String, String> customHyperlaneData = new LinkedHashMap<>();

    private LinkedHashMap<Pair<String, Integer>, String> adjacencyOverrides = new LinkedHashMap<>();

    private List<String> publicObjectives1;
    private List<String> publicObjectives2;
    private List<String> publicObjectives1Peekable = new ArrayList<>();
    private List<String> publicObjectives2Peekable = new ArrayList<>();

    @Getter
    @Setter
    private Map<String, List<String>> publicObjectives1Peeked = new LinkedHashMap<>();

    @Getter
    @Setter
    private Map<String, List<String>> publicObjectives2Peeked = new LinkedHashMap<>();

    private List<String> savedButtons = new ArrayList<>();
    private List<String> soToPoList = new ArrayList<>();

    private List<String> purgedPN = new ArrayList<>();

    private List<String> explore;
    private List<String> discardExplore = new ArrayList<>();
    private List<String> relics;

    private List<SimpleEntry<String, String>> tileNameAutocompleteOptionsCache;

    private final Set<String> runDataMigrations = new HashSet<>();
    private BagDraft activeDraft;

    @Getter
    @Setter
    private Map<String, Integer> tileDistances = new HashMap<>();

    private MiltyDraftManager miltyDraftManager;
    private DraftTileManager draftTileManager;
    private DraftManager draftManager;
    private DistanceTool distanceTool;

    @Getter
    private final Expeditions expeditions = new Expeditions(this);

    @Setter
    @Getter
    private String miltyDraftString;

    @Setter
    @Getter
    private String draftSystemSettingsJson;

    @Setter
    @Getter
    private String draftString;

    @Setter
    private MiltySettings miltySettings;

    @Setter
    private DraftSystemSettings draftSystemSettings;

    @Getter
    @Setter
    private String miltyJson;

    @Getter
    @Setter
    private TIGLRank minimumTIGLRankAtGameStart;

    private Map<String, String> debtPoolIcons = new HashMap<>();

    public Game() {
        long currentTimeMillis = System.currentTimeMillis();
        setCreationDate(Helper.getDateRepresentation(currentTimeMillis));
        setCreationDateTime(currentTimeMillis);
        setLastModifiedDate(currentTimeMillis);
    }

    public void newGameSetup() {
        // Normal Decks
        publicObjectives1 = Mapper.getShuffledDeck("public_stage_1_objectives_pok");
        publicObjectives2 = Mapper.getShuffledDeck("public_stage_2_objectives_pok");
        setSecretObjectives(Mapper.getShuffledDeck("secret_objectives_pok"));
        setActionCards(Mapper.getShuffledDeck("action_cards_pok"));
        setAgendas(Mapper.getShuffledDeck("agendas_pok"));
        explore = Mapper.getShuffledDeck("explores_pok");
        setRelics(Mapper.getShuffledDeck("relics_pok_te"));
        setStrategyCardSet("te");

        // OTHER
        setEvents(new ArrayList<>()); // ignis_aurora
        addCustomPO(Constants.CUSTODIAN, 1);
        setUpPeekableObjectives(5, 1);
        setUpPeekableObjectives(5, 2);
    }

    public void fixScrewedRelics() {

        for (Player p2 : getRealPlayers()) {
            List<String> relics = new ArrayList<>(p2.getRelics());
            for (String relic : relics) {
                if (Mapper.getRelic(relic) == null) {
                    p2.removeRelic(relic);
                }
            }
        }
    }

    public void fixScrewedSOs() {
        MessageHelper.sendMessageToChannel(
                getActionsChannel(),
                "The number of secret objectives in the deck before this operation is " + getNumberOfSOsInTheDeck()
                        + ". The number in players hands is " + getNumberOfSOsInPlayersHands() + ".");

        List<String> defaultSecrets =
                Mapper.getDecks().get("secret_objectives_pok").getNewShuffledDeck();
        List<String> currentSecrets = new ArrayList<>(getSecretObjectives());
        for (Player player : players.values()) {
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
        MessageHelper.sendMessageToChannel(
                getActionsChannel(),
                "Fixed the secret objectives. The total amount of secret objectives in deck is "
                        + getNumberOfSOsInTheDeck() + ". The number in players hands is "
                        + getNumberOfSOsInPlayersHands());
    }

    public Player getPlayerThatControlsPlanet(String planet) {
        for (Player p : getRealPlayers()) {
            if (p.getPlanets().contains(planet)) {
                return p;
            }
        }

        return null;
    }

    public Player setupNeutralPlayer(String color) {
        Player neutral = players.get(Constants.dicecordId);
        if (neutral != null) {
            ColorChangeHelper.changePlayerColor(this, neutral, neutral.getColor(), color);
            return players.get(Constants.dicecordId);
        }
        addPlayer(Constants.dicecordId, "Dicecord"); // Dicecord
        neutral = getPlayer(Constants.dicecordId);
        neutral.setColor(color);
        neutral.setFaction("neutral");
        neutral.setDummy(true);
        FactionModel setupInfo = neutral.getFactionSetupInfo();
        Set<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        neutral.setUnitsOwned(playerOwnedUnits);
        neutral.addTech("ff2");
        neutral.addTech("dd2");
        neutral.addTech("cv2");
        neutral.addTech("cr2");
        neutral.addTech("ws");
        return neutral;
    }

    private int getNumberOfSOsInTheDeck() {
        return getSecretObjectives().size();
    }

    public String getEndedDateString() {
        return Helper.getDateRepresentation(getEndedDate());
    }

    public boolean hasBorderAnomalyOn(String tile, Integer direction) {
        return borderAnomalyManager.has(tile, direction);
    }

    public void addBorderAnomaly(String tile, Integer direction, BorderAnomalyModel.BorderAnomalyType anomalyType) {
        borderAnomalyManager.add(tile, direction, anomalyType);
    }

    public void removeBorderAnomaly(String tile, Integer direction) {
        borderAnomalyManager.remove(tile, direction);
    }

    public List<BorderAnomalyHolder> getBorderAnomalies() {
        return borderAnomalyManager.get();
    }

    public void setBorderAnomalies(List<BorderAnomalyHolder> anomalies) {
        borderAnomalyManager.set(anomalies);
    }

    private int getNumberOfSOsInPlayersHands() {
        int soNum = 0;
        for (Player player : players.values()) {
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
        Map<String, Object> returnValue = new HashMap<>();

        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getDeclaredAnnotation(ExportableField.class) != null) {
                try {
                    returnValue.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    // This shouldn't really happen since we can even see private fields.
                    BotLogger.error(new LogOrigin(this), "Unknown error exporting fields from Game.", e);
                }
            }
        }
        return returnValue;
    }

    public MiltyDraftManager getMiltyDraftManagerUnsafe() {
        return miltyDraftManager;
    }

    @NotNull
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

    // public void setMiltyDraftManager(MiltyDraftManager miltyDraftManager) {
    //     this.miltyDraftManager = miltyDraftManager;
    // }

    @NotNull
    public DraftTileManager getDraftTileManager() {
        if (draftTileManager == null) {
            draftTileManager = new DraftTileManager();
        }
        return draftTileManager;
    }

    public DraftManager getDraftManagerUnsafe() {
        return draftManager;
    }

    public void clearAllDraftInfo() {
        draftManager = null;
        draftString = null;
        draftSystemSettings = null;
        draftSystemSettingsJson = null;
    }

    @NotNull
    public DraftManager getDraftManager() {
        if (draftManager != null) {
            return draftManager;
        }
        if (draftString != null) {
            try {
                draftManager = DraftLoadService.loadDraftManager(this, draftString);
            } catch (Exception e) {
                String sb = "Failed to load draft manager (and creating an empty new one instead): " + e.getMessage()
                        + System.lineSeparator()
                        + "With draft data: "
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), draftString);

                BotLogger.warning(new LogOrigin(this), sb, e);
                draftManager = new DraftManager(this);
            }
        } else {
            draftManager = new DraftManager(this);
        }
        return draftManager;
    }

    public DistanceTool getDistanceTool() {
        if (distanceTool != null) {
            return distanceTool;
        }
        if (getMapTemplateID() == null) {
            BotLogger.warning(new LogOrigin(this), "Map template ID is null, distance tool cannot be created.");
            return null;
        }
        distanceTool = new DistanceTool(this);
        return distanceTool;
    }

    @Nullable
    public MiltySettings getMiltySettingsUnsafe() {
        return miltySettings;
    }

    public MiltySettings initializeMiltySettings() {
        if (miltySettings == null) {
            if (miltyJson != null) {
                try {
                    JsonNode json = mapper.readTree(miltyJson);
                    miltySettings = new MiltySettings(this, json);
                } catch (Exception e) {
                    BotLogger.error(
                            new LogOrigin(this),
                            "Failed loading milty draft settings for `" + getName() + "` " + Constants.jazzPing(),
                            e);
                    MessageHelper.sendMessageToChannel(
                            getActionsChannel(), "Milty draft settings failed to load. Resetting to default.");
                    miltySettings = new MiltySettings(this, null);
                }
            } else {
                miltySettings = new MiltySettings(this, null);
            }
        }
        return miltySettings;
    }

    public DraftSystemSettings getDraftSystemSettingsUnsafe() {
        return draftSystemSettings;
    }

    public DraftSystemSettings initializeDraftSystemSettings() {
        if (draftSystemSettings == null) {
            if (draftSystemSettingsJson != null) {
                try {
                    JsonNode json = mapper.readTree(draftSystemSettingsJson);
                    draftSystemSettings = new DraftSystemSettings(this, json);
                } catch (Exception e) {
                    BotLogger.error(
                            new LogOrigin(this),
                            "Failed loading draft system settings for `" + getName() + "` "
                                    + Constants.jabberwockyPing(),
                            e);
                    MessageHelper.sendMessageToChannel(
                            getActionsChannel(), "Draft system settings failed to load. Resetting to default.");
                    draftSystemSettings = new DraftSystemSettings(this, null);
                }
            } else {
                draftSystemSettings = new DraftSystemSettings(this, null);
            }
        }
        return draftSystemSettings;
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

    public int getFrankenBagSize() {
        int size = 0;
        boolean overRodeNormal = false;
        Iterable<DraftItem.Category> categories = new ArrayList<>(EnumSet.allOf(DraftItem.Category.class));
        for (DraftItem.Category category : categories) {
            if (!getStoredValue("frankenLimit" + category.toString()).isEmpty()) {
                overRodeNormal = true;
            }
            size += FrankenDraft.getItemLimitForCategory(category, this);
        }
        if (overRodeNormal) {
            return size;
        } else {
            return activeDraft.getBagSize();
        }
    }

    public void setBagDraft(BagDraft draft) {
        activeDraft = draft;
    }

    public void setupTwilightsFallMode(GenericInteractionCreateEvent event) {
        setTwilightsFallMode(true);
        setThundersEdge(false);
        validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_twilights_fall"));
        validateAndSetRelicDeck(Mapper.getDeck("relics_pok_te"));
        setStrategyCardSet("twilights_fall_sc");
        removeSOFromGame("baf");
        removeSOFromGame("dp");
        removeSOFromGame("dtd");
        removeSOFromGame("sb"); // get this stuff too
        removeRelicFromGame("quantumcore");
        removeRelicFromGame("mawofworlds");
        removeRelicFromGame("prophetstears");
        validateAndSetActionCardDeck(event, Mapper.getDeck("tf_action_deck"));
        setTechnologyDeckID("techs_tf");
    }

    public void addActionCardDuplicates(List<String> acIDs) {
        getActionCards().addAll(acIDs);
        Collections.shuffle(getActionCards());
    }

    public void setPurgedPNs(List<String> purgedPN) {
        this.purgedPN = purgedPN;
    }

    public List<String> getPurgedPN() {
        return purgedPN;
    }

    public boolean hasWinner() {
        return getWinner().isPresent();
    }

    public Optional<Player> getWinner() {
        Player winner = null;
        for (Player player : getRealPlayersNDummies()) {
            if (!meetsVictoryRequirement(player)) {
                continue;
            }
            if (winner == null) {
                winner = player;
                continue;
            }

            Player candidate = compareWinners(player, winner);
            if (candidate == null) {
                return Optional.empty();
            }
            winner = candidate;
        }

        if (winner == null && isOmegaPhaseMode() && revealedPublicObjectives.containsKey(Constants.IMPERIUM_REX_ID)) {
            winner = getOmegaPhaseWinner().orElse(null);
        }
        return Optional.ofNullable(winner);
    }

    public List<Player> getWinners() {
        Optional<Player> winnerOptional = getWinner();
        if (winnerOptional.isEmpty()) {
            return Collections.emptyList();
        }

        Player winner = winnerOptional.get();
        List<Player> winners = new ArrayList<>();
        winners.add(winner);

        if (winner.getAllianceMembers() != null) {
            String faction = winner.getFaction();
            getRealPlayers().stream()
                    .filter(p -> p.getAllianceMembers() != null
                            && p.getAllianceMembers().contains(faction))
                    .forEach(winners::add);
        }

        return winners;
    }

    private boolean meetsVictoryRequirement(Player player) {
        if (player.getTotalVictoryPoints() < getVp()) {
            return false;
        }

        if (!isLiberationC4Mode()) {
            return true;
        }

        if (player.getAllianceMembers().isEmpty()) {
            return true;
        }

        Player ally = getRealPlayersNDummies().stream()
                .filter(p -> p != player && p.getAllianceMembers().contains(player.getFaction()))
                .findFirst()
                .orElse(null);

        return ally != null
                && ally.getTotalVictoryPoints() >= getVp()
                && (player.getTotalVictoryPoints() >= 12 || ally.getTotalVictoryPoints() >= 12);
    }

    private Player compareWinners(Player contender, Player current) {

        if (isCivilizedSocietyMode()) {
            if (contender.getTotalVictoryPoints() > current.getTotalVictoryPoints()) {
                return contender;
            }
            if (contender.getTotalVictoryPoints() == current.getTotalVictoryPoints()) {
                if (Helper.getPlayerInfluenceTotal(current, this) + current.getTg()
                        < Helper.getPlayerInfluenceTotal(contender, this) + contender.getTg()) {
                    return contender;
                } else {
                    return current;
                }
            }
            return current;
        }
        if (hasFullPriorityTrackMode()) {
            if (contender.hasPriorityPosition() && !current.hasPriorityPosition()) {
                return contender;
            }
            if (contender.hasPriorityPosition() && current.hasPriorityPosition()) {
                return contender.getPriorityPosition() < current.getPriorityPosition() ? contender : current;
            }
            return current;
        }
        if (isNotEmpty(contender.getSCs()) && isNotEmpty(current.getSCs())) {
            return getLowestInitiativePlayer(contender, current);
        }
        return null; // not enough info to break tie
    }

    private Optional<Player> getOmegaPhaseWinner() {
        return getRealPlayersNDummies().stream()
                .filter(Player::hasPriorityPosition)
                .reduce((p1, p2) -> {
                    if (p1.getTotalVictoryPoints() != p2.getTotalVictoryPoints()) {
                        return p1.getTotalVictoryPoints() > p2.getTotalVictoryPoints() ? p1 : p2;
                    }
                    return p1.getPriorityPosition() < p2.getPriorityPosition() ? p1 : p2;
                });
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
        return slashCommandsUsed.values().stream().mapToInt(Integer::intValue).sum();
    }

    // This is presently only used to determine if an AC is NOT playable.
    // Therefore, the method name is now inaccurate
    public boolean isACInDiscard(String name) {
        for (String ac : getDiscardActionCards().keySet()) {
            ACStatus status = getDiscardACStatus().get(ac);
            ActionCardModel acModel = Mapper.getActionCard(ac);
            if (acModel.getName().contains(name)) {
                // true = it cannot be played, false = it's on garbozia
                return Arrays.asList(null, ACStatus.ralnelbt, ACStatus.purged).contains(status);
            }
        }
        return false;
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
        if (isAbsolMode()
                || isMiltyModMode()
                || isDiscordantStarsMode()
                || isHomebrewSCMode()
                || isFowMode()
                || isAllianceMode()
                || isCommunityMode()) competitiveTIGLGame = false;
        super.setCompetitiveTIGLGame(competitiveTIGLGame);
    }

    @Override
    public boolean isAllianceMode() {
        for (Player player : getRealPlayers()) {
            if (player.getAllianceMembers() != null
                    && !player.getAllianceMembers()
                            .replace(player.getFaction(), "")
                            .isEmpty()) {
                setAllianceMode(true);
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

    public String getGameModesText() {
        boolean isNormalGame = isNormalGame();
        Map<String, Boolean> gameModes = new HashMap<>();
        gameModes.put(SourceEmojis.TI4PoK + "Normal", isNormalGame);
        gameModes.put(SourceEmojis.TI4BaseGame + "Base Game", isBaseGameMode());
        gameModes.put("Prophecy of Kings", isProphecyOfKings());
        gameModes.put("Thunder's Edge", isThundersEdge());
        gameModes.put("Twilight's Fall", isTwilightsFallMode());

        gameModes.put("Minor Factions", isMinorFactionsMode());
        gameModes.put("Age of Exploration", isAgeOfExplorationMode());
        gameModes.put("Hidden Agenda", isHiddenAgendaMode());
        gameModes.put("Total War", isTotalWarMode());
        gameModes.put("Dangerous Wilds", isDangerousWildsMode());
        gameModes.put("Stellar Atomics", isStellarAtomicsMode());
        gameModes.put("Civilized Society", isCivilizedSocietyMode());
        gameModes.put("Age Of Fighters", isAgeOfFightersMode());
        gameModes.put("Advent of the Warsun", isAdventOfTheWarsunMode());
        gameModes.put("Cultural Exchange Program", isCulturalExchangeProgramMode());
        gameModes.put("Conventions of War Abandoned", isConventionsOfWarAbandonedMode());
        gameModes.put("Rapid Mobilization", isRapidMobilizationMode());
        gameModes.put("Monuments to the Ages", isMonumentToTheAgesMode());
        gameModes.put("Weird Wormholes", isWeirdWormholesMode());
        gameModes.put("Cosmic Phenomenae", isCosmicPhenomenaeMode());
        gameModes.put("Wild wild Galaxy", isWildWildGalaxyMode());
        gameModes.put("Zealous Orthodoxy", isZealousOrthodoxyMode());
        gameModes.put("Mercenaries For Hire", isMercenariesForHireMode());
        gameModes.put("Age Of Commerce", isAgeOfCommerceMode());

        gameModes.put("Liberation", isLiberationC4Mode());
        gameModes.put("Ordinian", isOrdinianC1Mode());
        gameModes.put("Alliance", isAllianceMode());

        gameModes.put("No Support Swaps", isNoSwapMode());
        gameModes.put("Veiled Heart", isVeiledHeartMode());
        gameModes.put(MiscEmojis.TIGL + "TIGL", isCompetitiveTIGLGame());
        gameModes.put("Community", isCommunityMode());
        gameModes.put("FoW", isFowMode());

        gameModes.put("Homebrew", isHomebrew());
        gameModes.put(SourceEmojis.MiltyMod + "MiltyMod", isMiltyModMode());
        gameModes.put("Franken", isFrankenGame());
        gameModes.put(SourceEmojis.Absol + "Absol", isAbsolMode());
        gameModes.put("VotC", isVotcMode());
        gameModes.put(SourceEmojis.DiscordantStars + "DiscordantStars", isDiscordantStarsMode());
        gameModes.put("HomebrewSC", isHomebrewSCMode());
        gameModes.put("AC Deck 2", isAcd2());
        gameModes.put("Omega Phase", isOmegaPhaseMode());
        gameModes.put("Priority Track", hasAnyPriorityTrackMode());

        for (String tag : getTags()) {
            gameModes.put(tag, true);
        }
        return gameModes.entrySet().stream()
                .filter(Entry::getValue)
                .map(Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    public boolean isAcd2() {
        return getAcDeckID().startsWith("action_deck_2");
    }

    public boolean isNormalGame() {
        return !hasHomebrew()
                && !isMinorFactionsMode()
                && !isAgeOfExplorationMode()
                && !isHiddenAgendaMode()
                && !isTotalWarMode()
                && !isDangerousWildsMode()
                && !isStellarAtomicsMode()
                && !isCivilizedSocietyMode()
                && !isAgeOfFightersMode()
                && !isAdventOfTheWarsunMode()
                && !isCulturalExchangeProgramMode()
                && !isConventionsOfWarAbandonedMode()
                && !isRapidMobilizationMode()
                && !isMonumentToTheAgesMode()
                && !isWeirdWormholesMode()
                && !isCosmicPhenomenaeMode()
                && !isWildWildGalaxyMode()
                && !isZealousOrthodoxyMode()
                && !isMercenariesForHireMode()
                && !isAgeOfCommerceMode()
                && !isLiberationC4Mode()
                && !isOrdinianC1Mode()
                && !isAllianceMode()
                && !isTwilightsFallMode();
    }

    public boolean isFrankenGame() {
        return getRealPlayers().stream()
                .anyMatch(p -> p.getFaction().toLowerCase().contains("franken"));
    }

    public String gameJumpLinks() {
        return String.format("%s %s %s", getName(), getTabletalkJumpLinkFormatted(), getActionsJumpLinkFormatted());
    }

    public String getTabletalkJumpLink() {
        TextChannel tt = getTableTalkChannel();
        if (tt == null) return null;
        return tt.getJumpUrl();
    }

    private String getTabletalkJumpLinkFormatted() {
        TextChannel tt = getTableTalkChannel();
        if (tt == null) return "[no tt]";
        return String.format("[__[Tabletalk](%s)__]", tt.getJumpUrl());
    }

    public String getActionsJumpLink() {
        TextChannel act = getActionsChannel();
        if (act == null) return null;
        return act.getJumpUrl();
    }

    private String getActionsJumpLinkFormatted() {
        TextChannel act = getActionsChannel();
        if (act == null) return "[no actions]";
        return String.format("[__[Actions](%s)__]", act.getJumpUrl());
    }

    @Nullable
    public TextChannel getTableTalkChannel() {
        try {
            return JdaService.jda.getTextChannelById(getTableTalkChannelID());
        } catch (Exception e) {
            TextChannel tableTalkChannel;
            List<TextChannel> gameChannels = JdaService.jda.getTextChannels().stream()
                    .filter(c -> c.getName().startsWith(getName()))
                    .filter(not(c -> c.getName().contains(Constants.ACTIONS_CHANNEL_SUFFIX)))
                    .toList();
            if (gameChannels.size() == 1) {
                tableTalkChannel = gameChannels.getFirst();
                setTableTalkChannelID(tableTalkChannel.getId());
                return tableTalkChannel;
            }
        }
        return null;
    }

    public TextChannel getMainGameChannel() {
        try {
            return JdaService.jda.getTextChannelById(getMainChannelID());
        } catch (Exception e) {
            List<TextChannel> gameChannels =
                    JdaService.jda.getTextChannelsByName(getName() + Constants.ACTIONS_CHANNEL_SUFFIX, true);
            if (gameChannels.size() == 1) {
                TextChannel mainGameChannel = gameChannels.getFirst();
                setMainChannelID(mainGameChannel.getId());
                return mainGameChannel;
            }
            // BotLogger.log("Could not retrieve MainGameChannel for " + getName(), e);
        }
        return null;
    }

    public TextChannel getSavedChannel() {
        try {
            return JdaService.jda.getTextChannelById(getSavedChannelID());
        } catch (Exception e) {
            return getMainGameChannel();
        }
    }

    public TextChannel getActionsChannel() {
        return getMainGameChannel();
    }

    public ThreadChannel getBotMapUpdatesThread() {
        if (isFowMode()) {
            return null;
        }

        // FIND BY ID
        if (StringUtils.isNumeric(getBotMapUpdatesThreadID())) {
            ThreadChannel threadChannel = JdaService.jda.getThreadChannelById(getBotMapUpdatesThreadID());
            if (threadChannel != null) {
                return threadChannel;
            }
        }

        // FIND BY NAME
        List<ThreadChannel> botChannels =
                JdaService.jda.getThreadChannelsByName(getName() + Constants.BOT_CHANNEL_SUFFIX, true);
        if (botChannels.size() == 1) {
            return botChannels.getFirst();
        } else if (botChannels.size() > 1) {
            BotLogger.warning(
                    new LogOrigin(this),
                    getName() + " appears to have more than one bot-map-updates channel:\n"
                            + botChannels.stream()
                                    .map(ThreadChannel::getJumpUrl)
                                    .collect(Collectors.joining("\n")));
            return botChannels.getFirst();
        }

        // CHECK IF ARCHIVED
        if (getActionsChannel() == null) {
            return null;
        }
        for (ThreadChannel archivedChannel : getActionsChannel().retrieveArchivedPublicThreadChannels()) {
            if (archivedChannel.getId().equals(getBotMapUpdatesThreadID())
                    || archivedChannel.getName().equals(getName() + Constants.BOT_CHANNEL_SUFFIX)) {
                setBotMapUpdatesThreadID(archivedChannel.getId());
                return archivedChannel;
            }
        }
        setBotMapUpdatesThreadID(null);
        return null;
    }

    public ThreadChannel getLaunchPostThread() {
        if (StringUtils.isNumeric(getLaunchPostThreadID())) {
            return JdaService.guildPrimary.getThreadChannelById(getLaunchPostThreadID());
        }
        return null;
    }

    /**
     * @return Guild that the ActionsChannel or MainGameChannel resides
     */
    @Nullable
    public Guild getGuild() {
        return getActionsChannel() == null ? null : getActionsChannel().getGuild();
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

    public Map<String, String> getMessagesThatICheckedForAllReacts() {
        return checkingForAllReacts;
    }

    private String getFactionsThatReactedToThis(String messageID) {
        if (checkingForAllReacts.get(messageID) != null) {
            return checkingForAllReacts.get(messageID);
        }
        return "";
    }

    public void clearAllEmptyStoredValues() {
        // Remove the entry if the value is empty
        checkingForAllReacts
                .entrySet()
                .removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    }

    public void setStoredValue(String key, String value) {
        if (value == null) return;
        value = StringHelper.escape(value);
        checkingForAllReacts.put(key, value);
    }

    public int changeCommsOnPlanet(int change, String planet) {
        int amountRemaining = 0;
        if (!getStoredValue("CommsOnPlanet" + planet).isEmpty()) {
            int thereAlready = Integer.parseInt(getStoredValue("CommsOnPlanet" + planet));
            change += thereAlready;
        }
        if (change > 0) {
            amountRemaining = change;
            setStoredValue("CommsOnPlanet" + planet, "" + change);
        } else {
            removeStoredValue("CommsOnPlanet" + planet);
        }
        return amountRemaining;
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

    public Set<Integer> getPlayedSCs() {
        return scPlayed.entrySet().stream()
                .filter(Entry::getValue)
                .map(Entry::getKey)
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
            if (player.hasAbility("patience")) {
                playerSC = 9;
            }
        }
        for (int sc : orderedSCsBasic) {
            Player holder = getPlayerFromSC(sc);
            String scT = sc + "";
            int judger = sc;
            if (holder != null && !scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
                judger = 0;
                if (player.hasAbility("patience")) {
                    judger = 9;
                }
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
                if (player.hasAbility("patience")) {
                    judger = 9;
                }
            }
            if (judger == 0 || (player.hasAbility("patience") && judger == 9)) {
                orderedSCs.add(sc);
            }
        }
        for (int sc : orderedSCsBasic) {
            Player holder = getPlayerFromSC(sc);
            String scT = sc + "";
            int judger = sc;
            if (holder != null && !scT.equalsIgnoreCase(getSCNumberIfNaaluInPlay(holder, scT))) {
                judger = 0;
                if (player.hasAbility("patience")) {
                    judger = 9;
                }
            }
            if (judger < playerSC && judger != 0 && (!player.hasAbility("patience") || judger != 9)) {
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
        if (tileMap.isEmpty()) {
            return 0;
        }
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

    public Player getActivePlayer() {
        return getPlayer(getActivePlayerID());
    }

    public Player getSpeaker() {
        return getPlayer(getSpeakerUserID());
    }

    public Player getPlanetOwner(String planet) {
        for (Player player : getRealPlayers()) {
            if (player.getPlanets().contains(planet)) {
                return player;
            }
        }
        return null;
    }

    public void setTyrant(Player speaker) {
        setTyrantUserID(speaker.getUserID());
    }

    public Player getTyrant() {
        return getPlayer(getTyrantUserID());
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

    @Override
    public boolean isHomebrewSCMode() {
        return !"pok".equals(getScSetID())
                && !"base_game".equals(getScSetID())
                && !"te".equals(getScSetID())
                && !"twilights_fall_sc".equals(getScSetID());
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
        String prevFaction =
                (prevPlayer != null && prevPlayer.getFaction() != null) ? prevPlayer.getFaction() : "jazzwuzhere&p1too";
        long elapsedTime = newTime.getTime() - lastActivePlayerChange.getTime();
        if (lastActivePlayerChange.getTime() < 1000000) {
            elapsedTime = 60000; // if for some reason the last Active player change was never set, ignore the time
        }
        if (prevPlayer != null) {
            if (!factionsInCombat.contains(prevFaction) && !isTemporaryPingDisable()) {
                prevPlayer.updateTurnStats(elapsedTime);
            } else {
                prevPlayer.updateTurnStatsWithAverage(elapsedTime);
            }
        }

        setStoredValue("factionsInCombat", "");
        setTemporaryPingDisable(false);
        // reset timers for ping and stats
        setActivePlayerID(player == null ? null : player.getUserID());
        lastActivePlayerChange = newTime;
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

    private void setSentAgenda(String id) {
        Collection<Integer> values = sentAgendas.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        sentAgendas.put(id, identifier);
    }

    private int addDiscardAgenda(String id) {
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

    private void addRevealedPublicObjective(String id) {
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
        return strategyCardManager.getTradeGoodCounts();
    }

    public void setScTradeGoods(Map<Integer, Integer> strategyCardToTradeGoodCount) {
        strategyCardManager.setTradeGoodCounts(strategyCardToTradeGoodCount);
    }

    public void setScTradeGood(Integer sc, Integer tradeGoodCount) {
        strategyCardManager.setTradeGoodCount(sc, tradeGoodCount);
    }

    public void incrementScTradeGoods() {
        strategyCardManager.incrementTradeGoods();
    }

    public boolean addSC(Integer sc) {
        return strategyCardManager.add(sc);
    }

    public boolean removeSC(Integer sc) {
        return strategyCardManager.remove(sc);
    }

    public List<Integer> getSCList() {
        return strategyCardManager.list();
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

    public List<String> getPublicObjectives1Peekable() {
        return publicObjectives1Peekable;
    }

    public List<String> getPublicObjectives2() {
        return publicObjectives2;
    }

    public List<String> getPublicObjectives2Peekable() {
        return publicObjectives2Peekable;
    }

    public Map.Entry<String, Integer> revealStage1() {
        if (publicObjectives1Peekable.isEmpty() || getPhaseOfGame().contains("agenda")) {
            return revealNextPublicObjective(publicObjectives1);
        } else {
            return revealNextPublicObjective(publicObjectives1Peekable);
        }
    }

    public Map.Entry<String, Integer> revealStage2() {
        if (publicObjectives2Peekable.isEmpty() || getPhaseOfGame().contains("agenda")) {
            return revealNextPublicObjective(publicObjectives2);
        } else {
            return revealNextPublicObjective(publicObjectives2Peekable);
        }
    }

    public Map.Entry<String, Integer> revealStage2Random() {
        Collections.shuffle(publicObjectives2);
        return revealNextPublicObjective(publicObjectives2);
    }

    public Map.Entry<String, Integer> revealStage1Random() {
        Collections.shuffle(publicObjectives1);
        return revealNextPublicObjective(publicObjectives1);
    }

    public void shuffleInBottomObjective(String cardIdToShuffle, int sizeOfBottom, int type) {
        List<String> objectiveList = type == 1 ? publicObjectives1Peekable : publicObjectives2Peekable;
        if (objectiveList.size() + 1 < sizeOfBottom) {
            throw new IllegalArgumentException(
                    "Cannot shuffle in bottom objective, size of bottom exceeds new size of deck.");
        }
        if (sizeOfBottom < 1) {
            throw new IllegalArgumentException("Size of bottom must be greater than 0.");
        }
        var insertPositionFromEnd = ThreadLocalRandom.current().nextInt(sizeOfBottom);
        var insertPosition = objectiveList.size() - insertPositionFromEnd;
        objectiveList.add(insertPosition, cardIdToShuffle);
    }

    public void setUpPeekableObjectives(int num, int type) {
        if (type == 1) {
            var maxSize = publicObjectives1.size() + publicObjectives1Peekable.size();
            if (num > maxSize) {
                num = maxSize;
            }
            while (publicObjectives1Peekable.size() != num) {
                if (publicObjectives1Peekable.size() > num) {
                    String id = publicObjectives1Peekable.removeLast();
                    publicObjectives1.add(id);
                    Collections.shuffle(publicObjectives1);
                } else {
                    Collections.shuffle(publicObjectives1);
                    String id = publicObjectives1.getFirst();
                    publicObjectives1.remove(id);
                    publicObjectives1Peekable.add(id);
                }
            }
        } else {
            var maxSize = publicObjectives2.size() + publicObjectives2Peekable.size();
            if (num > maxSize) {
                num = maxSize;
            }
            while (publicObjectives2Peekable.size() != num) {
                if (publicObjectives2Peekable.size() > num) {
                    String id = publicObjectives2Peekable.removeLast();
                    publicObjectives2.add(id);
                    Collections.shuffle(publicObjectives2);
                } else {
                    Collections.shuffle(publicObjectives2);
                    String id = publicObjectives2.getFirst();
                    publicObjectives2.remove(id);
                    publicObjectives2Peekable.add(id);
                }
            }
        }
    }

    public String peekAtStage1(int place, Player player) {
        String objective = peekAtObjective(publicObjectives1Peekable, place);

        if (publicObjectives1Peeked.containsKey(objective)
                && !publicObjectives1Peeked.get(objective).contains(player.getUserID())) {
            publicObjectives1Peeked.get(objective).add(player.getUserID());
        } else {
            List<String> list = new ArrayList<>();
            list.add(player.getUserID());
            publicObjectives1Peeked.put(objective, list);
        }

        return objective;
    }

    public String peekAtStage2(int place, Player player) {
        String objective = peekAtObjective(publicObjectives2Peekable, place);

        if (publicObjectives2Peeked.containsKey(objective)
                && !publicObjectives2Peeked.get(objective).contains(player.getUserID())) {
            publicObjectives2Peeked.get(objective).add(player.getUserID());
        } else {
            List<String> list = new ArrayList<>();
            list.add(player.getUserID());
            publicObjectives2Peeked.put(objective, list);
        }

        return objective;
    }

    public boolean revealSpecificStage1(String id) {
        return revealPublicObjective(publicObjectives1, id) != null
                || revealPublicObjective(publicObjectives1Peekable, id) != null;
    }

    public boolean revealSpecificStage2(String id) {
        return revealPublicObjective(publicObjectives2, id) != null
                || revealPublicObjective(publicObjectives2Peekable, id) != null;
    }

    public void swapStage1(int place1, int place2) {
        swapObjective(publicObjectives1Peekable, place1, place2);
    }

    public void swapStage2(int place1, int place2) {
        swapObjective(publicObjectives2Peekable, place1, place2);
    }

    private void swapObjective(List<String> objectiveList, int place1, int place2) {
        if (objectiveList.isEmpty()) return;
        place1 -= 1;
        place2 -= 1;
        String id = objectiveList.get(place1);
        String id2 = objectiveList.get(place2);
        objectiveList.set(place1, id2);
        objectiveList.set(place2, id);
    }

    public void swapPublicObjectiveOut(int stage1Or2, int place, String id) {
        if (stage1Or2 == 1) {
            String removed = publicObjectives1Peekable.remove(place);
            publicObjectives1Peekable.add(place, id);
            addPublicObjectiveToDeck(removed);
        } else {
            String removed = publicObjectives2Peekable.remove(place);
            publicObjectives2Peekable.add(place, id);
            addPublicObjectiveToDeck(removed);
        }
    }

    private String peekAtObjective(List<String> objectiveList, int place) {
        if (objectiveList.isEmpty()) return null;
        place -= 1;
        return objectiveList.get(place);
    }

    public String getTopPublicObjective(int stage1Or2) {
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

    private void addPublicObjectiveToDeck(String id) {
        PublicObjectiveModel obj = Mapper.getPublicObjective(id);
        if (obj == null) return;
        if (obj.getPoints() == 1) {
            publicObjectives1.add(id);
            Collections.shuffle(publicObjectives1);
        } else {
            publicObjectives2.add(id);
            Collections.shuffle(publicObjectives2);
        }
    }

    private Entry<String, Integer> revealNextPublicObjective(List<String> objectives) {
        if (objectives.isEmpty()) return null;
        String id = objectives.getFirst();
        return revealPublicObjective(objectives, id);
    }

    private Entry<String, Integer> revealPublicObjective(Collection<String> objectives, String objective) {
        boolean removedFromDeck = objectives.remove(objective);
        if (!removedFromDeck) return null;

        addRevealedPublicObjective(objective);
        for (Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
            if (entry.getKey().equals(objective)) {
                return entry;
            }
        }
        return null;
    }

    public Entry<String, Integer> revealSecretObjective() {
        Collections.shuffle(getSecretObjectives());
        String id = getSecretObjectives().getFirst();
        removeSOFromGame(id);
        addToSoToPoList(id);
        addCustomPO(Mapper.getSecretObjectivesJustNames().get(id), 1);
        for (Entry<String, Integer> entry : revealedPublicObjectives.entrySet()) {
            if (entry.getKey().equals(Mapper.getSecretObjectivesJustNames().get(id))) {
                return entry;
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
        if (id.isEmpty()) return false;

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

    public void shuffleObjectiveDeck(int stage) {
        if (stage == 1) {
            Collections.shuffle(publicObjectives1);
        } else if (stage == 2) {
            Collections.shuffle(publicObjectives2);
        }
    }

    public void removeRevealedObjective(String id) {
        revealedPublicObjectives.remove(id);
        soToPoList.remove(id);
        customPublicVP.remove(id);
        scoredPublicObjectives.remove(id);
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

    public boolean doesSomeoneControlRex() {
        boolean custodiansTaken = false;
        for (Player p : getRealPlayersNDummies()) {
            if (p.controlsMecatol(false)) {
                return true;
            }
        }
        return custodiansTaken;
    }

    public boolean isCustodiansScored() {
        boolean custodiansTaken = false;
        if (isOrdinianC1Mode()) {
            return ButtonHelper.isCoatlHealed(this);
        }
        if (isTwilightsFallMode()) {
            return getTyrant() != null;
        }
        if (isLiberationC4Mode()) {
            return true;
        }
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

        for (Player p : getRealPlayersNDummies()) {
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
            if (!Constants.CUSTODIAN.equals(id)
                    && !Constants.IMPERIAL_RIDER.equals(id)
                    && scoredPlayerList.contains(userID)) {
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

    public boolean unscorePublicObjective(String userID, Integer idNumber) {
        String id = "";
        for (Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(idNumber)) {
                id = po.getKey();
                break;
            }
        }

        return unscorePublicObjective(userID, id);
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

    public boolean removeRelicFromGame(String id) {
        return relics.remove(id);
    }

    public void addRelicToGame(String id) {
        relics.add(id);
        Collections.shuffle(relics);
    }

    public boolean removePOFromGame(String id) {
        if (publicObjectives1.remove(id)) return true;
        if (publicObjectives2.remove(id)) return true;
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

    public void setPublicObjectives1Peekable(List<String> publicObjectives1) {
        publicObjectives1Peekable = publicObjectives1;
    }

    public void setPublicObjectives2Peekable(List<String> publicObjectives2) {
        publicObjectives2Peekable = publicObjectives2;
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

    public Map<Pair<String, Integer>, String> getAdjacentTileOverrides() {
        return adjacencyOverrides;
    }

    public void addAdjacentTileOverride(String primaryTile, int direction, String secondaryTile) {
        Pair<String, Integer> primary = new ImmutablePair<>(primaryTile, direction);
        Pair<String, Integer> secondary = new ImmutablePair<>(secondaryTile, (direction + 3) % 6);

        adjacencyOverrides.put(primary, secondaryTile);
        adjacencyOverrides.put(secondary, primaryTile);
    }

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
        discardAgendas = new LinkedHashMap<>();
    }

    public void resetEvents() {
        if (Mapper.getDeck(getEventDeckID()) == null) return;
        setEvents(Mapper.getShuffledDeck(getEventDeckID()));
        discardedEvents = new LinkedHashMap<>();
    }

    public void resetDrawStateAgendas() {
        sentAgendas.clear();
    }

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
            if (laws.size() > 2) {
                for (Player p : getRealPlayers()) {
                    if (p.getSecretsUnscored().containsKey("dp")) {
                        MessageHelper.sendMessageToChannel(
                                p.getCardsInfoThread(),
                                p.getRepresentationUnfogged()
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
                    if (IsPlayerElectedService.isPlayerElected(this, p2, id)) {
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
        if (Constants.VOICE_OF_THE_COUNCIL_ID.equalsIgnoreCase(id)) {
            VoiceOfTheCouncilHelper.ResetVoiceOfTheCouncil(this);
            return true;
        }
        if ("warrant".equalsIgnoreCase(id)) {
            for (Player p2 : getRealPlayers()) {
                if (IsPlayerElectedService.isPlayerElected(this, p2, id)) {
                    p2.setSearchWarrant(false);
                }
            }
        }
        if ("censure".equalsIgnoreCase(id)) {
            Map<String, Integer> customPOs = new HashMap<>(revealedPublicObjectives);
            for (Entry<String, Integer> entry : customPOs.entrySet()) {
                if (entry.getKey().toLowerCase().contains("political censure")) {
                    removeCustomPO(entry.getValue());
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
            if (Constants.VOICE_OF_THE_COUNCIL_ID.equalsIgnoreCase(id)) {
                VoiceOfTheCouncilHelper.ResetVoiceOfTheCouncil(this);
                return true;
            }
            if ("warrant".equalsIgnoreCase(id)) {
                for (Player p2 : getRealPlayers()) {
                    if (IsPlayerElectedService.isPlayerElected(this, p2, id)) {
                        p2.setSearchWarrant(false);
                    }
                }
            }
            if ("censure".equalsIgnoreCase(id)) {
                if (customPublicVP.get("Political Censure") != null) {
                    Map<String, Integer> customPOs = new HashMap<>(revealedPublicObjectives);
                    for (Entry<String, Integer> entry : customPOs.entrySet()) {
                        if (entry.getKey().toLowerCase().contains("political censure")) {
                            removeCustomPO(entry.getValue());
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

    public boolean putAgendaBottom(String id) {
        if (!id.isEmpty()) {
            getAgendas().remove(id);
            getAgendas().add(id);
            sentAgendas.remove(id);
            return true;
        }
        return false;
    }

    public boolean putExploreBottom(String id) {
        if (!id.isEmpty()) {
            explore.remove(id);
            explore.add(id);
            return true;
        }
        return false;
    }

    public boolean putRelicBottom(String id) {
        if (!id.isEmpty()) {
            relics.remove(id);
            relics.add(id);
            return true;
        }
        return false;
    }

    public boolean putACBottom(String id) {
        if (!id.isEmpty()) {
            getActionCards().remove(id);
            getActionCards().add(id);
            return true;
        }
        return false;
    }

    public boolean putSOBottom(String id) {
        if (!id.isEmpty()) {
            getSecretObjectives().remove(id);
            getSecretObjectives().add(id);
            return true;
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
    private boolean reshuffleActionCardDiscard() {
        List<String> acsToShuffle = getDiscardActionCards().keySet().stream()
                .filter(ac -> getDiscardACStatus().get(ac) == null)
                .toList();

        if (acsToShuffle.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    getActionsChannel(), "Unable to reshuffle the action card deck because the discard pile is empty.");
            return false;
        }

        getActionCards().addAll(acsToShuffle);
        String names = acsToShuffle.stream()
                .map(ac -> Mapper.getActionCard(ac).getName())
                .collect(Collectors.joining("\n"));
        Collections.shuffle(getActionCards());
        acsToShuffle.forEach(ac -> getDiscardActionCards().remove(ac)); // clear out the shuffled back cards
        acsToShuffle.forEach(ac -> getDiscardACStatus().remove(ac)); // just in case
        String msg = "# " + getPing()
                + ", the action card deck has run out of cards, and so the discard pile has been shuffled to form a new action card deck.";
        if (!isFowMode()) {
            msg += "The shuffled cards are:\n" + names;
        }
        MessageHelper.sendMessageToChannel(getMainGameChannel(), msg);
        return true;
    }

    @NotNull
    public Map<String, Integer> drawActionCard(String userID) {
        Player player = getPlayer(userID);
        return drawActionCard(player);
    }

    @NotNull
    public Map<String, Integer> drawActionCard(Player player) {
        if (!getActionCards().isEmpty()) {
            String id = getActionCards().getFirst();
            if (player.hasAbility("deceive")) {
                ButtonHelperFactionSpecific.resolveDeceive(player, this);
            } else {
                getActionCards().remove(id);
                player.setActionCard(id);
            }
            return player.getActionCards();
        }

        boolean reshuffled = reshuffleActionCardDiscard();
        if (reshuffled) {
            return drawActionCard(player);
        }

        MessageHelper.sendMessageToChannel(
                getActionsChannel(), "Unable to draw an action card: both the deck and discard pile are empty.");
        return player.getActionCards();
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

    public List<String> getTechnologyDeck() {
        List<String> techDeck = Mapper.getDecks().get(getTechnologyDeckID()).getNewDeck();
        for (Player player : getRealPlayers()) {
            for (String tech : player.getFactionTechs()) {
                if (!techDeck.contains(tech)) {
                    techDeck.add(tech);
                }
            }
        }
        if (isTwilightsFallMode()) {
            techDeck.add("wavelength");
            techDeck.add("antimatter");
        }
        return techDeck;
    }

    public List<TechnologyModel> getPropulsionTechDeck() {
        return getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(TechnologyModel::isPropulsionTech)
                .sorted(TechnologyModel.sortByTechRequirements)
                .toList();
    }

    public List<TechnologyModel> getWarfareTechDeck() {
        return getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(TechnologyModel::isWarfareTech)
                .sorted(TechnologyModel.sortByTechRequirements)
                .toList();
    }

    public List<TechnologyModel> getCyberneticTechDeck() {
        return getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(TechnologyModel::isCyberneticTech)
                .sorted(TechnologyModel.sortByTechRequirements)
                .toList();
    }

    public List<TechnologyModel> getBioticTechDeck() {
        return getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(TechnologyModel::isBioticTech)
                .sorted(TechnologyModel.sortByTechRequirements)
                .toList();
    }

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
            BotLogger.warning(
                    new LogOrigin(this),
                    "Map: `" + getName() + "` MIGRATION CODE TRIGGERED: Explore " + reqType
                            + " deck was empty, shuffling discards into deck.");
        } // end of migration code

        if (!deck.isEmpty()) {
            String id = deck.getFirst();
            discardExplore(id);
            result = id;
        }

        // If deck is empty after draw, auto refresh deck from discard
        if (getExplores(reqType, explore).isEmpty()) {
            if ("pbd1000".equalsIgnoreCase(getName())) {
                resetExploresOfCertainType(reqType);
            } else {
                shuffleDiscardsIntoExploreDeck(reqType);
            }
        }
        return result;
    }

    private void shuffleDiscardsIntoExploreDeck(String reqType) {
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
        List<String> deck2 =
                new ArrayList<>(Mapper.getDecks().get(getExplorationDeckID()).getNewShuffledDeck());
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
        setActionCards(multiplyDeck(2, "action_cards_pok", "action_deck_2_pok"));
        setSecretObjectives(multiplyDeck(3, "pbd100_secret_objectives"));
    }

    public void triplicateACs() {
        setActionCards(multiplyDeck(3, "action_cards_pok"));
    }

    public void duplicateACs() {
        setActionCards(multiplyDeck(2, getAcDeckID()));
    }

    public void triplicateSOs() {
        setSecretObjectives(multiplyDeck(3, "secret_objectives_pok"));
    }

    private List<String> multiplyDeck(int totalCopies, String... deckIDs) {
        List<String> newDeck = Arrays.stream(deckIDs)
                .flatMap(deckID -> Mapper.getDecks().get(deckID).getNewDeck().stream())
                .toList();
        List<String> newDeck2 = new ArrayList<>(newDeck);
        for (String card : newDeck) for (int i = 1; i < totalCopies; i++) newDeck2.add(card + "extra" + i);
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
            setDiscardActionCard(id, null);
            return id;
        }

        boolean reshuffled = reshuffleActionCardDiscard();
        if (reshuffled) {
            return drawActionCardAndDiscard();
        }

        MessageHelper.sendMessageToChannel(
                getActionsChannel(), "Unable to draw an action card: both the deck and discard pile are empty.");
        return null;
    }

    public void checkSOLimit(Player player) {
        if (player.getSecretsScored().size() + player.getSecretsUnscored().size() > player.getMaxSOCount()
                && !player.getSecretsUnscored().isEmpty()) {
            String msg = player.getRepresentationUnfogged() + " you have more secret objectives than the limit ("
                    + player.getMaxSOCount()
                    + ") and should discard one. If your game is playing with a higher secret objective limit, you may change that in `/game setup`.";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            String secretScoreMsg = "Click a button below to discard your secret objective.";
            List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveDiscardButtons(player);
            if (!soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(), "Something went wrong. Please report to Fin.");
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
        boolean isRemoved = removeSOFromGame(soID);
        if (!isRemoved) {
            return null;
        }
        Player player = getPlayer(userID);
        if (player == null) {
            return null;
        }
        player.setSecret(soID);
        return player.getSecrets();
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

    private boolean shouldPutCardOnRalnel(Player discardingPlayer) {
        if (!"action".equals(getPhaseOfGame())) return false;
        for (Player p : getRealPlayers()) {
            if (p == discardingPlayer) continue;
            if (p.hasUnlockedBreakthrough("ralnelbt") && !p.isPassed()) return true;
        }
        return false;
    }

    private void setDiscardActionCard(String id, ACStatus status) {
        Collection<Integer> values = getDiscardActionCards().values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        getDiscardActionCards().put(id, identifier);
        if (status != null) getDiscardACStatus().put(id, status);
    }

    public void setPurgedActionCard(String id) {
        setDiscardActionCard(id, ACStatus.purged);
    }

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
                ACStatus status = shouldPutCardOnRalnel(player) ? ACStatus.ralnelbt : null;
                setDiscardActionCard(acID, status);
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
            if (player.getPlanets().contains("garbozia")) { // allow checking for garbozia
                for (Entry<String, Integer> ac : getDiscardActionCards().entrySet()) {
                    if (ac.getValue().equals(acIDNumber)
                            && getDiscardACStatus().get(ac.getKey()) == ACStatus.garbozia) {
                        acID = ac.getKey();
                        break;
                    }
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

    public Map<String, Integer> getPurgedActionCards() {
        return new HashMap<>(getDiscardActionCards().entrySet().stream()
                .filter(e -> getDiscardACStatus().get(e.getKey()) == ACStatus.purged)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
    }

    public boolean pickActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            String acID = "";
            for (Entry<String, Integer> ac : getDiscardActionCards().entrySet()) {
                ACStatus status = getDiscardACStatus().get(ac.getKey());
                if (ac.getValue().equals(acIDNumber) && status != ACStatus.purged) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                getDiscardActionCards().remove(acID);
                getDiscardACStatus().remove(acID);
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
            for (Map.Entry<String, Integer> ac : getDiscardActionCards().entrySet()) {
                ACStatus status = getDiscardACStatus().get(ac.getKey());
                if (ac.getValue().equals(acIDNumber) && status == ACStatus.purged) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                getDiscardActionCards().remove(acID);
                getDiscardACStatus().remove(acID);
                player.setActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public boolean shuffleActionCardBackIntoDeck(Integer acIDNumber) {
        String acID = "";
        for (Entry<String, Integer> ac : getDiscardActionCards().entrySet()) {
            if (ac.getValue().equals(acIDNumber)) {
                acID = ac.getKey();
                break;
            }
        }
        if (!acID.isEmpty()) {
            getDiscardActionCards().remove(acID);
            getDiscardACStatus().remove(acID);
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

    public boolean islandMode() {
        boolean otherThings =
                getName().contains("island") || (getMapTemplateID() != null && "1pIsland".equals(getMapTemplateID()));
        if (otherThings) setStoredValue("IslandMode", "true");
        return "true".equals(getStoredValue("IslandMode"));
    }

    public boolean loadGameSettingsFromSettings(GenericInteractionCreateEvent event, MiltySettings miltySettings) {
        SourceSettings sources = miltySettings.getSourceSettings();
        if (sources.getAbsol().isVal()) setAbsolMode(true);

        GameSettings settings = miltySettings.getGameSettings();
        setVp(settings.getPointTotal().getVal());

        if (getMaxSOCountPerPlayer() != 4) {
            setMaxSOCountPerPlayer(settings.getSecrets().getVal());
        }
        if (settings.getTigl().isVal()) {
            TIGLHelper.initializeTIGLGame(this, settings.getTiglFractured().isVal());
        }
        setAllianceMode(settings.getAlliance().isVal());

        if ("1pIsland".equals(settings.getMapTemplate().getValue().getAlias())) {
            setStoredValue("IslandMode", "true");
        }

        DeckSettings deckSettings = settings.getDecks();
        return validateAndSetAllDecks(
                event,
                deckSettings,
                settings.getStage1s().getVal(),
                settings.getStage2s().getVal());
    }

    public boolean loadGameSettingsFromSettings(
            GenericInteractionCreateEvent event, DraftSystemSettings draftSettings) {
        GameSetupSettings gameSetupSettings = draftSettings.getGameSetupSettings();
        SourceSettings sources = draftSettings.getSourceSettings();
        if (sources.getAbsol().isVal()) setAbsolMode(true);

        setVp(gameSetupSettings.getPointTotal().getVal());

        if (getMaxSOCountPerPlayer() != 4) {
            setMaxSOCountPerPlayer(gameSetupSettings.getSecrets().getVal());
        }
        if (gameSetupSettings.getTigl().isVal()) {
            TIGLHelper.initializeTIGLGame(
                    this, gameSetupSettings.getTiglFractured().isVal());
        }
        setAllianceMode(gameSetupSettings.getAlliance().isVal());

        // TODO
        // MiltySliceDraftableSettings miltySettings = draftSettings.getMiltySliceDraftableSettings();
        // if ("1pIsland".equals(miltySettings.getMapTemplate().getValue().getAlias())) {
        //     setStoredValue("IslandMode", "true");
        // }

        DeckSettings deckSettings = gameSetupSettings.getDecks();
        return validateAndSetAllDecks(
                event,
                deckSettings,
                gameSetupSettings.getStage1s().getVal(),
                gameSetupSettings.getStage2s().getVal());
    }

    private boolean validateAndSetAllDecks(
            GenericInteractionCreateEvent event, DeckSettings deckSettings, int stage1Count, int stage2Count) {
        boolean success = true;
        // &= is the "and operator". It will assign true to success iff success is true and the result is true.
        // Otherwise it will propagate a false value to the end
        success &= validateAndSetPublicObjectivesStage1Deck(
                event, deckSettings.getStage1().getValue());
        success &= validateAndSetPublicObjectivesStage2Deck(
                event, deckSettings.getStage2().getValue());
        success &= validateAndSetSecretObjectiveDeck(
                event, deckSettings.getSecrets().getValue());
        success &= validateAndSetActionCardDeck(
                event, deckSettings.getActionCards().getValue());
        success &= validateAndSetExploreDeck(event, deckSettings.getExplores().getValue());
        success &= validateAndSetTechnologyDeck(event, deckSettings.getTechs().getValue());
        setStrategyCardSet(deckSettings.getStratCards().getChosenKey());

        // Setup peakable objectives
        if (publicObjectives1Peekable.size() != 4) {
            if (isOmegaPhaseMode()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "This game is using Omega Phase, so the objective setup was ignored. If there's a problem with it, use `/omegaphase "
                                + Constants.RESET_OMEGA_PHASE_OBJECTIVES + "`");
            } else {
                setUpPeekableObjectives(stage1Count, 1);
                setUpPeekableObjectives(stage2Count, 2);
            }
        }

        if (isAbsolMode() && !deckSettings.getAgendas().getChosenKey().contains("absol")) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "This game seems to be using Absol mode, so the agenda deck you chose will be overridden.");
            success &= validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"));
        } else {
            success &= validateAndSetAgendaDeck(event, deckSettings.getAgendas().getValue());
        }

        if (isAbsolMode() && !deckSettings.getRelics().getChosenKey().contains("absol")) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "This game seems to be using Absol mode, so the relic deck you chose will be overridden.");
            success &= validateAndSetRelicDeck(Mapper.getDeck("relics_absol"));
        } else {
            success &= validateAndSetRelicDeck(deckSettings.getRelics().getValue());
        }

        return success;
    }

    public boolean validateAndSetPublicObjectivesStage1Deck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getStage1PublicDeckID().equals(deck.getAlias())) return true;

        int peekableStageOneCount = publicObjectives1Peekable.size();
        setUpPeekableObjectives(0, 1);
        if (revealedPublicObjectives.size() > 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Cannot change public objective deck to **" + deck.getName()
                            + "** while there are revealed public objectives.");
            return false;
        }

        setStage1PublicDeckID(deck.getAlias());
        publicObjectives1 = deck.getNewShuffledDeck();
        setUpPeekableObjectives(peekableStageOneCount, 1);
        return true;
    }

    public boolean validateAndSetPublicObjectivesStage2Deck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getStage2PublicDeckID().equals(deck.getAlias())) return true;

        int peekableStageTwoCount = publicObjectives2Peekable.size();
        setUpPeekableObjectives(0, 2);
        if (revealedPublicObjectives.size() > 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Cannot change public objective deck to **" + deck.getName()
                            + "** while there are revealed public objectives.");
            return false;
        }

        setStage2PublicDeckID(deck.getAlias());
        publicObjectives2 = deck.getNewShuffledDeck();
        setUpPeekableObjectives(peekableStageTwoCount, 2);
        return true;
    }

    public void resetActionCardDeck(DeckModel deck) {
        setAcDeckID(deck.getAlias());
        setActionCards(deck.getNewShuffledDeck());
        getDiscardActionCards().clear();
        getDiscardACStatus().clear();
        for (Player player : players.values()) {
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
        if (!getDiscardActionCards().isEmpty() && !isTwilightsFallMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Since there were action cards in the discard pile, will just shuffle any new action cards into the existing deck.");
            shuffledExtrasIn = true;
        } else {
            for (Player player : players.values()) {
                if (!player.getActionCards().isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Since there were action cards in players hands, will just shuffle any new action cards into the existing deck.");
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

        for (Player player : players.values()) {
            if (!player.getSecrets().isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
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
        if (getExplorationDeckID().equals(deck.getAlias())) return true;

        if (!discardExplore.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Cannot change exploration deck to **" + deck.getName()
                            + "** while there are exploration cards in the discard piles.");
            return false;
        }
        setExplorationDeckID(deck.getAlias());
        explore = deck.getNewShuffledDeck();
        return true;
    }

    public boolean validateAndSetAgendaDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getAgendaDeckID().equals(deck.getAlias())) return true;

        if (!discardAgendas.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Cannot change agenda deck to **" + deck.getName()
                            + "** while there are agendas in the discard pile.");
            return false;
        }
        setAgendaDeckID(deck.getAlias());
        setAgendas(deck.getNewShuffledDeck());
        if ("agendas_br".equalsIgnoreCase(deck.getAlias())) {
            List<String> list = new ArrayList<>(Arrays.asList(
                    "prophecy",
                    "minister_antiquities",
                    "minister_commrece",
                    "minister_exploration",
                    "minister_industry",
                    "minister_peace",
                    "minister_policy",
                    "minister_sciences",
                    "minister_war",
                    "censure",
                    "arbiter",
                    "abolishment",
                    "classified",
                    "crisis",
                    "execution",
                    "grant_reallocation",
                    "redistribution",
                    "secret",
                    "standardization",
                    "warrant",
                    "prophecy",
                    "minister_antiquities",
                    "minister_commrece",
                    "minister_exploration",
                    "minister_industry",
                    "minister_peace",
                    "minister_policy",
                    "minister_sciences",
                    "minister_war",
                    "censure",
                    "arbiter",
                    "abolishment",
                    "classified",
                    "crisis",
                    "execution",
                    "grant_reallocation",
                    "redistribution",
                    "secret",
                    "standardization",
                    "warrant",
                    "prophecy",
                    "minister_antiquities",
                    "minister_commrece",
                    "minister_exploration",
                    "minister_industry",
                    "minister_peace",
                    "minister_policy",
                    "minister_sciences",
                    "minister_war",
                    "censure",
                    "arbiter",
                    "abolishment",
                    "classified",
                    "crisis",
                    "execution",
                    "grant_reallocation",
                    "redistribution",
                    "secret",
                    "standardization",
                    "warrant",
                    "strategic_coordination",
                    "minister_of_justice",
                    "invalidated_patent"));
            Collections.shuffle(list);
            setMandates(list);
        }
        return true;
    }

    private boolean validateAndSetTechnologyDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getTechnologyDeckID().equals(deck.getAlias())) return true;

        swapOutVariantTechs();
        setTechnologyDeckID(deck.getAlias());
        swapInVariantTechs();
        return true;
    }

    public boolean validateAndSetEventDeck(GenericInteractionCreateEvent event, DeckModel deck) {
        if (getEventDeckID().equals(deck.getAlias())) return true;

        if (!discardedEvents.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Cannot change event deck to **" + deck.getName()
                            + "** while there are events in the discard pile.");
            return false;
        }
        setEventDeckID(deck.getAlias());
        setEvents(deck.getNewShuffledDeck());
        return true;
    }

    public void setDiscardActionCards(Map<String, Integer> discardActionCards) {
        discardActionCards.forEach((key, value) -> getDiscardActionCards().put(key, value));
    }

    public void setDiscardActionCardStatus(Map<String, ACStatus> discardACStatus) {
        discardACStatus.forEach((key, value) -> getDiscardACStatus().put(key, value));
    }

    public void setDiscardActionCards(List<String> discardActionCardList) {
        for (String card : discardActionCardList) {
            Collection<Integer> values = getDiscardActionCards().values();
            int identifier = ThreadLocalRandom.current().nextInt(1000);
            while (values.contains(identifier)) {
                identifier = ThreadLocalRandom.current().nextInt(1000);
            }
            getDiscardActionCards().put(card, identifier);
        }
    }

    public void setPurgedActionCards(List<String> purgedActionCardList) {
        purgedActionCardList.forEach(ac -> setDiscardActionCard(ac, ACStatus.purged));
    }

    public String getPing() {
        Role role = getGameRole();
        if (role != null) {
            return role.getAsMention();
        }
        StringBuilder sb = new StringBuilder(getName()).append(" ");
        for (String playerID : getPlayerIDs()) {
            User user = JdaService.jda.getUserById(playerID);
            if (user != null) sb.append(user.getAsMention()).append(" ");
        }
        return sb.toString();
    }

    private Role getGameRole() {
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
        if (getTileByPosition(positionOrAlias) != null) return getTileByPosition(positionOrAlias);
        return getTile(AliasHandler.resolveTile(positionOrAlias));
    }

    public Tile getTile(String tileID) {
        if (Constants.TOKEN_PLANETS.contains(tileID)) {
            for (Tile t : tileMap.values()) {
                if (t.getUnitHolderFromPlanet(tileID) != null) {
                    return t;
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
                        .count()
                > 1;
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

    public List<Player> getRealPlayers() {
        return players.values().stream().filter(Player::isRealPlayer).toList();
    }

    public List<Player> getRealPlayersExcludingThis(Player p) {
        return players.values().stream()
                .filter(Player::isRealPlayer)
                .filter(p1 -> p1 != p)
                .toList();
    }

    public List<Player> getRealPlayersNNeutral() {
        return players.values().stream()
                .filter(p -> p.isRealPlayer() || (p.getFaction() != null && "neutral".equals(p.getFaction())))
                .toList();
    }

    public List<Player> getRealPlayersNDummies() {
        return players.values().stream()
                .filter(player -> player.isRealPlayer()
                        || player.isDummy() && player.getColor() != null && !"null".equals(player.getColor()))
                .collect(Collectors.toList());
    }

    public List<Player> getRealAndEliminatedPlayers() {
        return players.values().stream()
                .filter(player -> (player.isRealPlayer() || player.isEliminated()))
                .toList();
    }

    public List<Player> getRealAndEliminatedAndDummyPlayers() {
        return players.values().stream()
                .filter(player -> (player.isRealPlayer() || player.isEliminated() || player.isDummy()))
                .toList();
    }

    public List<Player> getDummies() {
        return players.values().stream().filter(Player::isDummy).toList();
    }

    public List<Player> getNotRealPlayers() {
        return players.values().stream().filter(not(Player::isRealPlayer)).toList();
    }

    public List<Player> getPlayersWithGMRole() {
        if (getGuild() == null) return Collections.emptyList();
        List<Role> roles = getGuild().getRolesByName(getName() + " GM", true);
        Role gmRole = roles.isEmpty() ? null : roles.getFirst();
        return players.values().stream()
                .filter(player -> {
                    Member user = getGuild().getMemberById(player.getUserID());
                    return user != null && user.getRoles().contains(gmRole);
                })
                .toList();
    }

    public List<Player> getPassedPlayers() {
        List<Player> passedPlayers = new ArrayList<>();
        for (Player player : getRealPlayers()) {
            if (player.isPassed()) {
                passedPlayers.add(player);
            }
        }
        return passedPlayers;
    }

    public Set<String> getFactions() {
        return getRealAndEliminatedAndDummyPlayers().stream()
                .map(Player::getFaction)
                .collect(Collectors.toSet());
    }

    public Set<String> getRealFactions() {
        return getRealPlayers().stream().map(Player::getFaction).collect(Collectors.toSet());
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

    public Set<String> getPlayerIDs() {
        return players.keySet();
    }

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
        if (ownerID.length() > 18) ownerID = ownerID.substring(0, 18);
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
            customHyperlaneData.remove(position);
        }

        tileMap.remove(position);
        planets.clear();
    }

    public void removeAllTiles() {
        for (Tile tile : new ArrayList<>(tileMap.values())) {
            removeTile(tile.getPosition());
        }
    }

    public void removePlanet(UnitHolder planet) {
        if ("styx".equals(planet.getName())) {
            String marrow = "A Song Like Marrow";
            for (Player p : players.values()) {
                if (unscorePublicObjective(p.getUserID(), marrow)) {
                    String msg = p.getRepresentation() + " lost 1 victory point because Styx is gone.";
                    MessageHelper.sendMessageToChannel(p.getCorrectChannel(), msg);
                }
            }
        }

        for (Player p : players.values()) {
            String color = p.getColor();
            planet.removeAllUnits(color);
            PlanetRemove.removePlayerControlToken(p, planet);
            p.removePlanet(planet.getName());
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

    public Set<String> getPlanets() {
        if (planets.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (Entry<String, UnitHolder> unitHolderEntry :
                        tile.getUnitHolders().entrySet()) {
                    if (unitHolderEntry.getValue() instanceof Planet p) {
                        planets.put(unitHolderEntry.getKey(), p);
                    }
                }
            }
            planets.put("custodiavigilia", new Planet("custodiavigilia", new Point(0, 0)));
            if ("custodiavigilia".equalsIgnoreCase(getStoredValue("terraformedPlanet"))) {
                planets.get("custodiavigilia").addToken(Constants.ATTACHMENT_TITANSPN_PNG);
            }
            if (isThundersEdge()) {
                planets.get("custodiavigilia").addToken("attachment_negativeinf.png");
            }
            planets.put("custodiavigiliaplus", new Planet("custodiavigiliaplus", new Point(0, 0)));
            planets.put("nevermore", new Planet("nevermore", new Point(0, 0)));
            planets.put("ghoti", new Planet("ghoti", new Point(0, 0)));
            if ("ghoti".equalsIgnoreCase(getStoredValue("terraformedPlanet"))) {
                planets.get("ghoti").addToken(Constants.ATTACHMENT_TITANSPN_PNG);
            }
            planets.put("ocean1", new Planet("ocean1", new Point(0, 0)));
            planets.put("ocean2", new Planet("ocean2", new Point(0, 0)));
            planets.put("ocean3", new Planet("ocean3", new Point(0, 0)));
            planets.put("bannerhall1", new Planet("bannerhall1", new Point(0, 0)));
            planets.put("bannerhall2", new Planet("bannerhall2", new Point(0, 0)));
            planets.put("bannerhall3", new Planet("bannerhall3", new Point(0, 0)));
            planets.put("ocean4", new Planet("ocean4", new Point(0, 0)));
            planets.put("ocean5", new Planet("ocean5", new Point(0, 0)));
            planets.put("triad", new Planet("triad", new Point(0, 0)));
            planets.put("grove", new Planet("grove", new Point(0, 0)));
        }
        return planets.keySet();
    }

    public void rebuildTilePositionAutoCompleteList() {
        tileNameAutocompleteOptionsCache = tileMap.values().stream()
                .map(tile -> new SimpleEntry<>(tile.getAutoCompleteName(), tile.getPosition()))
                .filter(e -> !e.getKey().toLowerCase().contains("hyperlane"))
                .toList();
    }

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
        for (Player player : players.values()) {
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

        for (Player player : players.values()) {
            allPromissoryNotes.addAll(player.getPromissoryNotes().keySet());
            allPlayerHandPromissoryNotes.addAll(player.getPromissoryNotes().keySet());
            allPromissoryNotes.addAll(player.getPromissoryNotesInPlayArea());
            allOwnedPromissoryNotes.addAll(player.getPromissoryNotesOwned());
        }

        // Find duplicate PNs - PNs that are in multiple players' hands or play areas
        if (!Helper.findDuplicateInList(allPlayerHandPromissoryNotes).isEmpty()) {
            BotLogger.warning(
                    new LogOrigin(this),
                    "`" + getName() + "`: there are duplicate promissory notes in the game:\n> `"
                            + Helper.findDuplicateInList(allPlayerHandPromissoryNotes) + "`");
        }

        allPromissoryNotes.addAll(purgedPN);

        // Find PNs that are extra - players have them but nobody "owns" them
        List<String> unOwnedPromissoryNotes = new ArrayList<>(allPromissoryNotes);
        unOwnedPromissoryNotes.removeAll(allOwnedPromissoryNotes);
        if (!unOwnedPromissoryNotes.isEmpty()) {
            BotLogger.warning(
                    new LogOrigin(this),
                    "`" + getName() + "`: there are promissory notes in the game that no player owns:\n> `"
                            + unOwnedPromissoryNotes + "`");
            purgedPN.removeAll(unOwnedPromissoryNotes);
        }

        // Remove unowned PNs from all players hands
        for (Player player : players.values()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotes().keySet());
            for (String pnID : pns) {
                if (unOwnedPromissoryNotes.contains(pnID)) {
                    player.removePromissoryNote(pnID);
                    BotLogger.info("`" + getName() + "`: removed promissory note `" + pnID + "` from player `"
                            + player.getUserName() + "` because nobody 'owned' it");
                }
            }
        }

        // Report PNs that are missing from the game
        List<String> missingPromissoryNotes = new ArrayList<>(allOwnedPromissoryNotes);
        missingPromissoryNotes.removeAll(allPromissoryNotes);
        if (!missingPromissoryNotes.isEmpty()) {
            BotLogger.warning(
                    new LogOrigin(this),
                    "`" + getName() + "`: there are promissory notes that should be in the game but are not:\n> `"
                            + missingPromissoryNotes + "`");
            for (Player player : players.values()) {
                PromissoryNoteHelper.checkAndAddPNs(this, player);
            }
            GameManager.save(this, "Added missing promissory notes to players' hands: " + missingPromissoryNotes);
        }
    }

    private boolean leaderIsFake(String leaderID) {
        return (getStoredValue("fakeCommanders").contains(leaderID)
                || getStoredValue("minorFactionCommanders").contains(leaderID));
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
        if (player.hasLeaderUnlocked(leaderID)) return true;
        if (!leaderID.contains("commander")) return false;

        if (leaderIsFake(leaderID) && !"gateteen".equalsIgnoreCase(getName())) {
            return false;
        }

        if ("sardakkcommander".equalsIgnoreCase(leaderID) && player.hasTech("tf-valkyrie")) {
            return true;
        }
        if ("crimsoncommander".equalsIgnoreCase(leaderID) && player.hasTech("tf-entropicharvest")) {
            return true;
        }

        for (String pnID : player.getPromissoryNotesInPlayArea()) {
            if (pnID.contains("_an") || "dspnceld".equals(pnID)) { // dspnceld = Celdauri Trade Alliance
                Player pnOwner = getPNOwner(pnID);
                if (pnOwner != null
                        && !pnOwner.getFaction().equalsIgnoreCase(player.getFaction())
                        && pnOwner.hasLeaderUnlocked(leaderID)) {
                    return true;
                }
            }
        }

        // check if player has Imperia and if any of the stolen CCs are owned by players
        // that have the leader unlocked
        if (player.hasAbility("imperia")) {
            for (Player player_ : getRealPlayersNDummies()) {
                if (player_.getFaction().equalsIgnoreCase(player.getFaction())) continue;
                if (player.getMahactCC().contains(player_.getColor()) && player_.hasLeaderUnlocked(leaderID)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<Leader> playerUnlockedLeadersOrAlliance(Player player) {
        List<Leader> leaders = new ArrayList<>(player.getLeaders());
        // check if player has any alliances with players that have the commander
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
                if (otherPlayer.equals(player)) continue;
                if (player.getMahactCC().contains(otherPlayer.getColor())) {
                    for (Leader playerLeader : otherPlayer.getLeaders()) {
                        if (leaderIsFake(playerLeader.getId())) {
                            continue;
                        }
                        if (!playerLeader.getId().contains("commander")) {
                            continue;
                        }
                        if (isAllianceMode()
                                && "mahact".equalsIgnoreCase(player.getFaction())
                                && !playerLeader.getId().contains(otherPlayer.getFaction())) {
                            continue;
                        }
                        leaders.add(playerLeader);
                    }
                }
            }
        }
        leaders = leaders.stream()
                .filter(leader -> leader != null && !leader.isLocked())
                .collect(Collectors.toList());
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

    public StrategyCardSetModel getStrategyCardSet() {
        return Mapper.getStrategyCardSets().get(getScSetID());
    }

    public Optional<StrategyCardModel> getStrategyCardModelByInitiative(int scInitiative) {
        return getStrategyCardSet().getStrategyCardModelByInitiative(scInitiative);
    }

    public Optional<StrategyCardModel> getStrategyCardModelByName(String name) {
        return getStrategyCardSet().getStrategyCardModelByName(name);
    }

    public String getSCName(int scInitiative) {
        if (getStrategyCardModelByInitiative(scInitiative).isPresent()) {
            return getStrategyCardModelByInitiative(scInitiative).get().getName();
        }
        return "SC#" + scInitiative;
    }

    public String getSCEmojiWordRepresentation(int scInitiative) {
        if (getStrategyCardModelByInitiative(scInitiative).isPresent()) {
            return getStrategyCardModelByInitiative(scInitiative).get().getEmojiWordRepresentation();
        }
        return "SC#" + scInitiative;
    }

    /**
     * @param scID
     * @return true when the Game's SC Set contains a strategy card which uses a certain automation
     */
    public boolean usesStrategyCardAutomation(String scID) {
        return getStrategyCardSet().getStrategyCardModels().stream()
                .anyMatch(sc -> scID.equals(sc.getBotSCAutomationID()));
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

    public int getEventDeckSize() {
        return getEvents().size();
    }

    public int getEventFullDeckSize() {
        DeckModel eventDeckModel = Mapper.getDeck(getEventDeckID());
        if (eventDeckModel != null) return eventDeckModel.getCardCount();
        return -1;
    }

    public int getPublicObjectives1DeckSize() {
        return publicObjectives1.size();
    }

    public int getPublicObjectives1FullDeckSize() {
        DeckModel po1DeckModel = Mapper.getDeck(getStage1PublicDeckID());
        if (po1DeckModel != null) return po1DeckModel.getCardCount();
        return -1;
    }

    public int getPublicObjectives2DeckSize() {
        return publicObjectives2.size();
    }

    public int getPublicObjectives2FullDeckSize() {
        DeckModel po2DeckModel = Mapper.getDeck(getStage2PublicDeckID());
        if (po2DeckModel != null) return po2DeckModel.getCardCount();
        return -1;
    }

    public int getRelicDeckSize() {
        return relics.size();
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

        int count = 0;
        for (String exploreCardID : exploreDeckModel.getNewDeck()) {
            ExploreModel exploreCard = Mapper.getExplore(exploreCardID);
            if (exploreCard.getType().equalsIgnoreCase(exploreDeckID)) {
                count++;
            }
        }
        return count;
    }

    public int getHazardousExploreDeckSize() {
        return getExploreDeckSize(Constants.HAZARDOUS);
    }

    public int getHazardousExploreDiscardSize() {
        return getExploreDiscard(Constants.HAZARDOUS).size();
    }

    public int getHazardousExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.HAZARDOUS);
    }

    public int getCulturalExploreDeckSize() {
        return getExploreDeckSize(Constants.CULTURAL);
    }

    public int getCulturalExploreDiscardSize() {
        return getExploreDiscard(Constants.CULTURAL).size();
    }

    public int getCulturalExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.CULTURAL);
    }

    public int getIndustrialExploreDeckSize() {
        return getExploreDeckSize(Constants.INDUSTRIAL);
    }

    public int getIndustrialExploreDiscardSize() {
        return getExploreDiscard(Constants.INDUSTRIAL).size();
    }

    public int getIndustrialExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.INDUSTRIAL);
    }

    public int getFrontierExploreDeckSize() {
        return getExploreDeckSize(Constants.FRONTIER);
    }

    public int getFrontierExploreDiscardSize() {
        return getExploreDiscard(Constants.FRONTIER).size();
    }

    public int getFrontierExploreFullDeckSize() {
        return getExploreDeckFullSize(Constants.FRONTIER);
    }

    public List<String> getAllPlanetsWithSleeperTokens() {
        List<String> planetsWithSleepers = new ArrayList<>();
        for (Tile tile : tileMap.values()) {
            planetsWithSleepers.addAll(tile.getPlanetsWithSleeperTokens());
        }
        return planetsWithSleepers;
    }

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
            if (player.getLeaderIDs().contains(leaderID)) return true;
        }
        return false;
    }

    public Tile getMecatolTile() {
        if (isOrdinianC1Mode()) {
            return ButtonHelper.getTileWithCoatl(this);
        }
        if (isLiberationC4Mode()) {
            return getTileFromPlanet("ordinianc4");
        }
        for (String mr : Constants.MECATOL_SYSTEMS) {
            Tile tile = getTile(mr);
            if (tile != null) return tile;
        }
        return null;
    }

    public List<String> mecatols() {
        if (getMecatolTile() == null) return Constants.MECATOLS;
        List<String> mecs = getMecatolTile().getPlanetUnitHolders().stream()
                .map(UnitHolder::getName)
                .toList();
        List<String> result = new ArrayList<>(ListUtils.intersection(mecs, Constants.MECATOLS));
        return result;
    }

    @Nullable
    public Tile getTileFromPlanet(String planetName) {
        for (Tile tile_ : tileMap.values()) {
            for (Entry<String, UnitHolder> unitHolderEntry :
                    tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet
                        && unitHolderEntry.getKey().equals(planetName)) {
                    return tile_;
                }
            }
        }
        return null;
    }

    public List<String> getPlanetsPlayerIsCoexistingOn(Player player) {
        List<String> coexistPlanets = new ArrayList<>();

        for (Player p2 : getRealPlayersNNeutral()) {
            if (p2.getFaction().equalsIgnoreCase(player.getFaction())
                    || player.getAllianceMembers().contains(p2.getFaction())) {
                continue;
            }
            for (String planet : p2.getPlanets()) {
                UnitHolder uH = getUnitHolderFromPlanet(planet);
                if (uH != null && FoWHelper.playerHasUnitsOnPlanet(player, uH)) {
                    coexistPlanets.add(planet);
                }
            }
        }
        return coexistPlanets;
    }

    public List<String> getPlayersPlanetsThatOthersAreCoexistingOn(Player player) {
        List<String> coexistPlanets = new ArrayList<>();

        for (Player p2 : getRealPlayers()) {
            if (p2.getFaction().equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            for (String planet : player.getPlanets()) {
                UnitHolder uH = getUnitHolderFromPlanet(planet);
                if (uH != null && FoWHelper.playerHasUnitsOnPlanet(p2, uH) && !coexistPlanets.contains(planet)) {
                    coexistPlanets.add(planet);
                }
            }
        }
        return coexistPlanets;
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
        for (Player player : players.values()) {
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
            if (Objects.equals(factionColor, player.getFaction())
                    || Objects.equals(factionColor, player.getColor())
                    || Objects.equals(factionColor, player.getColorID())) {
                return player;
            }
        }
        return null;
    }

    @Nullable
    public Player getPlayerFromLeader(String leader) {
        Player player = null;
        if (leader != null) {
            for (Player player_ : players.values()) {
                if (player_.getLeaderIDs().contains(leader)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    @Nullable
    public Player getPlayerFromBreakthrough(String bt) {
        if (bt != null) {
            for (Player player : players.values()) {
                if (player.hasBreakthrough(bt)) {
                    return player;
                }
            }
        }
        return null;
    }

    public UnitModel getUnitFromUnitKey(UnitKey unitKey) {
        Player player = getPlayerFromColorOrFaction(unitKey.getColorID());
        if (player == null) return null;
        return player.getUnitFromUnitKey(unitKey);
    }

    public void swapInVariantUnits(String source) {
        List<UnitModel> variantUnits = Mapper.getUnits().values().stream()
                .filter(unit -> source.equals(unit.getSource().toString()))
                .toList();
        for (Player player : players.values()) {
            List<UnitModel> playersUnits = player.getUnitModels().stream()
                    .filter(unit -> !source.equals(unit.getSource().toString()))
                    .toList();
            for (UnitModel playerUnit : playersUnits) {
                for (UnitModel variantUnit : variantUnits) {
                    boolean variantReplacesPok =
                            variantUnit.getHomebrewReplacesID().orElse("-").equals(playerUnit.getId());
                    boolean pokReplacesVariant =
                            playerUnit.getHomebrewReplacesID().orElse("-").equals(variantUnit.getId());
                    if (variantReplacesPok || pokReplacesVariant) {
                        player.removeOwnedUnitByID(playerUnit.getId());
                        player.addOwnedUnitByID(variantUnit.getId());
                        break;
                    }
                }
            }
        }
    }

    public void swapInVariantTechs() {
        DeckModel deckModel = Mapper.getDeck(getTechnologyDeckID());
        if (deckModel == null) return;
        List<TechnologyModel> techsToReplace = deckModel.getNewDeck().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(t -> t.getHomebrewReplacesID().isPresent())
                .toList();
        for (Player player : players.values()) {
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

    public void swapOutVariantTechs() {
        DeckModel deckModel = Mapper.getDeck(getTechnologyDeckID());
        if (deckModel == null) return;
        List<TechnologyModel> techsToReplace = Mapper.getTechs().values().stream()
                .filter(t -> t.getHomebrewReplacesID().isPresent())
                .toList();
        for (Player player : players.values()) {
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
        if (player.hasTheZeroToken()) scText = "0/" + scText; // naalu 0 token ability
        if (player.hasAbility("patience")) {
            scText = "9/" + scText;
        }

        return scText;
    }

    // Currently unused
    // TODO (Jazz): parse this better
    public List<ComponentSource> getComponentSources() {
        List<ComponentSource> sources = new ArrayList<>();
        sources.add(ComponentSource.base);
        sources.add(ComponentSource.codex1);
        sources.add(ComponentSource.codex2);
        sources.add(ComponentSource.codex3);
        sources.add(ComponentSource.codex4);
        if (!isBaseGameMode()) sources.add(ComponentSource.pok);
        if (isAbsolMode()) sources.add(ComponentSource.absol);
        if (isVotcMode()) sources.add(ComponentSource.cryypter);
        if (isMiltyModMode()) sources.add(ComponentSource.miltymod);
        if (isDiscordantStarsMode()) sources.add(ComponentSource.ds);
        return sources;
    }

    public boolean isThundersEdgeDemo() {
        return getTags().contains("Thunder's Edge Demo");
    }

    public boolean hasHomebrew() {
        return isHomebrew()
                || isExtraSecretMode()
                || isFowMode()
                || isFacilitiesMode()
                || isLightFogMode()
                || isRedTapeMode()
                || isDiscordantStarsMode()
                || isFrankenGame()
                || isMiltyModMode()
                || isThundersEdgeDemo()
                || isAbsolMode()
                || isVotcMode()
                || isPromisesPromisesMode()
                || isFlagshippingMode()
                || getSpinMode() != null && !"OFF".equalsIgnoreCase(getSpinMode())
                || isHomebrewSCMode()
                || isCommunityMode()
                || !checkAllDecksAreOfficial()
                || !checkAllTilesAreOfficial()
                || getRealAndEliminatedPlayers().size() < 3
                || getRealAndEliminatedPlayers().size() > 8
                || getFactions().stream()
                        .map(Mapper::getFaction)
                        .filter(Objects::nonNull)
                        .anyMatch(faction -> !faction.getSource().isOfficial())
                || getRealAndEliminatedAndDummyPlayers().stream()
                        .map(Player::getLeaderIDs)
                        .flatMap(Collection::stream)
                        .map(Mapper::getLeader)
                        .filter(Objects::nonNull)
                        .anyMatch(leader -> !leader.getSource().isOfficial());
    }

    public boolean checkAllDecksAreOfficial() {
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

    public boolean checkAllTilesAreOfficial() {
        // Tiles
        return tileMap.values().stream().allMatch(tile -> {
            if (tile == null || tile.getTileModel() == null) {
                return true;
            }
            ComponentSource tileSource = tile.getTileModel().getSource();
            if (tile.getTileModel().getImagePath().endsWith("_Hyperlane.png")) {
                return true; // official hyperlane
            }
            return tileSource != null && tileSource.isOfficial();
        });
    }

    public void setStrategyCardSet(String scSetID) {
        StrategyCardSetModel strategyCardModel = Mapper.getStrategyCardSets().get(scSetID);
        if (strategyCardModel == null) {
            throw new IllegalArgumentException("Strategy card set not found for ID: " + scSetID);
        }
        setHomebrewSCMode(!"pok".equals(scSetID) && !"base_game".equals(scSetID) && !"te".equals(scSetID));

        Map<Integer, Integer> oldTGs = getScTradeGoods();
        setScTradeGoods(new LinkedHashMap<>());
        setScSetID(strategyCardModel.getAlias());
        strategyCardModel
                .getStrategyCardModels()
                .forEach(scModel ->
                        setScTradeGood(scModel.getInitiative(), oldTGs.getOrDefault(scModel.getInitiative(), 0)));
    }

    public List<ColorModel> getUnusedColorsPreferringBase() {
        List<String> priorityColourIDs = List.of("red", "blue", "yellow", "purple", "green", "orange", "pink", "black");
        List<ColorModel> priorityColours = priorityColourIDs.stream()
                .map(Mapper::getColor)
                .filter(color -> players.values().stream()
                        .noneMatch(player -> player.getColor().equals(color.getName())))
                .toList();
        if (!priorityColours.isEmpty()) {
            return priorityColours;
        }
        return getUnusedColors();
    }

    public List<ColorModel> getUnusedColors() {
        return Mapper.getColors().stream()
                .filter(color -> players.values().stream()
                        .noneMatch(player -> player.getColor().equals(color.getName())))
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
        Map<String, Tile> tileMap = new HashMap<>(this.tileMap);
        StringBuilder sb = new StringBuilder();
        for (String position : sortedTilePositions) {
            boolean missingTile = true;
            for (Tile tile : tileMap.values()) {
                if (tile.getPosition().equals(position)) {
                    String tileID =
                            AliasHandler.resolveStandardTile(tile.getTileID()).toUpperCase();
                    if ("000".equalsIgnoreCase(position) && "112".equalsIgnoreCase(tileID)) {
                        // Mecatol Rex in Centre Position
                        sb.append("{112}");
                    } else if ("000".equalsIgnoreCase(position) && !"112".equalsIgnoreCase(tileID)) {
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
        return tileMap.values().stream().map(Tile::getHexTileSummary).collect(Collectors.joining(","));
    }

    public boolean hasUser(User user) {
        if (user == null) return false;
        String id = user.getId();
        for (Player player : players.values()) {
            if (player.getUserID().equals(id)) {
                return true;
            }
            if (player.getTeamMateIDs().contains(id)) {
                return true;
            }
        }
        return false;
    }

    public List<String> peekAtSecrets(int count) {
        var peekedSecrets = new ArrayList<String>();
        for (int i = 0; i < count && i < getSecretObjectives().size(); i++) {
            peekedSecrets.add(getSecretObjectives().get(i));
        }
        return peekedSecrets;
    }

    public int addTradeGoodsToStrategyCard(int strategyCard, int tradeGoodCount) {
        return strategyCardManager.addTradeGoods(strategyCard, tradeGoodCount);
    }

    public void setAllDebtPoolIcons(Map<String, String> debtPoolIcons) {
        this.debtPoolIcons = debtPoolIcons;
    }

    public Map<String, String> getAllDebtPoolIcons() {
        return debtPoolIcons;
    }

    public void setDebtPoolIcon(String pool, String icon) {
        debtPoolIcons.put(pool.toLowerCase(), icon);
    }

    public String getDebtPoolIcon(String pool) {
        return debtPoolIcons.getOrDefault(pool.toLowerCase(), null);
    }

    public void clearDebtPoolIcon(String pool) {
        debtPoolIcons.remove(pool.toLowerCase());
    }
}
