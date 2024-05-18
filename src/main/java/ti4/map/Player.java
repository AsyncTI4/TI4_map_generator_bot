package ti4.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.user.UserSettings;
import ti4.commands.user.UserSettingsManager;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;

public class Player {

    private String userID;
    private String userName;

    private String gameID;
    private boolean tenMinReminderPing;

    private boolean passed;
    private boolean readyToPassBag;
    private boolean searchWarrant;
    private boolean isDummy;
    private boolean prefersDistanceBasedTacticalActions;
    private boolean autoPassOnWhensAfters;
    private boolean eliminated;

    private String faction;
    private String factionEmoji;

    @Getter
    @Setter
    private String displayName;

    @Setter
    private String playerStatsAnchorPosition;
    private String allianceMembers = "";
    private String hoursThatPlayerIsAFK = "";
    private String color;

    @Getter
    @Setter
    private String decalSet;
    private String autoCompleteRepresentation;

    private int tacticalCC = 3;
    private int fleetCC = 3;
    private int strategicCC = 2;
    private int turnCount;
    private int tg;
    private int commodities;
    private int personalPingInterval;
    private int commoditiesTotal;
    private int stasisInfantry;
    private int autoSaboPassMedian;
    private int actualHits;
    private int expectedHitsTimes10;
    private int totalExpenses;

    private Set<Integer> followedSCs = new HashSet<>();

    private final Map<String, Integer> actionCards = new LinkedHashMap<>();
    private final Map<String, Integer> events = new LinkedHashMap<>();
    private final Map<String, Integer> trapCards = new LinkedHashMap<>();
    private final Map<String, String> trapCardsPlanets = new LinkedHashMap<>();
    private final Map<String, Integer> secrets = new LinkedHashMap<>();
    private final Map<String, Integer> secretsScored = new LinkedHashMap<>();
    private Map<String, Integer> promissoryNotes = new LinkedHashMap<>();
    private Set<String> abilities = new HashSet<>();
    private Set<String> exhaustedAbilities = new HashSet<>();
    private Set<String> promissoryNotesOwned = new HashSet<>();
    private Set<String> unitsOwned = new HashSet<>();
    private List<String> promissoryNotesInPlayArea = new ArrayList<>();
    private List<String> techs = new ArrayList<>();
    private List<String> spentThingsThisWindow = new ArrayList<>();
    private List<String> teamMateIDs = new ArrayList<>();
    private Map<String, Integer> producedUnits = new HashMap<>();
    @Getter
    @Setter
    private List<String> factionTechs = new ArrayList<>();
    private DraftBag draftHand = new DraftBag();
    private DraftBag currentDraftBag = new DraftBag();
    private final DraftBag draftItemQueue = new DraftBag();
    private List<String> exhaustedTechs = new ArrayList<>();
    private List<String> planets = new ArrayList<>();
    private List<String> exhaustedPlanets = new ArrayList<>();
    private List<String> exhaustedPlanetsAbilities = new ArrayList<>();
    private List<String> mahactCC = new ArrayList<>();

    @JsonProperty("leaders")
    private List<Leader> leaders = new ArrayList<>();

    private Map<String, Integer> debt_tokens = new LinkedHashMap<>(); // color, count
    private final Map<String, String> fow_seenTiles = new HashMap<>();
    private final Map<String, Integer> unitCaps = new HashMap<>();
    private final Map<String, String> fow_customLabels = new HashMap<>();
    private String fowFogFilter;
    private boolean fogInitialized;

    @Nullable
    private String roleIDForCommunity;
    @Nullable
    private String privateChannelID;
    @Nullable
    private String cardsInfoThreadID;
    @Nullable
    private String bagInfoThreadID;

    private int crf;
    private int hrf;
    private int irf;
    private int vrf;
    private List<String> fragments = new ArrayList<>();
    private List<String> relics = new ArrayList<>();
    private List<String> exhaustedRelics = new ArrayList<>();
    private LinkedHashSet<Integer> SCs = new LinkedHashSet<>();

    private final List<TemporaryCombatModifierModel> newTempCombatModifiers = new ArrayList<>();
    private List<TemporaryCombatModifierModel> tempCombatModifiers = new ArrayList<>();

    // BENTOR CONGLOMERATE ABILITY "Ancient Blueprints"
    private boolean hasFoundCulFrag;
    private boolean hasFoundHazFrag;
    private boolean hasFoundIndFrag;
    private boolean hasFoundUnkFrag;

    // LANEFIR TECH "ATS Armaments"
    @Getter
    @Setter
    private int atsCount;

    // OLRADIN POLICY ONCE PER ACTION EXHAUST PLANET ABILITIES
    @Setter
    private boolean hasUsedEconomyEmpowerAbility;
    @Setter
    private boolean hasUsedEconomyExploitAbility;
    @Setter
    private boolean hasUsedEnvironmentPreserveAbility;
    @Setter
    private boolean hasUsedEnvironmentPlunderAbility;
    @Setter
    private boolean hasUsedPeopleConnectAbility;

    // Statistics
    private int numberOfTurns;
    private long totalTimeSpent;

    private final Tile nomboxTile = new Tile("nombox", "nombox");

    public Player() {
    }

    public Player(@JsonProperty("userID") String userID, @JsonProperty("userName") String userName,
        @JsonProperty("gameID") String gameID) {
        this.userID = userID;
        this.userName = userName;
        this.gameID = gameID;
    }

    @JsonIgnore
    public Game getGame() {
        return GameManager.getInstance().getGame(gameID);
    }

    @JsonIgnore
    public String getDecalName() {
        return Mapper.getDecalName(getDecalSet());
    }

    public Tile getNomboxTile() {
        return nomboxTile;
    }

    public List<String> getMahactCC() {
        return mahactCC;
    }

    public void setMahactCC(List<String> mahactCC) {
        this.mahactCC = mahactCC;
    }

    public void resetProducedUnits() {
        producedUnits = new HashMap<>();
    }

    public void resetSpentThings() {
        spentThingsThisWindow = new ArrayList<>();
    }

    public Map<String, Integer> getCurrentProducedUnits() {
        return producedUnits;
    }

    public List<String> getSpentThingsThisWindow() {
        return spentThingsThisWindow;
    }

    public void addSpentThing(String thing) {
        spentThingsThisWindow.add(thing);
    }

    public void removeSpentThing(String thing) {
        spentThingsThisWindow.remove(thing);
    }

    public int getSpentTgsThisWindow() {
        for (String thing : spentThingsThisWindow) {
            if (thing.contains("tg_")) {
                return Integer.parseInt(thing.split("_")[1]);
            }
        }
        return 0;
    }

    public int getSpentInfantryThisWindow() {
        for (String thing : spentThingsThisWindow) {
            if (thing.contains("infantry_")) {
                return Integer.parseInt(thing.split("_")[1]);
            }
        }
        return 0;
    }

    public void increaseTgsSpentThisWindow(int amount) {
        int oldTgSpent = getSpentTgsThisWindow();
        int newTgSpent = oldTgSpent + amount;
        if (oldTgSpent != 0) {
            removeSpentThing("tg_" + oldTgSpent);
        }
        addSpentThing("tg_" + newTgSpent);
    }

    public void increaseInfantrySpentThisWindow(int amount) {
        int oldTgSpent = getSpentInfantryThisWindow();
        int newTgSpent = oldTgSpent + amount;
        if (oldTgSpent != 0) {
            removeSpentThing("infantry_" + oldTgSpent);
        }
        addSpentThing("infantry_" + newTgSpent);
    }

    public void setSpentThings(List<String> things) {
        spentThingsThisWindow = things;
    }

    public void setProducedUnit(String unit, int count) {
        producedUnits.put(unit, count);
    }

    public int getProducedUnit(String unit) {
        if (producedUnits.get(unit) == null) {
            return 0;
        } else {
            return producedUnits.get(unit);
        }
    }

    public void produceUnit(String unit) {
        int amount = getProducedUnit(unit) + 1;
        producedUnits.put(unit, amount);
    }

    public void setProducedUnits(Map<String, Integer> displacedUnits) {
        producedUnits = displacedUnits;
    }

    public void addMahactCC(String cc) {
        if (!mahactCC.contains(cc)) {
            mahactCC.add(cc);
        }
    }

    public void removeMahactCC(String cc) {
        mahactCC.remove(cc);
    }

    public String getRoleIDForCommunity() {
        return roleIDForCommunity;
    }

    public void setRoleIDForCommunity(String roleIDForCommunity) {
        this.roleIDForCommunity = roleIDForCommunity;
    }

    @Nullable
    @JsonIgnore
    public Role getRoleForCommunity() {
        try {
            return AsyncTI4DiscordBot.jda.getRoleById(getRoleIDForCommunity());
        } catch (Exception e) {
            // BotLogger.log("Could not retrieve MainGameChannel for " + getName(), e);
        }
        return null;
    }

    public String getPrivateChannelID() {
        return privateChannelID;
    }

    public void setPrivateChannelID(String privateChannelID) {
        this.privateChannelID = privateChannelID;
    }

    @Nullable
    @JsonIgnore
    public MessageChannel getPrivateChannel() {
        try {
            return AsyncTI4DiscordBot.jda.getTextChannelById(getPrivateChannelID());
        } catch (Exception e) {
            return null;

        }

    }

    public String getCardsInfoThreadID() {
        return cardsInfoThreadID;
    }

    public String getBagInfoThreadID() {
        return bagInfoThreadID;
    }

    public String getFinsFactionCheckerPrefix() {
        return "FFCC_" + getFaction() + "_";
    }

    @JsonIgnore
    public boolean hasPDS2Tech() {
        return getTechs().contains("ht2") || getTechs().contains("pds2") || getTechs().contains("dsgledpds")
            || getTechs().contains("dsmirvpds");
    }

    @JsonIgnore
    public boolean hasInf2Tech() {// "dszeliinf"
        return getTechs().contains("cl2") || getTechs().contains("so2") || getTechs().contains("inf2")
            || getTechs().contains("lw2") || getTechs().contains("dscymiinf")
            || getTechs().contains("dszeliinf");
    }

    @JsonIgnore
    public boolean hasWarsunTech() {
        return getTechs().contains("pws2") || getTechs().contains("dsrohdws") || getTechs().contains("ws")
            || getTechs().contains("absol_ws")
            || hasUnit("muaat_warsun") || hasUnit("rohdhna_warsun");
    }

    @JsonIgnore
    public boolean hasFF2Tech() {
        return getTechs().contains("ff2") || getTechs().contains("hcf2") || getTechs().contains("dsflorff")
            || getTechs().contains("dslizhff") || getTechs().contains("absol_ff2") || ownsUnit("florzen_fighter");
    }

    @JsonIgnore
    public boolean hasUpgradedUnit(String baseUpgradeID) {
        for (String tech : techs) {
            TechnologyModel model = Mapper.getTech(tech);
            if (tech.equalsIgnoreCase(baseUpgradeID)
                || model.getBaseUpgrade().orElse("Bah").equalsIgnoreCase(baseUpgradeID)) {
                return true;
            }
        }
        return false;
    }

    public void setCardsInfoThreadID(String cardsInfoThreadID) {
        this.cardsInfoThreadID = cardsInfoThreadID;
    }

    public void setBagInfoThreadID(String bagInfoThreadID) {
        this.bagInfoThreadID = bagInfoThreadID;
    }

    @JsonIgnore
    public ThreadChannel getCardsInfoThread() {
        Game game = getGame();
        TextChannel actionsChannel = game.getMainGameChannel();
        if (game.isFoWMode() || game.isCommunityMode())
            actionsChannel = (TextChannel) getPrivateChannel();
        if (actionsChannel == null) {
            actionsChannel = game.getMainGameChannel();
        }
        if (actionsChannel == null) {
            BotLogger.log(
                "`Helper.getPlayerCardsInfoThread`: actionsChannel is null for game, or community game private channel not set: "
                    + game.getName());
            return null;
        }

        String threadName = Constants.CARDS_INFO_THREAD_PREFIX + game.getName() + "-"
            + getUserName().replaceAll("/", "");
        if (game.isFoWMode()) {
            threadName = game.getName() + "-" + "cards-info-" + getUserName().replaceAll("/", "") + "-private";
        }

        // ATTEMPT TO FIND BY ID
        String cardsInfoThreadID = getCardsInfoThreadID();
        boolean hasCardsInfoThreadId = cardsInfoThreadID != null && !cardsInfoThreadID.isBlank()
            && !cardsInfoThreadID.isEmpty() && !"null".equals(cardsInfoThreadID);
        try {
            if (hasCardsInfoThreadId) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null)
                    return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels()
                    .complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using ID: "
                + cardsInfoThreadID + " for potential thread name: " + threadName, e);
        }

        // ATTEMPT TO FIND BY NAME
        try {
            if (hasCardsInfoThreadId) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null)
                    return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels()
                    .complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log(
                "`Player.getCardsInfoThread`: Could not find existing Cards Info thead using name: " + threadName,
                e);
        }

        // CREATE NEW THREAD
        // Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!game.isFoWMode());
        if (game.getName().contains("pbd100") || game.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = actionsChannel.createThreadChannel(threadName, isPrivateChannel);
        threadAction.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
        if (isPrivateChannel) {
            threadAction.setInvitable(false);
        }
        ThreadChannel threadChannel = threadAction.complete();
        setCardsInfoThreadID(threadChannel.getId());
        Helper.checkThreadLimitAndArchive(game.getGuild());
        return threadChannel;
    }

    @JsonIgnore
    public ThreadChannel getCardsInfoThreadWithoutCompletes() {
        Game game = getGame();
        TextChannel actionsChannel = game.getMainGameChannel();
        if (game.isFoWMode() || game.isCommunityMode())
            actionsChannel = (TextChannel) getPrivateChannel();
        if (actionsChannel == null) {
            actionsChannel = game.getMainGameChannel();
        }
        if (actionsChannel == null) {
            BotLogger.log(
                "`Helper.getPlayerCardsInfoThread`: actionsChannel is null for game, or community game private channel not set: "
                    + game.getName());
            return null;
        }

        String threadName = Constants.CARDS_INFO_THREAD_PREFIX + game.getName() + "-"
            + getUserName().replaceAll("/", "");
        if (game.isFoWMode()) {
            threadName = game.getName() + "-" + "cards-info-" + getUserName().replaceAll("/", "") + "-private";
        }

        // ATTEMPT TO FIND BY ID
        String cardsInfoThreadID = getCardsInfoThreadID();
        boolean hasCardsInfoThreadId = cardsInfoThreadID != null && !cardsInfoThreadID.isBlank()
            && !cardsInfoThreadID.isEmpty() && !"null".equals(cardsInfoThreadID);
        try {
            if (hasCardsInfoThreadId) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null)
                    return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using ID: "
                + cardsInfoThreadID + " for potential thread name: " + threadName, e);
        }

        // ATTEMPT TO FIND BY NAME
        try {
            if (hasCardsInfoThreadId) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null)
                    return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log(
                "`Player.getCardsInfoThread`: Could not find existing Cards Info thead using name: " + threadName,
                e);
        }

        return null;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;

    }

    public boolean isReadyToPassBag() {
        return readyToPassBag;
    }

    public boolean doesPlayerPreferDistanceBasedTacticalActions() {
        return prefersDistanceBasedTacticalActions;
    }

    public boolean doesPlayerAutoPassOnWhensAfters() {
        return autoPassOnWhensAfters;
    }

    public void setPreferenceForDistanceBasedTacticalActions(boolean preference) {
        prefersDistanceBasedTacticalActions = preference;
    }

    public void setAutoPassWhensAfters(boolean preference) {
        autoPassOnWhensAfters = preference;
    }

    public boolean shouldPlayerBeTenMinReminded() {
        return tenMinReminderPing;
    }

    public void setReadyToPassBag(boolean passed) {
        readyToPassBag = passed;
    }

    public void setWhetherPlayerShouldBeTenMinReminded(boolean status) {
        tenMinReminderPing = status;
    }

    public Set<String> getAbilities() {
        return abilities;
    }

    public void setAbilities(Set<String> abilities) {
        this.abilities = abilities;
    }

    /**
     * @param abilityID The ID of the ability - does not check if valid
     */
    public void addAbility(String abilityID) {
        abilities.add(abilityID);
    }

    public void removeAbility(String abilityID) {
        abilities.remove(abilityID);
    }

    public boolean hasAbility(String ability) {
        return getAbilities().contains(ability);
    }

    public Set<String> getExhaustedAbilities() {
        return exhaustedAbilities;
    }

    public void setExhaustedAbilities(Set<String> exhaustedAbilities) {
        this.exhaustedAbilities = exhaustedAbilities;
    }

    public boolean addExhaustedAbility(String ability) {
        return exhaustedAbilities.add(ability);
    }

    public boolean removeExhaustedAbility(String ability) {
        return exhaustedAbilities.remove(ability);
    }

    public void clearExhaustedAbilities() {
        exhaustedAbilities.clear();
    }

    public int getUnitCap(String unit) {
        if (unitCaps.get(unit) == null) {
            return 0;
        }
        return unitCaps.get(unit);
    }

    public Map<String, Integer> getUnitCaps() {
        return unitCaps;
    }

    public void setUnitCap(String unit, int cap) {
        unitCaps.put(unit, cap);
    }

    public Map<String, Integer> getActionCards() {
        return actionCards;
    }

    public Map<String, Integer> getEvents() {
        return events;
    }

    public Map<String, Integer> getTrapCards() {
        return trapCards;
    }

    public Map<String, String> getTrapCardsPlanets() {
        return trapCardsPlanets;
    }

    public Set<String> getPromissoryNotesOwned() {
        return promissoryNotesOwned;
    }

    public Set<String> getSpecialPromissoryNotesOwned() {
        return promissoryNotesOwned.stream()
            .filter(pn -> Mapper.getPromissoryNotes().get(pn).isNotWellKnown())
            .collect(Collectors.toSet());
    }

    public void setPromissoryNotesOwned(Set<String> promissoryNotesOwned) {
        this.promissoryNotesOwned = promissoryNotesOwned;
    }

    public boolean ownsPromissoryNote(String promissoryNoteID) {
        return promissoryNotesOwned.contains(promissoryNoteID);
    }

    public boolean removeOwnedPromissoryNoteByID(String promissoryNoteID) {
        return promissoryNotesOwned.remove(promissoryNoteID);
    }

    public boolean addOwnedPromissoryNoteByID(String promissoryNoteID) {
        return promissoryNotesOwned.add(promissoryNoteID);
    }

    public Map<String, Integer> getPromissoryNotes() {
        return promissoryNotes;
    }

    public List<String> getPromissoryNotesInPlayArea() {
        return promissoryNotesInPlayArea;
    }

    public Set<String> getUnitsOwned() {
        return unitsOwned;
    }

    public Set<String> getSpecialUnitsOwned() {
        return unitsOwned.stream()
            .filter(u -> Mapper.getUnit(u).getFaction().isPresent())
            .collect(Collectors.toSet());
    }

    public boolean hasUnit(String unit) {
        return unitsOwned.contains(unit);
    }

    public void setUnitsOwned(Set<String> unitsOwned) {
        this.unitsOwned = unitsOwned;
    }

    public boolean ownsUnit(String unitID) {
        return unitsOwned.contains(unitID);
    }

    public boolean removeOwnedUnitByID(String unitID) {
        return unitsOwned.remove(unitID);
    }

    public boolean addOwnedUnitByID(String unitID) {
        return unitsOwned.add(unitID);
    }

    @JsonIgnore
    public List<UnitModel> getUnitModels() {
        return getUnitsOwned().stream()
            .map(Mapper::getUnit)
            .filter(Objects::nonNull)
            .toList();
    }

    public UnitModel getUnitByBaseType(String unitType) {
        return getUnitsOwned().stream()
            .map(Mapper::getUnit)
            .filter(Objects::nonNull)
            .filter(unit -> unitType.equalsIgnoreCase(unit.getBaseType()))
            .findFirst()
            .orElse(null);
    }

    public List<UnitModel> getUnitsByAsyncID(String asyncID) {
        return getUnitsOwned().stream()
            .map(Mapper::getUnit)
            .filter(Objects::nonNull)
            .filter(unit -> asyncID.equalsIgnoreCase(unit.getAsyncId()))
            .toList();
    }

    public UnitModel getPriorityUnitByAsyncID(String asyncID, UnitHolder unitHolder) {
        List<UnitModel> allUnits = new ArrayList<>(getUnitsByAsyncID(asyncID));

        if (allUnits.isEmpty()) {
            return null;
        }
        if (allUnits.size() == 1) {
            return allUnits.get(0);
        }
        allUnits.sort((d1, d2) -> GetUnitModelPriority(d2, unitHolder) - GetUnitModelPriority(d1, unitHolder));

        return allUnits.get(0);
    }

    private Integer GetUnitModelPriority(UnitModel unit, UnitHolder unitHolder) {
        int score = 0;

        if (StringUtils.isNotBlank(unit.getFaction().orElse(""))
            && StringUtils.isNotBlank(unit.getUpgradesFromUnitId().orElse("")))
            score += 4;
        if (StringUtils.isNotBlank(unit.getFaction().orElse("")))
            score += 3;
        if (StringUtils.isNotBlank(unit.getUpgradesFromUnitId().orElse("")))
            score += 2;
        if (unitHolder != null
            && ((unitHolder.getName().equals(Constants.SPACE) && Boolean.TRUE.equals(unit.getIsShip()))
                || (!unitHolder.getName().equals(Constants.SPACE) && !Boolean.TRUE.equals(unit.getIsShip()))))
            score++;

        return score;
    }

    public UnitModel getUnitByID(String unitID) {
        return Mapper.getUnit(unitID);
    }

    public String checkUnitsOwned() {
        for (int count : getUnitsOwnedByBaseType().values()) {
            if (count > 1) {
                String message = "> Warning - Player: " + getUserName()
                    + " has more than one of the same unit type.\n> Unit Counts: `" + getUnitsOwnedByBaseType()
                    + "`\n> Units Owned: `"
                    + getUnitsOwned() + "`";
                BotLogger.log(message);
                return message;
            }
        }
        return null;
    }

    @JsonIgnore
    public Map<String, Integer> getUnitsOwnedByBaseType() {
        Map<String, Integer> unitCount = new HashMap<>();
        for (String unitID : getUnitsOwned()) {
            UnitModel unitModel = Mapper.getUnit(unitID);
            unitCount.merge(unitModel.getBaseType(), 1, (oldValue, newValue) -> oldValue + 1);
        }
        return unitCount;
    }

    public void setActionCard(String id) {
        Collection<Integer> values = actionCards.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        actionCards.put(id, identifier);
    }

    public void setEvent(String id) {
        Collection<Integer> values = events.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        events.put(id, identifier);
    }

    public void setTrapCard(String id) {
        Collection<Integer> values = trapCards.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        trapCards.put(id, identifier);
    }

    public void setTrapCardPlanet(String id, String planet) {
        trapCardsPlanets.put(id, planet);
    }

    public void removeTrapCardPlanet(String id) {
        trapCardsPlanets.remove(id);
    }

    public void setPromissoryNote(String id) {
        Collection<Integer> values = promissoryNotes.values();
        int identifier = ThreadLocalRandom.current().nextInt(100);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(100);
        }
        promissoryNotes.put(id, identifier);
    }

    public void clearPromissoryNotes() {
        promissoryNotes.clear();
    }

    public void setPromissoryNotesInPlayArea(String id) {
        if (!promissoryNotesInPlayArea.contains(id)) {
            promissoryNotesInPlayArea.add(id);
        }
    }

    @JsonSetter
    public void setPromissoryNotesInPlayArea(List<String> promissoryNotesInPlayArea) {
        this.promissoryNotesInPlayArea = promissoryNotesInPlayArea;
    }

    public void setPromissoryNotes(Map<String, Integer> promissoryNotes) {
        this.promissoryNotes = promissoryNotes;
    }

    public void removePromissoryNotesInPlayArea(String id) {
        promissoryNotesInPlayArea.remove(id);
    }

    public void setActionCard(String id, Integer identifier) {
        actionCards.put(id, identifier);
    }

    public void setEvent(String id, Integer identifier) {
        events.put(id, identifier);
    }

    public void removeEvent(String eventID) {
        events.remove(eventID);
    }

    public void setTrapCard(String id, Integer identifier) {
        trapCards.put(id, identifier);
    }

    public void setPromissoryNote(String id, Integer identifier) {
        promissoryNotes.put(id, identifier);
    }

    public void removeActionCard(Integer identifier) {
        String idToRemove = "";
        for (Map.Entry<String, Integer> so : actionCards.entrySet()) {
            if (so.getValue().equals(identifier)) {
                idToRemove = so.getKey();
                break;
            }
        }
        actionCards.remove(idToRemove);
    }

    public void removePromissoryNote(Integer identifier) {
        String idToRemove = "";
        for (Map.Entry<String, Integer> so : promissoryNotes.entrySet()) {
            if (so.getValue().equals(identifier)) {
                idToRemove = so.getKey();
                break;
            }
        }
        promissoryNotes.remove(idToRemove);
    }

    public void removePromissoryNote(String id) {
        promissoryNotes.remove(id);
        removePromissoryNotesInPlayArea(id);
    }

    public int getMaxSOCount() {
        int maxSOCount = getGame().getMaxSOCountPerPlayer();
        if (hasRelic("obsidian"))
            maxSOCount++;
        if (hasRelic("absol_obsidian"))
            maxSOCount++;
        if (hasAbility("information_brokers"))
            maxSOCount++;
        return maxSOCount;
    }

    public Map<String, Integer> getSecrets() {
        return secrets;
    }

    public void setSecret(String id) {

        Collection<Integer> values = secrets.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        secrets.put(id, identifier);
    }

    public void setSecret(String id, Integer identifier) {
        secrets.put(id, identifier);
    }

    public void removeSecret(Integer identifier) {
        String idToRemove = "";
        for (Map.Entry<String, Integer> so : secrets.entrySet()) {
            if (so.getValue().equals(identifier)) {
                idToRemove = so.getKey();
                break;
            }
        }
        secrets.remove(idToRemove);
    }

    public SecretObjectiveModel getSecret(Integer identifier) {
        String idToRemove = "";
        for (Map.Entry<String, Integer> so : secrets.entrySet()) {
            if (so.getValue().equals(identifier)) {
                idToRemove = so.getKey();
                break;
            }
        }
        return Mapper.getSecretObjective(idToRemove);
    }

    public Map<String, Integer> getSecretsScored() {
        return secretsScored;
    }

    @JsonIgnore
    public Map<String, Integer> getSecretsUnscored() {
        Map<String, Integer> secretsUnscored = new HashMap<>();
        for (Map.Entry<String, Integer> secret : secrets.entrySet()) {
            if (!secretsScored.containsKey(secret.getKey())) {
                secretsUnscored.put(secret.getKey(), secret.getValue());
            }
        }
        return secretsUnscored;
    }

    public void setSecretScored(String id) {
        Collection<Integer> values = secretsScored.values();
        List<Integer> allIDs = getGame().getPlayers().values().stream()
            .flatMap(player -> player.getSecretsScored().values().stream()).toList();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier) || allIDs.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        secretsScored.put(id, identifier);
    }

    public void setSecretScored(String id, Integer identifier) {
        secretsScored.put(id, identifier);
    }

    public void removeSecretScored(Integer identifier) {
        String idToRemove = "";
        for (Map.Entry<String, Integer> so : secretsScored.entrySet()) {
            if (so.getValue().equals(identifier)) {
                idToRemove = so.getKey();
                break;
            }
        }
        secretsScored.remove(idToRemove);
    }

    public int getCrf() {
        return crf;
    }

    public int getIrf() {
        return irf;
    }

    public int getHrf() {
        return hrf;
    }

    public int getUrf() {
        return vrf;
    }

    public List<String> getFragments() {
        return fragments;
    }

    @JsonIgnore
    public boolean enoughFragsForRelic() {
        boolean enough = false;
        int haz = 0;
        int ind = 0;
        int cult = 0;
        int frontier = 0;
        for (String id : fragments) {
            switch (Mapper.getExplore(id).getType().toLowerCase()) {
                case "cultural" -> cult++;
                case "industrial" -> ind++;
                case "hazardous" -> haz++;
                case "frontier" -> frontier++;
            }
        }
        int targetToHit = 3 - frontier;
        if (hasAbility("fabrication") || getPromissoryNotes().containsKey("bmf")) {
            targetToHit = targetToHit - 1;
        }
        if (haz >= targetToHit || cult >= targetToHit || ind >= targetToHit) {
            enough = true;
        }

        return enough;
    }

    public int getNumberOfBluePrints() {
        int count = 0;
        if (hasFoundCulFrag) {
            count++;
        }

        if (hasFoundHazFrag) {
            count++;
        }
        if (hasFoundIndFrag) {
            count++;
        }
        if (hasFoundUnkFrag) {
            count++;
        }
        return count;
    }

    public void setFragments(List<String> fragmentList) {
        fragments = fragmentList;
        updateFragments();
    }

    public void addFragment(String fragmentID) {
        fragments.add(fragmentID);
        updateFragments();
    }

    public void removeFragment(String fragmentID) {
        fragments.remove(fragmentID);
        updateFragments();
    }

    private void updateFragments() {
        crf = irf = hrf = vrf = 0;
        for (String cardID : fragments) {
            String color = Mapper.getExplore(cardID).getType().toLowerCase();
            switch (color) {
                case Constants.CULTURAL -> {
                    crf++;
                    if (!hasFoundCulFrag) {
                        hasFoundCulFrag = true;
                        if (hasUnit("bentor_mech")) {
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(this, getGame(),
                                "mech", "placeOneNDone_skipbuild"));
                            String message = getRepresentation() + " due tp your mech deploy ability, you can now place a mech on a planet you control";
                            MessageHelper.sendMessageToChannel(getCorrectChannel(), message, buttons);
                        }
                    }
                }
                case Constants.INDUSTRIAL -> {
                    irf++;
                    if (!hasFoundIndFrag) {
                        hasFoundIndFrag = true;
                        if (hasUnit("bentor_mech")) {
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(this, getGame(),
                                "mech", "placeOneNDone_skipbuild"));
                            String message = getRepresentation() + " due tp your mech deploy ability, you can now place a mech on a planet you control";
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(this, getGame()), message, buttons);
                        }
                    }
                }
                case Constants.HAZARDOUS -> {
                    hrf++;
                    if (!hasFoundHazFrag) {
                        hasFoundHazFrag = true;
                        if (hasUnit("bentor_mech")) {
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(this, getGame(),
                                "mech", "placeOneNDone_skipbuild"));
                            String message = getRepresentation() + " due tp your mech deploy ability, you can now place a mech on a planet you control";
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(this, getGame()), message, buttons);
                        }
                    }
                }
                case Constants.FRONTIER -> {
                    vrf++;
                    if (!hasFoundUnkFrag) {
                        hasFoundUnkFrag = true;
                        if (hasUnit("bentor_mech")) {
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(this, getGame(),
                                "mech", "placeOneNDone_skipbuild"));
                            String message = getRepresentation() + " due tp your mech deploy ability, you can now place a mech on a planet you control";
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(this, getGame()), message, buttons);
                        }
                    }
                }
            }
        }
    }

    public void addRelic(String relicID) {
        if (!relics.contains(relicID) || Constants.ENIGMATIC_DEVICE.equals(relicID)) {
            if ("dynamiscore".equals(relicID) || "absol_dynamiscore".equals(relicID)) {
                setCommoditiesTotal(getCommoditiesTotal() + 2);
            }
            relics.add(relicID);
        }
    }

    public void addExhaustedRelic(String relicID) {
        exhaustedRelics.add(relicID);
    }

    public void removeRelic(String relicID) {
        if ("dynamiscore".equals(relicID) || "absol_dynamiscore".equals(relicID)) {
            setCommoditiesTotal(getCommoditiesTotal() - 2);
        }
        relics.remove(relicID);
    }

    public void removeExhaustedRelic(String relicID) {
        exhaustedRelics.remove(relicID);
    }

    public List<String> getRelics() {
        return relics;
    }

    public List<String> getExhaustedRelics() {
        return exhaustedRelics;
    }

    @JsonIgnore
    public User getUser() {
        return AsyncTI4DiscordBot.jda.getUserById(userID);
    }

    public String getUserID() {
        return userID;
    }

    public String getUserName() {
        User userById = AsyncTI4DiscordBot.jda.getUserById(userID);
        if (userById != null) {
            userName = userById.getName();
            Member member = AsyncTI4DiscordBot.guildPrimary.getMemberById(userID);
            if (member != null)
                userName = member.getEffectiveName();
        }
        return userName;
    }

    public String getFaction() {
        return faction;
    }

    @JsonIgnore
    public FactionModel getFactionModel() {
        return Mapper.getFaction(faction);
    }

    public void setFaction(String faction) {
        this.faction = faction;
        initLeaders();
        initAbilities();
    }

    @JsonIgnore
    public String getRepresentation() {
        return getRepresentation(false, true);
    }

    @JsonIgnore
    public String getRepresentationNoPing() {
        return getRepresentation(false, false);
    }

    public String getRepresentation(boolean overrideFow, boolean ping) {
        Game game = getGame();
        boolean privateGame = FoWHelper.isPrivateGame(game);
        if (privateGame && !overrideFow) {
            return Emojis.getColorEmojiWithName(getColor());
        }

        if (game != null && game.isCommunityMode()) {
            Role roleForCommunity = getRoleForCommunity();
            if (roleForCommunity == null && !getTeamMateIDs().isEmpty()) {
                StringBuilder sb = new StringBuilder(getFactionEmoji());
                for (String userID : getTeamMateIDs()) {
                    User userById = AsyncTI4DiscordBot.jda.getUserById(userID);
                    if (userById == null) {
                        continue;
                    }
                    if (ping) {
                        sb.append(" ").append(userById.getAsMention());
                    } else {
                        sb.append(" ").append(userById.getEffectiveName());
                    }
                }
                if (getColor() != null && !"null".equals(getColor())) {
                    sb.append(" ").append(Emojis.getColorEmojiWithName(getColor()));
                }
                return sb.toString();
            } else if (roleForCommunity != null) {
                return getFactionEmoji() + " " + roleForCommunity.getAsMention() + " "
                    + Emojis.getColorEmojiWithName(getColor());
            } else {
                return getFactionEmoji() + " " + Emojis.getColorEmojiWithName(getColor());
            }
        }

        // DEFAULT REPRESENTATION
        StringBuilder sb = new StringBuilder(getFactionEmoji());
        if (ping) {
            sb.append(getPing());
        } else {
            sb.append(getUserName());
        }
        sb.append(getGlobalUserSetting("emojiAfterName").orElse(""));
        if (getColor() != null && !"null".equals(getColor())) {
            sb.append(" ").append(Emojis.getColorEmojiWithName(getColor()));
        }
        return sb.toString();
    }

    @JsonIgnore
    public String getPing() {
        User userById = AsyncTI4DiscordBot.jda.getUserById(getUserID());
        if (userById == null)
            return "";

        StringBuilder sb = new StringBuilder(userById.getAsMention());
        switch (getUserID()) {
            case "154000388121559040" -> sb.append(Emojis.BortWindow); // mysonisalsonamedbort
            case "150809002974904321" -> sb.append(Emojis.SpoonAbides); // tispoon
            case "228999251328368640" -> sb.append(Emojis.Scout); // Jazzx
        }
        return sb.toString();
    }

    @NotNull
    public String getFactionEmoji() {
        String emoji = null;
        if (StringUtils.isNotBlank(factionEmoji) && !"null".equals(factionEmoji)) {
            emoji = factionEmoji;
        }
        if (emoji == null && getFactionModel() != null) {
            emoji = getFactionModel().getFactionEmoji();
        }
        return emoji != null ? emoji : Emojis.getFactionIconFromDiscord(faction);
    }

    public String getFactionEmojiOrColor() {
        if (getGame().isFoWMode() || FoWHelper.isPrivateGame(getGame())) {
            return Emojis.getColorEmojiWithName(getColor());
        }
        return getFactionEmoji();
    }

    public void setFactionEmoji(String factionEmoji) {
        this.factionEmoji = factionEmoji;
    }

    public String getFactionEmojiRaw() {
        return factionEmoji;
    }

    public boolean hasCustomFactionEmoji() {
        return StringUtils.isNotBlank(factionEmoji) && !"null".equals(factionEmoji)
            && getFactionModel() != null && !factionEmoji.equalsIgnoreCase(getFactionModel().getFactionEmoji());
    }

    private void initAbilities() {
        Set<String> abilities = new HashSet<>();
        for (String ability : getFactionStartingAbilities()) {
            if (!ability.isEmpty() && !ability.isBlank()) {
                abilities.add(ability);
            }
        }
        setAbilities(abilities);
        if (hasAbility("cunning")) {
            Map<String, String> dsHandcards = Mapper.getDSHandcards();
            for (Entry<String, String> entry : dsHandcards.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(Constants.LIZHO)) {
                    setTrapCard(key);
                }
            }
        }
    }

    @JsonIgnore
    public FactionModel getFactionSetupInfo() {
        if (faction == null || "null".equals(faction) || "keleres".equals(faction))
            return null;
        FactionModel factionSetupInfo = Mapper.getFaction(faction);
        if (factionSetupInfo == null) {
            BotLogger.log("Could not get faction setup info for: " + faction);
            return null;
        }
        return factionSetupInfo;
    }

    private List<String> getFactionStartingAbilities() {
        FactionModel factionSetupInfo = getFactionSetupInfo();
        if (factionSetupInfo == null)
            return new ArrayList<>();
        return new ArrayList<>(factionSetupInfo.getAbilities());
    }

    private List<String> getFactionStartingLeaders() {
        FactionModel factionSetupInfo = getFactionSetupInfo();
        if (factionSetupInfo == null)
            return new ArrayList<>();
        return new ArrayList<>(factionSetupInfo.getLeaders());
    }

    public void initLeaders() {
        leaders.clear();
        for (String leaderID : getFactionStartingLeaders()) {
            Leader leader = new Leader(leaderID);
            leaders.add(leader);
        }
    }

    @Nullable
    public Leader unsafeGetLeader(String leaderIdOrType) {
        return getLeader(leaderIdOrType).orElse(null);
    }

    public Optional<Leader> getLeader(String leaderIdOrType) {
        Optional<Leader> leader = getLeaderByID(leaderIdOrType);
        if (leader.isEmpty()) {
            leader = getLeaderByType(leaderIdOrType);
        }
        if (leader.isEmpty() && leaderIdOrType.contains("agent")) {
            leader = getLeaderByID("yssarilagent");
        }
        return leader;
    }

    public boolean isAFK() {
        if (getHoursThatPlayerIsAFK().length() < 1) {
            return false;
        }
        String[] hoursAFK = getHoursThatPlayerIsAFK().split(";");
        int currentHour = Helper.getCurrentHour();
        for (String hour : hoursAFK) {
            int h = Integer.parseInt(hour);
            if (h == currentHour) {
                return true;
            }
        }
        return false;
    }

    public boolean hasUnexhaustedLeader(String leaderId) {
        if (hasLeader(leaderId)) {
            return !getLeaderByID(leaderId).map(Leader::isExhausted).orElse(true);
        } else {
            return hasExternalAccessToLeader(leaderId)
                && !getLeaderByID("yssarilagent").map(Leader::isExhausted).orElse(true);
        }
    }

    public Optional<Leader> getLeaderByType(String leaderType) {
        for (Leader leader : leaders) {
            if (leader.getType().equals(leaderType)) {
                return Optional.of(leader);
            }
        }
        return Optional.empty();
    }

    public Optional<Leader> getLeaderByID(String leaderID) {
        for (Leader leader : leaders) {
            if (leader.getId().equals(leaderID)) {
                return Optional.of(leader);
            }
        }
        if (leaderID.contains("agent")) {
            leaderID = "yssarilagent";
            for (Leader leader : leaders) {
                if (leader.getId().equals(leaderID)) {
                    return Optional.of(leader);
                }
            }
        }
        return Optional.empty();
    }

    public List<Leader> getLeaders() {
        return leaders;
    }

    public List<String> getLeaderIDs() {
        return getLeaders().stream().map(Leader::getId).toList();
    }

    /**
     * @param leaderID
     * @return whether a player has access to this leader, typically by way of
     *         Yssaril Agent
     */
    public boolean hasExternalAccessToLeader(String leaderID) {
        if (!hasLeader(leaderID) && leaderID.contains("agent") && getLeaderIDs().contains("yssarilagent")) {
            return getGame().isLeaderInGame(leaderID);
        }
        return false;
    }

    public boolean hasLeader(String leaderID) {
        return getLeaderIDs().contains(leaderID);
    }

    public boolean hasLeaderUnlocked(String leaderID) {
        return hasLeader(leaderID) && !getLeader(leaderID).map(Leader::isLocked).orElse(true);
    }

    public void setLeaders(List<Leader> leaders) {
        leaders.sort(Leader.sortByType());
        this.leaders = leaders;
    }

    public boolean removeLeader(String leaderID) {
        Leader leaderToPurge = null;
        for (Leader leader : leaders) {
            if (leader.getId().equals(leaderID)) {
                leaderToPurge = leader;
                break;
            }
        }
        if (leaderToPurge == null) {
            return false;
        }
        return leaders.remove(leaderToPurge);
    }

    public boolean removeLeader(Leader leader) {
        return leaders.remove(leader);
    }

    public void addLeader(String leaderID) {
        if (!getLeaderIDs().contains(leaderID)) {
            Leader leader = new Leader(leaderID);
            leaders.add(leader);
        }
    }

    public void addLeader(Leader leader) {
        if (!getLeaderIDs().contains(leader.getId())) {
            leaders.add(leader);
        }
    }

    public String getColor() {
        return color != null ? color : "null";
    }

    public String getColorID() {
        return color != null ? Mapper.getColorID(color) : "null";
    }

    public void setColor(String color) {
        if (!"null".equals(color)) {
            this.color = AliasHandler.resolveColor(color);
        }
    }

    public void addAllianceMember(String color) {
        if (!"null".equals(color)) {
            allianceMembers = allianceMembers + color;
        }
    }

    public void addHourThatIsAFK(String hour) {
        if (hoursThatPlayerIsAFK.length() < 1) {
            hoursThatPlayerIsAFK = hour;
        } else {
            hoursThatPlayerIsAFK = hoursThatPlayerIsAFK + ";" + hour;
        }
    }

    public void setHoursThatPlayerIsAFK(String hours) {
        hoursThatPlayerIsAFK = hours;
    }

    public String getHoursThatPlayerIsAFK() {
        return hoursThatPlayerIsAFK;
    }

    public void setAllianceMembers(String color) {
        if (!"null".equals(color)) {
            allianceMembers = color;
        }
    }

    public String getAllianceMembers() {
        return allianceMembers;
    }

    public void removeAllianceMember(String color) {
        if (!"null".equals(color)) {
            allianceMembers = allianceMembers.replace(color, "");
        }
    }

    public void changeColor(String color) {
        if (!"null".equals(color)) {
            this.color = AliasHandler.resolveColor(color);
        }
    }

    public void initPNs() {
        if (getGame() != null && color != null && faction != null && Mapper.isValidColor(color)
            && Mapper.isValidFaction(faction)) {
            promissoryNotes.clear();
            List<String> promissoryNotes = Mapper.getColorPromissoryNoteIDs(getGame(), color);
            for (String promissoryNote : promissoryNotes) {
                if (promissoryNote.endsWith("_an") && hasAbility("hubris")) {
                    continue;
                }
                if ("blood_pact".equalsIgnoreCase(promissoryNote) && !hasAbility("dark_whispers")) {
                    continue;
                }
                if (promissoryNote.endsWith("_sftt") && hasAbility("enlightenment")) {
                    continue;
                }
                setPromissoryNote(promissoryNote);
            }
        }
    }

    public String getCCRepresentation() {
        return getTacticalCC() + "/" + getFleetCC() + "/" + getStrategicCC();
    }

    public int getTacticalCC() {
        return tacticalCC;
    }

    public void setTacticalCC(int tacticalCC) {
        this.tacticalCC = tacticalCC;
    }

    public int getFleetCC() {
        return fleetCC;
    }

    public void setFleetCC(int fleetCC) {
        this.fleetCC = fleetCC;
    }

    public int getStrategicCC() {
        return strategicCC;
    }

    public void setStrategicCC(int strategicCC) {
        this.strategicCC = strategicCC;
    }

    public int getTg() {
        return tg;
    }

    public int getPersonalPingInterval() {
        return personalPingInterval;
    }

    public void setPersonalPingInterval(int interval) {
        personalPingInterval = interval;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public int getActualHits() {
        return actualHits;
    }

    public int getExpectedHitsTimes10() {
        return expectedHitsTimes10;
    }

    public int getTotalExpenses() {
        return totalExpenses;
    }

    @JsonIgnore
    public int getPublicVictoryPoints(boolean countCustoms) {
        Game game = getGame();
        Map<String, List<String>> scoredPOs = game.getScoredPublicObjectives();
        int vpCount = 0;
        for (Entry<String, List<String>> scoredPOEntry : scoredPOs.entrySet()) {
            if (scoredPOEntry.getValue().contains(getUserID())) {
                String poID = scoredPOEntry.getKey();
                try {
                    PublicObjectiveModel po = Mapper.getPublicObjective(poID);
                    if (po != null) {// IS A PO
                        vpCount += po.getPoints();
                    } else { // IS A CUSTOM PO
                        if (countCustoms) {
                            int frequency = Collections.frequency(scoredPOEntry.getValue(), userID);
                            int poValue = game.getCustomPublicVP().getOrDefault(poID, 0);
                            vpCount += poValue * frequency;
                        }
                    }
                } catch (Exception e) {
                    BotLogger.log("`Player.getPublicVictoryPoints   map=" + game.getName() + "  player="
                        + getUserName() + "` - error finding value of `PO_ID=" + poID, e);
                }
            }
        }

        return vpCount;
    }

    @JsonIgnore
    public int getSecretVictoryPoints() {
        Map<String, Integer> scoredSecrets = getSecretsScored();
        for (String id : getGame().getSoToPoList()) {
            scoredSecrets.remove(id);
        }
        return scoredSecrets.size();
    }

    @JsonIgnore
    public int getSupportForTheThroneVictoryPoints() {
        List<String> promissoryNotesInPlayArea = getPromissoryNotesInPlayArea();
        int vpCount = 0;
        for (String id : promissoryNotesInPlayArea) {
            if (id.endsWith("_sftt")) {
                vpCount++;
            }
        }
        return vpCount;
    }

    @JsonIgnore
    public int getTotalVictoryPoints() {
        return getPublicVictoryPoints(true) + getSecretVictoryPoints() + getSupportForTheThroneVictoryPoints();
    }

    public void setTg(int tg) {
        this.tg = tg;
    }

    @JsonIgnore
    public String gainTG(int count) {
        String message = "(" + getTg() + " -> " + (getTg() + count) + ")";
        this.tg += count;
        return message;
    }

    public void setTurnCount(int turn) {
        turnCount = turn;
    }

    public void setActualHits(int tg) {
        actualHits = tg;
    }

    public void setExpectedHitsTimes10(int tg) {
        expectedHitsTimes10 = tg;
    }

    public void setTotalExpenses(int tg) {
        totalExpenses = tg;
    }

    public void setFollowedSCs(Set<Integer> followedSCs) {
        this.followedSCs = followedSCs;
    }

    public void addFollowedSC(Integer sc) {
        followedSCs.add(sc);
    }

    public void removeFollowedSC(Integer sc) {
        followedSCs.remove(sc);
    }

    public boolean hasFollowedSC(int sc) {
        return getFollowedSCs().contains(sc);
    }

    public void clearFollowedSCs() {
        followedSCs.clear();
    }

    public Set<Integer> getFollowedSCs() {
        return followedSCs;
    }

    @JsonIgnore
    public int getAc() {
        return actionCards.size();
    }

    @JsonIgnore
    public int getPnCount() {
        return (promissoryNotes.size() - promissoryNotesInPlayArea.size());
    }

    @JsonIgnore
    public int getSo() {
        return secrets.size();
    }

    @JsonIgnore
    public int getSoScored() {
        return secretsScored.size();
    }

    public Set<Integer> getSCs() {
        return SCs;
    }

    public void setSCs(Set<Integer> SCs) {
        this.SCs = new LinkedHashSet<>(SCs);
        this.SCs.remove(0); // TEMPORARY MIGRATION TO REMOVE 0 IF PLAYER HAS IT FROM OLD SAVES
    }

    public void addSC(int sc) {
        SCs.add(sc);
    }

    public void removeSC(int sc) {
        SCs.remove(sc);
    }

    public void clearSCs() {
        SCs.clear();
    }

    public int getLowestSC() {
        try {
            int min = 100;
            Game game = getGame();
            for (int SC : getSCs()) {
                if (SC == ButtonHelper.getKyroHeroSC(game)) {
                    min = Math.min(game.getSCList().size() + 1, min);
                } else {
                    min = Math.min(SC, min);
                }
            }
            return min;
        } catch (NoSuchElementException e) {
            return 100;
        }
    }

    public int getCommodities() {
        return commodities;
    }

    public void setCommodities(int comms) {
        if (comms > commoditiesTotal && commoditiesTotal > 0) {
            comms = commoditiesTotal;
        }
        if (comms < 0) {
            comms = 0;
        }
        commodities = comms;
    }

    public List<String> getTechs() {
        return techs;
    }

    public List<String> getTeamMateIDs() {
        if (!teamMateIDs.contains(getUserID())) {
            teamMateIDs.add(getUserID());
        }
        return teamMateIDs;
    }

    public List<String> getNotResearchedFactionTechs() {
        return getFactionTechs().stream().filter(tech -> !hasTech(tech)).toList();
    }

    public DraftBag getDraftHand() {
        return draftHand;
    }

    public void setDraftHand(DraftBag hand) {
        draftHand = hand;
    }

    public DraftBag getCurrentDraftBag() {
        return currentDraftBag;
    }

    public void setCurrentDraftBag(DraftBag bag) {
        currentDraftBag = bag;
    }

    public DraftBag getDraftQueue() {
        return draftItemQueue;
    }

    public boolean hasTech(String techID) {
        if ("det".equals(techID) || "amd".equals(techID)) {
            if (techs.contains("absol_" + techID)) {
                return true;
            }
        }
        return techs.contains(techID);
    }

    public boolean hasTechReady(String techID) {
        return hasTech(techID) && !exhaustedTechs.contains(techID);
    }

    public List<String> getPlanets() {
        return planets;
    }

    public boolean isPlayerMemberOfAlliance(Player player2) {
        return allianceMembers.contains(player2.getFaction());
    }

    public List<String> getPlanetsAllianceMode() {
        List<String> newPlanets = new ArrayList<>(planets);
        if (!"".equalsIgnoreCase(allianceMembers)) {
            for (Player player2 : getGame().getRealPlayers()) {
                if (getAllianceMembers().contains(player2.getFaction())) {
                    newPlanets.addAll(player2.getPlanets());
                }
            }
        }
        return newPlanets;
    }

    public void setPlanets(List<String> planets) {
        this.planets = planets;
    }

    public void loadDraftHand(List<String> saveString) {
        DraftBag newBag = new DraftBag();
        for (String item : saveString) {
            newBag.Contents.add(DraftItem.GenerateFromAlias(item));
        }
        draftHand = newBag;
    }

    public void loadCurrentDraftBag(List<String> saveString) {
        DraftBag newBag = new DraftBag();
        for (String item : saveString) {
            newBag.Contents.add(DraftItem.GenerateFromAlias(item));
        }
        currentDraftBag = newBag;
    }

    public void loadItemsToDraft(List<String> saveString) {
        List<DraftItem> items = new ArrayList<>();
        for (String item : saveString) {
            items.add(DraftItem.GenerateFromAlias(item));
        }
        draftItemQueue.Contents = items;
    }

    public void queueDraftItem(DraftItem item) {
        draftItemQueue.Contents.add(item);
    }

    public void resetDraftQueue() {
        draftItemQueue.Contents.clear();
    }

    @JsonIgnore
    public List<String> getReadiedPlanets() {
        List<String> planets = new ArrayList<>(getPlanets());
        planets.removeAll(getExhaustedPlanets());
        return planets;
    }

    public List<String> getExhaustedPlanets() {
        return exhaustedPlanets;
    }

    public void setExhaustedPlanets(List<String> exhaustedPlanets) {
        this.exhaustedPlanets = exhaustedPlanets;
    }

    public List<String> getExhaustedPlanetsAbilities() {
        return exhaustedPlanetsAbilities;
    }

    public void setExhaustedPlanetsAbilities(List<String> exhaustedPlanetsAbilities) {
        this.exhaustedPlanetsAbilities = exhaustedPlanetsAbilities;
    }

    public void setTechs(List<String> techs) {
        this.techs = techs;
    }

    public void setTeamMateIDs(List<String> techs) {
        List<String> nonDuplicates = new ArrayList<>();
        for (String id : techs) {
            if (!nonDuplicates.contains(id)) {
                nonDuplicates.add(id);
            }
        }
        teamMateIDs = nonDuplicates;
    }

    public void setRelics(List<String> relics) {
        this.relics = relics;
    }

    public void setExhaustedRelics(List<String> exhaustedRelics) {
        this.exhaustedRelics = exhaustedRelics;
    }

    public boolean hasRelic(String relicID) {
        return relics.contains(relicID);
    }

    public boolean hasRelicReady(String relicID) {
        return hasRelic(relicID) && !exhaustedRelics.contains(relicID);
    }

    public List<String> getExhaustedTechs() {
        return exhaustedTechs;
    }

    public void cleanExhaustedTechs() {
        exhaustedTechs.clear();
    }

    public void cleanExhaustedPlanets(boolean cleanAbilities) {
        exhaustedPlanets.clear();
        if (cleanAbilities) {
            exhaustedPlanetsAbilities.clear();
        }
    }

    public void cleanExhaustedRelics() {
        exhaustedRelics.clear();
    }

    public void setExhaustedTechs(List<String> exhaustedTechs) {
        this.exhaustedTechs = exhaustedTechs;
    }

    public void addFactionTech(String techID) {
        if (factionTechs.contains(techID))
            return;
        factionTechs.add(techID);
    }

    public boolean removeFactionTech(String techID) {
        return factionTechs.remove(techID);
    }

    public void addTeamMateID(String techID) {
        teamMateIDs.add(techID);
    }

    public void removeTeamMateID(String techID) {
        teamMateIDs.remove(techID);
    }

    public void addTech(String techID) {
        if (techs.contains(techID)) {
            return;
        }
        techs.add(techID);

        doAdditionalThingsWhenAddingTech(techID);
    }

    private void doAdditionalThingsWhenAddingTech(String techID) {
        // Set ATS Armaments to 0 when adding tech (if it was removed we reset it)
        if ("dslaner".equalsIgnoreCase(techID)) {
            setAtsCount(0);
        }

        // Add Custodia Vigilia when researching IIHQ
        if ("iihq".equalsIgnoreCase(techID)) {
            addPlanet("custodiavigilia");
            exhaustPlanet("custodiavigilia");

            if (getPlanets().contains(Constants.MR)) {
                Planet mecatolRex = (Planet) getGame().getPlanetsInfo().get(Constants.MR);
                if (mecatolRex != null) {
                    PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                    mecatolRex.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                    mecatolRex.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
                }
            }
        }

        // Update Owned Units when Researching a Unit Upgrade
        TechnologyModel techModel = Mapper.getTech(techID);
        if (techID == null)
            return;

        if (techModel.getType() == TechnologyType.UNITUPGRADE) {
            UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
            if (unitModel != null) {
                // Remove all non-faction-upgrade matching units
                String asyncId = unitModel.getAsyncId();
                List<UnitModel> unitsToRemove = getUnitsByAsyncID(asyncId).stream()
                    .filter(unit -> unit.getFaction().isEmpty() || unit.getUpgradesFromUnitId().isEmpty()).toList();
                for (UnitModel u : unitsToRemove) {
                    removeOwnedUnitByID(u.getId());
                }

                addOwnedUnitByID(unitModel.getId());
            }
        }
    }

    // Provided because people make mistakes, also nekro exists, also weird homebrew
    // exists
    private void doAdditionalThingsWhenRemovingTech(String techID) {
        // Remove Custodia Vigilia when un-researching IIHQ
        if ("iihq".equalsIgnoreCase(techID)) {
            removePlanet("custodiavigilia");
            if (getPlanets().contains(Constants.MR)) {
                Planet mecatolRex = (Planet) getGame().getPlanetsInfo().get(Constants.MR);
                if (mecatolRex != null) {
                    mecatolRex.setSpaceCannonDieCount(0);
                    mecatolRex.setSpaceCannonHitsOn(0);
                }
            }
        }

        // Update Owned Units when Researching a Unit Upgrade
        TechnologyModel techModel = Mapper.getTech(techID);
        if (techID == null)
            return;

        if (techModel.getType() == TechnologyType.UNITUPGRADE) {
            UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
            List<TechnologyModel> relevantTechs = getTechs().stream().map(Mapper::getTech)
                .filter(tech -> tech.getBaseUpgrade().orElse("").equals(unitModel.getBaseType())).toList();
            removeOwnedUnitByID(unitModel.getId());

            // Find another unit model to replace this lost model
            String replacementUnit = unitModel.getBaseType(); // default
            if (unitModel.getUpgradesFromUnitId().isPresent() && !unitModel.getUpgradesFromUnitId().isEmpty()) {
                addOwnedUnitByID(unitModel.getUpgradesFromUnitId().orElse(replacementUnit));
                return;
            }
            if (relevantTechs.isEmpty() && unitModel.getBaseType() != null) {
                // No other relevant unit upgrades
                FactionModel factionSetup = getFactionSetupInfo();
                replacementUnit = factionSetup.getUnits().stream().map(Mapper::getUnit)
                    .map(UnitModel::getId)
                    .filter(id -> id.equals(unitModel.getBaseType())).findFirst()
                    .orElse(replacementUnit);
            } else if (relevantTechs.size() > 0) {
                // Ignore the case where there's multiple faction techs and also
                replacementUnit = relevantTechs.stream().min(TechnologyModel::sortFactionTechsFirst)
                    .map(TechnologyModel::getAlias)
                    .map(Mapper::getUnitModelByTechUpgrade)
                    .map(UnitModel::getId)
                    .orElse(replacementUnit);
            }
            addOwnedUnitByID(replacementUnit);
        }
    }

    public void exhaustTech(String tech) {
        if (techs.contains(tech) && !exhaustedTechs.contains(tech)) {
            exhaustedTechs.add(tech);
        }
    }

    public void refreshTech(String tech) {
        boolean isRemoved = exhaustedTechs.remove(tech);
        if (isRemoved)
            refreshTech(tech);
    }

    public void removeTech(String tech) {
        techs.remove(tech);
        doAdditionalThingsWhenRemovingTech(tech);
    }

    public void addPlanet(String planet) {
        if (!planets.contains(planet)) {
            planets.add(planet);
        }
    }

    public void exhaustPlanet(String planet) {
        if (planets.contains(planet) && !exhaustedPlanets.contains(planet)) {
            exhaustedPlanets.add(planet);
        }
        Game game = getGame();
        if (ButtonHelper.getUnitHolderFromPlanetName(planet, game) != null && game.isAbsolMode()
            && ButtonHelper.getUnitHolderFromPlanetName(planet, game).getTokenList()
                .contains("attachment_nanoforge.png")
            && !getExhaustedPlanetsAbilities().contains(planet)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("planetAbilityExhaust_" + planet, "Use Nanoforge Ability"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(this, game),
                getRepresentation() + " You can choose to Exhaust Nanoforge Ability to ready the planet", buttons);
        }
    }

    public void exhaustPlanetAbility(String planet) {
        if (planets.contains(planet) && !exhaustedPlanetsAbilities.contains(planet)) {
            exhaustedPlanetsAbilities.add(planet);
        }
    }

    public void refreshPlanet(String planet) {
        boolean isRemoved = exhaustedPlanets.remove(planet);
        if (isRemoved)
            refreshPlanet(planet);
    }

    public void refreshPlanetAbility(String planet) {
        boolean isRemoved = exhaustedPlanetsAbilities.remove(planet);
        if (isRemoved)
            refreshPlanetAbility(planet);
    }

    public void removePlanet(String planet) {
        planets.remove(planet);
        refreshPlanet(planet);
        refreshPlanetAbility(planet);
    }

    public int getStasisInfantry() {
        return stasisInfantry;
    }

    public int getAutoSaboPassMedian() {
        return autoSaboPassMedian;
    }

    public void setAutoSaboPassMedian(int median) {
        autoSaboPassMedian = median;
        // setGlobalUserSetting("autoSaboPassMedianHours", String.valueOf(median));
    }

    public void setStasisInfantry(int stasisInfantry) {
        this.stasisInfantry = stasisInfantry;
    }

    public int getCommoditiesTotal() {
        return commoditiesTotal;
    }

    public void setCommoditiesTotal(int commoditiesTotal) {
        this.commoditiesTotal = commoditiesTotal;
    }

    public void setSearchWarrant() {
        searchWarrant = !searchWarrant;
    }

    public void setSearchWarrant(boolean value) {
        searchWarrant = value;
    }

    public boolean isSearchWarrant() {
        return searchWarrant;
    }

    public void updateFogTile(@NotNull Tile tile, String label) {
        fow_seenTiles.put(tile.getPosition(), tile.getTileID());
        if (label == null) {
            fow_customLabels.remove(tile.getPosition());
        } else {
            fow_customLabels.put(tile.getPosition(), label);
        }
    }

    public void addFogTile(String tileID, String position, String label) {
        fow_seenTiles.put(position, tileID);
        if (label != null && !".".equals(label) && !"".equals(label)) {
            fow_customLabels.put(position, label);
        }
    }

    public void removeFogTile(String position) {
        fow_seenTiles.remove(position);
        fow_customLabels.remove(position);
    }

    @JsonIgnore
    public Tile buildFogTile(String position, Player player) {

        String tileID = fow_seenTiles.get(position);
        if (tileID == null)
            tileID = "0b";

        String label = fow_customLabels.get(position);
        if (label == null)
            label = "";

        return new Tile(tileID, position, player, true, label);
    }

    public Map<String, String> getFogTiles() {
        return fow_seenTiles;
    }

    public Map<String, String> getFogLabels() {
        return fow_customLabels;
    }

    public boolean hasFogInitialized() {
        return fogInitialized;
    }

    public void setFogInitialized(boolean init) {
        fogInitialized = init;
    }

    public boolean isDummy() {
        return isDummy;
    }

    public void setDummy(boolean isDummy) {
        this.isDummy = isDummy;
    }

    /**
     * @return true if the player is: not a "dummy", faction != null, color != null,
     *         & color != "null"
     */
    @JsonIgnore
    public boolean isRealPlayer() {
        return !(isDummy || faction == null || color == null || "null".equals(color));
    }

    /**
     * @return true if the player is: a "dummy", faction == null, color == null, &
     *         color == "null"
     */
    @JsonIgnore
    public boolean isNotRealPlayer() {
        return !isRealPlayer();
    }

    public void setFogFilter(String preference) {
        fowFogFilter = preference;
    }

    public String getFogFilter() {
        return fowFogFilter == null ? "default" : fowFogFilter;
    }

    public void updateTurnStats(long turnTime) {
        numberOfTurns++;
        totalTimeSpent += turnTime;
    }

    public int getNumberTurns() {
        return numberOfTurns;
    }

    public void setNumberTurns(int numTurns) {
        numberOfTurns = numTurns;
    }

    public long getTotalTurnTime() {
        return totalTimeSpent;
    }

    public void setTotalTurnTime(long totalTime) {
        totalTimeSpent = totalTime;
    }

    @JsonIgnore
    public String getAutoCompleteRepresentation() {
        return getAutoCompleteRepresentation(false);
    }

    @JsonIgnore
    public String getAutoCompleteRepresentation(boolean reset) {
        if (reset || autoCompleteRepresentation == null) {
            String faction = getFaction();
            if (faction == null || "null".equals(faction)) {
                faction = "No Faction";
            } else {
                faction = getFactionModel().getFactionName();
            }

            String color = getColor();
            if (color == null || "null".equals(color))
                color = "No Color";

            String userName = getUserName();
            if (userName == null || userName.isEmpty() || userName.isBlank()) {
                userName = "No User";
            }

            String representation = color + " / " + faction + " / " + userName;
            setAutoCompleteRepresentation(representation);
            return getAutoCompleteRepresentation();
        }
        return autoCompleteRepresentation;
    }

    public void setAutoCompleteRepresentation(String representation) {
        autoCompleteRepresentation = representation;
    }

    // BENTOR CONGLOMERATE ABILITY "Ancient Blueprints"
    public boolean hasFoundCulFrag() {
        return hasFoundCulFrag;
    }

    public void setHasFoundCulFrag(boolean hasFoundCulFrag) {
        this.hasFoundCulFrag = hasFoundCulFrag;
    }

    public boolean hasFoundHazFrag() {
        return hasFoundHazFrag;
    }

    public void setHasFoundHazFrag(boolean hasFoundHazFrag) {
        this.hasFoundHazFrag = hasFoundHazFrag;
    }

    public boolean hasFoundIndFrag() {
        return hasFoundIndFrag;
    }

    public void setHasFoundIndFrag(boolean hasFoundIndFrag) {
        this.hasFoundIndFrag = hasFoundIndFrag;
    }

    public boolean hasFoundUnkFrag() {
        return hasFoundUnkFrag;
    }

    public void setHasFoundUnkFrag(boolean hasFoundUnkFrag) {
        this.hasFoundUnkFrag = hasFoundUnkFrag;
    }

    public Map<String, Integer> getDebtTokens() {
        return debt_tokens;
    }

    public void setDebtTokens(Map<String, Integer> debt_tokens) {
        this.debt_tokens = debt_tokens;
    }

    public void addDebtTokens(String tokenColor, int count) {
        if (debt_tokens.containsKey(tokenColor)) {
            debt_tokens.put(tokenColor, debt_tokens.get(tokenColor) + count);
        } else {
            debt_tokens.put(tokenColor, count);
        }
    }

    public void removeDebtTokens(String tokenColor, int count) {
        if (debt_tokens.containsKey(tokenColor)) {
            debt_tokens.put(tokenColor, Math.max(debt_tokens.get(tokenColor) - count, 0));
        }
    }

    public void clearAllDebtTokens(String tokenColor) {
        debt_tokens.remove(tokenColor);
    }

    public int getDebtTokenCount(String tokenColor) {
        return debt_tokens.getOrDefault(tokenColor, 0);
    }

    public String getPlayerStatsAnchorPosition() {
        if ("null".equals(playerStatsAnchorPosition)) {
            return null;
        }
        return playerStatsAnchorPosition;
    }

    public boolean hasOlradinPolicies() {
        return (hasAbility("policies"))
            || (hasAbility("policy_the_people_connect"))
            || (hasAbility("policy_the_environment_preserve"))
            || (hasAbility("policy_the_economy_empower"))
            || (hasAbility("policy_the_people_control"))
            || (hasAbility("policy_the_environment_plunder"))
            || (hasAbility("policy_the_economy_exploit"));
    }

    public void resetOlradinPolicyFlags() {
        setHasUsedEconomyEmpowerAbility(false);
        setHasUsedEconomyExploitAbility(false);
        setHasUsedEnvironmentPreserveAbility(false);
        setHasUsedEnvironmentPlunderAbility(false);
        setHasUsedPeopleConnectAbility(false);
    }

    public boolean getHasUsedEconomyEmpowerAbility() {
        return hasUsedEconomyEmpowerAbility;
    }

    public boolean getHasUsedEconomyExploitAbility() {
        return hasUsedEconomyExploitAbility;
    }

    public boolean getHasUsedEnvironmentPreserveAbility() {
        return hasUsedEnvironmentPreserveAbility;
    }

    public boolean getHasUsedEnvironmentPlunderAbility() {
        return hasUsedEnvironmentPlunderAbility;
    }

    public boolean getHasUsedPeopleConnectAbility() {
        return hasUsedPeopleConnectAbility;
    }

    public boolean hasPlanet(String planetID) {
        return planets.contains(planetID);
    }

    public boolean hasPlanetReady(String planetID) {
        return hasPlanet(planetID) && !exhaustedPlanets.contains(planetID);
    }

    public boolean hasCustodiaVigilia() {
        return planets.contains("custodiavigilia");
    }

    public boolean hasMechInSystem(Tile tile) {
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String colorID = Mapper.getColorID(getColor());
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty())
                continue;
            if (unitHolder.getUnitCount(UnitType.Mech, colorID) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasProductionUnitInSystem(Tile tile) {
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String colorID = Mapper.getColorID(getColor());
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty())
                continue;
            if (unitHolder.getUnitCount(UnitType.Spacedock, colorID) > 0) {
                return true;
            }
            if (unitHolder.getUnitCount(UnitType.CabalSpacedock, colorID) > 0) {
                return true;
            }
            if (hasUnit("arborec_mech")) {
                if (unitHolder.getUnitCount(UnitType.Mech, colorID) > 0) {
                    return true;
                }
            }
            if (hasUnit("arborec_infantry") || hasTech("lw2")) {
                if (unitHolder.getUnitCount(UnitType.Infantry, colorID) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public Set<Player> getNeighbouringPlayers() {
        Game game = getGame();
        Set<Player> adjacentPlayers = new HashSet<>();
        Set<Player> realPlayers = new HashSet<>(
            game.getPlayers().values().stream().filter(Player::isRealPlayer).toList());

        Set<Tile> playersTiles = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerIsInSystem(game, tile, this)) {
                playersTiles.add(tile);
            }
        }

        for (Tile tile : playersTiles) {
            adjacentPlayers.addAll(FoWHelper.getAdjacentPlayers(game, tile.getPosition(), false));
            if (realPlayers.size() == adjacentPlayers.size())
                break;
        }
        adjacentPlayers.remove(this);
        return adjacentPlayers;
    }

    @JsonIgnore
    public int getNeighbourCount() {
        return getNeighbouringPlayers().size();
    }

    @Deprecated
    public UnitModel getUnitFromImageName(String imageName) {
        String asyncID = StringUtils.substringBetween(imageName, "_", ".png");
        return getUnitFromAsyncID(asyncID);
    }

    public UnitModel getUnitFromUnitKey(UnitKey unit) {
        return getUnitFromAsyncID(unit.asyncID());
    }

    public UnitModel getUnitFromAsyncID(String asyncID) {
        // TODO: Maybe this sort can be better, idk
        return getUnitsByAsyncID(asyncID).stream().min(UnitModel::sortFactionUnitsFirst).orElse(null);
    }

    public boolean unitBelongsToPlayer(UnitKey unit) {
        return getColor().equals(AliasHandler.resolveColor(unit.getColorID()));
    }

    @Deprecated
    public boolean colorMatchesUnitImageName(String imageName) {
        return getColor().equals(AliasHandler.resolveColor(StringUtils.substringBefore(imageName, "_")));
    }

    public List<TemporaryCombatModifierModel> getNewTempCombatModifiers() {
        return newTempCombatModifiers;
    }

    public List<TemporaryCombatModifierModel> getTempCombatModifiers() {
        return tempCombatModifiers;
    }

    public boolean removeTempMod(TemporaryCombatModifierModel tempMod) {
        return tempCombatModifiers.remove(tempMod);
    }

    public void setTempCombatModifiers(List<TemporaryCombatModifierModel> tempMods) {
        tempCombatModifiers = new ArrayList<>(tempMods);
    }

    public void clearNewTempCombatModifiers() {
        newTempCombatModifiers.clear();
    }

    public void addNewTempCombatMod(TemporaryCombatModifierModel newTempMod) {
        newTempCombatModifiers.add(newTempMod);
    }

    public void addTempCombatMod(TemporaryCombatModifierModel mod) {
        tempCombatModifiers.add(mod);
    }

    @JsonIgnore
    public float getTotalResourceValueOfUnits() {
        float count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count = count + ButtonHelper.checkValuesOfUnits(this, getGame(), tile);
        }
        return count;
    }

    @JsonIgnore
    public int getTotalHPValueOfUnits() {
        int count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count = count + ButtonHelper.checkHPOfUnits(this, getGame(), tile);
        }
        return count;
    }

    @JsonIgnore
    public float getTotalCombatValueOfUnits() {
        float count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count = count + ButtonHelper.checkCombatValuesOfUnits(this, getGame(), tile);
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    @JsonIgnore
    public float getTotalUnitAbilityValueOfUnits() {
        float count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count = count + ButtonHelper.checkUnitAbilityValuesOfUnits(this, getGame(), tile);
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    /**
     * @return a list of colours the user would prefer to play as, in order of preference - the colours should all be "valid"- colourIDs
     */
    @JsonIgnore
    public List<String> getPreferredColours() {
        return UserSettingsManager.getInstance().getUserSettings(getUserID()).getPreferredColourList();
    }

    @JsonIgnore
    public String getNextAvailableColour() {
        if (getColor() != null && !getColor().equals("null")) {
            return getColor();
        }
        return getPreferredColours().stream()
            .filter(c -> getGame().getUnusedColours().contains(c))
            .findFirst()
            .orElse(getGame().getUnusedColours().stream().findFirst().orElse(null));
    }

    public Optional<String> getGlobalUserSetting(String setting) {
        return UserSettingsManager.getInstance().getUserSettings(getUserID()).getStoredValue(setting);
    }

    public void setGlobalUserSetting(String setting, String value) {
        UserSettings userSetting = UserSettingsManager.getInstance().getUserSettings(getUserID());
        userSetting.putStoredValue(setting, value);
        UserSettingsManager.getInstance().saveUserSetting(userSetting);
    }

    @JsonIgnore
    public boolean isSpeaker() {
        return getGame().getSpeaker().equals(getUserID());
    }

    /**
     * @return Player's private channel if Fog of War game, otherwise the main (action) game channel
     */
    @JsonIgnore
    public MessageChannel getCorrectChannel() {
        if (getGame().isFoWMode()) {
            return getPrivateChannel();
        } else {
            return getGame().getMainGameChannel();
        }
    }

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        FactionModel faction = getFactionModel();

        //TITLE
        StringBuilder title = new StringBuilder();
        title.append(getFactionEmoji()).append(" ");
        if (!"null".equals(getDisplayName())) title.append(getDisplayName()).append(" ");
        title.append(faction.getFactionNameWithSourceEmoji());
        eb.setTitle(title.toString());

        // // ICON
        // Emoji emoji = Emoji.fromFormatted(getFactionEmoji());
        // if (emoji instanceof CustomEmoji customEmoji) {
        //     eb.setThumbnail(customEmoji.getImageUrl());
        // }

        //DESCRIPTION
        StringBuilder desc = new StringBuilder();
        desc.append(Emojis.getColorEmojiWithName(getColor()));
        desc.append("\n").append(StringUtils.repeat(Emojis.comm, getCommoditiesTotal()));
        eb.setDescription(desc.toString());

        //FIELDS
        // Abilities
        StringBuilder sb = new StringBuilder();
        for (String id : getAbilities()) {
            AbilityModel model = Mapper.getAbility(id);
            sb.append(model.getNameRepresentation()).append("\n");
        }
        eb.addField("__Abilities__", sb.toString(), true);

        // Faction Tech
        sb = new StringBuilder();
        for (String id : getFactionTechs()) {
            TechnologyModel model = Mapper.getTech(id);
            sb.append(model.getNameRepresentation()).append("\n");
        }
        eb.addField("__Faction Technologies__", sb.toString(), true);

        // Techs
        sb = new StringBuilder();
        for (String id : getTechs()) {
            TechnologyModel model = Mapper.getTech(id);
            sb.append(model.getNameRepresentation()).append("\n");
        }
        eb.addField("__Technologies__", sb.toString(), true);

        // Special Units
        sb = new StringBuilder();
        for (String id : getSpecialUnitsOwned()) {
            UnitModel model = Mapper.getUnit(id);
            sb.append(model.getNameRepresentation()).append("\n");
        }
        eb.addField("__Units__", sb.toString(), true);

        // Promissory Notes
        sb = new StringBuilder();
        for (String id : getSpecialPromissoryNotesOwned()) {
            PromissoryNoteModel model = Mapper.getPromissoryNote(id);
            sb.append(model.getNameRepresentation()).append("\n");
        }
        eb.addField("__Promissory Notes__", sb.toString(), true);

        // Leaders
        sb = new StringBuilder();
        for (String id : getLeaderIDs()) {
            LeaderModel model = Mapper.getLeader(id);
            sb.append(model.getNameRepresentation()).append("\n");
        }
        eb.addField("__Leaders__", sb.toString(), false);

        // Author (Player Avatar)
        eb.setAuthor(getUserName(), null, getUser().getEffectiveAvatarUrl());

        //FOOTER
        StringBuilder foot = new StringBuilder();
        eb.setFooter(foot.toString());

        eb.setColor(ColorModel.primaryColor(color));
        return eb.build();
    }

    @JsonIgnore
    public Tile getHomeSystemTile() {
        Game game = getGame();
        if (hasAbility("mobile_command")) {
            if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, this, UnitType.Flagship).isEmpty()) {
                return null;
            }
            return ButtonHelper.getTilesOfPlayersSpecificUnits(game, this, UnitType.Flagship).get(0);
        }
        if (!faction.contains("franken") && game.getTile(AliasHandler.resolveTile(faction)) != null) {
            return game.getTile(AliasHandler.resolveTile(faction));
        }
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().equalsIgnoreCase(getPlayerStatsAnchorPosition()) && tile.isHomeSystem()) {
                return tile;
            }
            if (getPlanets().contains("creuss") && tile.getUnitHolders().get("creuss") != null) {
                return tile;
            }
        }
        return ButtonHelper.getTileOfPlanetWithNoTrait(this, game);
    }

    public List<Integer> getUnfollowedSCs() {
        List<Integer> unfollowedSCs = new ArrayList<>();
        for (int sc : getGame().getPlayedSCsInOrder(this)) {
            if (!hasFollowedSC(sc)) {
                unfollowedSCs.add(sc);
            }
        }
        return unfollowedSCs;
    }
}
