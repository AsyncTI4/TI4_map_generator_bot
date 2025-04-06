package ti4.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.DrawingUtil;
import ti4.image.Mapper;
import ti4.map.pojo.PlayerProperties;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.ColorModel;
import ti4.model.FactionModel;
import ti4.model.GenericCardModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.EndTurnService;
import ti4.service.turn.StartTurnService;
import ti4.service.user.AFKService;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

public class Player extends PlayerProperties {

    private final Game game;

    private DraftBag draftHand = new DraftBag();
    private DraftBag currentDraftBag = new DraftBag();
    private final DraftBag draftItemQueue = new DraftBag();
    private List<Leader> leaders = new ArrayList<>();
    private final List<TemporaryCombatModifierModel> newTempCombatModifiers = new ArrayList<>();
    private List<TemporaryCombatModifierModel> tempCombatModifiers = new ArrayList<>();

    // TIGL
    private @Getter @Setter TIGLRank playerTIGLRankAtGameStart;

    private final Tile nomboxTile = new Tile("nombox", "nombox");

    private final Map<String, Integer> actionCards = new LinkedHashMap<>();
    private final Map<String, Integer> events = new LinkedHashMap<>();
    private final Map<String, Integer> trapCards = new LinkedHashMap<>();
    private final Map<String, Integer> secrets = new LinkedHashMap<>();
    private final Map<String, Integer> secretsScored = new LinkedHashMap<>();
    private final Map<String, Integer> unitCaps = new HashMap<>();
    private final Map<String, String> trapCardsPlanets = new LinkedHashMap<>();
    private final Map<String, String> fow_seenTiles = new HashMap<>();
    private final Map<String, String> fow_customLabels = new HashMap<>();
    private Map<String, Integer> promissoryNotes = new LinkedHashMap<>();
    private @Getter Map<String, Integer> currentProducedUnits = new HashMap<>();
    private Map<String, Integer> debt_tokens = new LinkedHashMap<>(); // color, count

    public Player(String userID, String userName, Game game) {
        setUserID(userID);
        setUserName(userName);
        this.game = game;
    }

    @JsonIgnore
    public Game getGame() {
        return game;
    }

    @JsonIgnore
    public String getDecalName() {
        return Mapper.getDecalName(getDecalSet());
    }

    @JsonIgnore
    public String getDecalFile(String unitType) {
        if (getDecalSet() == null)
            return null;
        // TODO: Eventually remove if we stop setting values to string literal null, which is not good...
        if (getDecalSet().equals("null")) {
            setDecalSet(null);
            return null;
        }
        return String.format("%s_%s%s", getDecalSet(), unitType, DrawingUtil.getBlackWhiteFileSuffix(getColorID()));
    }

    public Tile getNomboxTile() {
        return nomboxTile;
    }

    public void resetProducedUnits() {
        currentProducedUnits = new HashMap<>();
    }

    public void resetSpentThings() {
        getSpentThingsThisWindow().clear();
    }

    public void addSpentThing(String thing) {
        getSpentThingsThisWindow().add(thing);
    }

    public void removeSpentThing(String thing) {
        getSpentThingsThisWindow().remove(thing);
    }

    public int getInitiative() {
        if (hasTheZeroToken()) return 0;
        return getLowestSC();
    }

    public static Comparator<Player> comparingInitiative() {
        return (p1, p2) -> Integer.compare(p1.getInitiative(), p2.getInitiative());
    }

    public int getSpentTgsThisWindow() {
        for (String thing : getSpentThingsThisWindow()) {
            if (thing.contains("tg_")) {
                return Integer.parseInt(thing.split("_")[1]);
            }
        }
        return 0;
    }

    public List<String> getTransactionItemsWithPlayer(Player player) {
        List<String> transactionItemsWithPlayer = new ArrayList<>();
        List<Player> players = new ArrayList<>();
        players.add(this);
        players.add(player);
        for (Player sender : players) {
            Player receiver = player;
            if (sender == player) {
                receiver = this;
            }
            for (String item : getTransactionItems()) {
                if (item.contains("sending" + sender.getFaction()) && item.contains("receiving" + receiver.getFaction())) {
                    transactionItemsWithPlayer.add(item);
                }
            }
        }
        return transactionItemsWithPlayer;
    }

    public void clearTransactionItemsWithPlayer(Player player) {
        List<String> newTransactionItems = new ArrayList<>();
        for (String item : getTransactionItems()) {
            if (!item.contains("ing" + player.getFaction())) {
                newTransactionItems.add(item);
            }
        }
        getGame().setStoredValue(player.getFaction() + "NothingMessage", "");
        getGame().setStoredValue(getFaction() + "NothingMessage", "");
        setTransactionItems(newTransactionItems);
    }

    public void addTransactionItem(String thing) {
        getTransactionItems().add(thing);
    }

    public void removeTransactionItem(String thing) {
        getTransactionItems().remove(thing);
    }

    @JsonIgnore
    public int getSpentInfantryThisWindow() {
        for (String thing : getSpentThingsThisWindow()) {
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
    public void increaseSarweenCount(int amount) {
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

    public void setProducedUnit(String unit, int count) {
        currentProducedUnits.put(unit, count);
    }

    public int getProducedUnit(String unit) {
        if (currentProducedUnits.get(unit) == null) {
            return 0;
        } else {
            return currentProducedUnits.get(unit);
        }
    }

    public void produceUnit(String unit) {
        int amount = getProducedUnit(unit) + 1;
        currentProducedUnits.put(unit, amount);
    }

    public void addMahactCC(String cc) {
        if (!getMahactCC().contains(cc)) {
            getMahactCC().add(cc);
        }
    }

    public void removeMahactCC(String cc) {
        getMahactCC().remove(cc);
    }

    @Nullable
    @JsonIgnore
    public Role getRoleForCommunity() {
        try {
            return AsyncTI4DiscordBot.jda.getRoleById(getRoleIDForCommunity());
        } catch (Exception e) {}
        return null;
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

    public String finChecker() {
        return getFinsFactionCheckerPrefix();
    }

    @JsonIgnore
    public String getFinsFactionCheckerPrefix() {
        return "FFCC_" + getFaction() + "_";
    }

    public String dummyPlayerSpoof() {
        return "dummyPlayerSpoof" + getFaction() + "_";
    }

    @JsonIgnore
    public boolean hasPDS2Tech() {
        return getTechs().contains("ht2") || getTechs().contains("pds2") || getTechs().contains("dsgledpds")
            || getTechs().contains("dsmirvpds");
    }

    @JsonIgnore
    public boolean hasInf2Tech() {// "dszeliinf"
        return getTechs().contains("cl2") || getTechs().contains("so2") || getTechs().contains("inf2")
            || getTechs().contains("lw2") || getTechs().contains("dscymiinf") || getTechs().contains("absol_inf2")
            || getTechs().contains("dszeliinf");
    }

    @JsonIgnore
    public boolean hasWarsunTech() {
        return getTechs().contains("pws2") || getTechs().contains("dsrohdws") || getTechs().contains("ws")
            || getTechs().contains("absol_ws") || getTechs().contains("absol_pws2")
            || hasUnit("muaat_warsun") || hasUnit("rohdhna_warsun");
    }

    @JsonIgnore
    public boolean hasFF2Tech() {
        return getTechs().contains("ff2") || getTechs().contains("hcf2") || getTechs().contains("dsflorff")
            || getTechs().contains("dslizhff") || getTechs().contains("absol_ff2") || getTechs().contains("absol_hcf2") || ownsUnit("florzen_fighter");
    }

    @JsonIgnore
    public boolean hasUpgradedUnit(String baseUpgradeID) {
        for (String tech : getTechs()) {
            TechnologyModel model = Mapper.getTech(tech);
            if (tech.equalsIgnoreCase(baseUpgradeID)
                || model.getBaseUpgrade().orElse("Bah").equalsIgnoreCase(baseUpgradeID)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @JsonIgnore
    public ThreadChannel getCardsInfoThread() {
        return getCardsInfoThread(true, false);
    }

    /**
     * Will create new Player Cards-Info threads (even if they exist but are archived) unless they exist and are open
     */
    public void createCardsInfoThreadChannelsIfRequired() {
        getCardsInfoThread(false, true);
    }

    /**
     * @param useComplete if false, will skip the RestAction.complete() steps, which may cause new Cards Info threads to be created despite
     * @param createWithQueue if true, will return null, and will create a new CardsInfo thread (if required) using a RestAction.queue() instead of a .complete()
     * @return
     */
    @JsonIgnore
    @Nullable
    private ThreadChannel getCardsInfoThread(boolean useComplete, boolean createWithQueue) {
        Game game = getGame();
        TextChannel actionsChannel = game.getMainGameChannel();
        if (game.isFowMode() || game.isCommunityMode()) {
            actionsChannel = (TextChannel) getPrivateChannel();

            if (!isRealPlayer() && isGM()) {
                List<TextChannel> channels = game.getGuild().getTextChannelsByName(game.getName() + "-gm-room", true);
                actionsChannel = channels.isEmpty() ? null : channels.getFirst();
            }
        }
        if (actionsChannel == null) {
            actionsChannel = game.getMainGameChannel();
        }
        if (actionsChannel == null) {
            BotLogger.log("`Helper.getPlayerCardsInfoThread`: actionsChannel is null for game, or community game private channel not set: " + game.getName());
            return null;
        }

        String threadName = Constants.CARDS_INFO_THREAD_PREFIX + game.getName() + "-" + getUserName().replace("/", "");
        if (game.isFowMode()) {
            threadName = game.getName() + "-" + "cards-info-" + getUserName().replace("/", "") + "-private";
        }

        List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
        List<ThreadChannel> hiddenThreadChannels = new ArrayList<>();

        // ATTEMPT TO FIND BY ID
        try {
            String cardsInfoThreadID = getCardsInfoThreadID();
            boolean hasCardsInfoThreadId = cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !"null".equals(cardsInfoThreadID);
            if (cardsInfoThreadID != null && hasCardsInfoThreadId) {
                ThreadChannel threadChannel = actionsChannel.getGuild().getThreadChannelById(cardsInfoThreadID);
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
                if (useComplete) {
                    hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                    for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                        if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                            setCardsInfoThreadID(threadChannel_.getId());
                            return threadChannel_;
                        }
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing #cards-info thread using ID: " + getCardsInfoThreadID() + " for potential thread name: " + threadName, e);
        }

        // ATTEMPT TO FIND BY NAME
        try {
            // SEARCH FOR EXISTING OPEN THREAD
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    setCardsInfoThreadID(threadChannel_.getId());
                    return threadChannel_;
                }
            }

            // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
            if (useComplete) {
                if (hiddenThreadChannels.isEmpty()) hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing #cards-info thread using name: " + threadName, e);
        }

        // CREATE NEW THREAD
        // Make card info thread a public thread in community mode
        boolean isPrivateChannel = !game.isFowMode();
        if (game.getName().contains("pbd100") || game.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = actionsChannel
            .createThreadChannel(threadName, isPrivateChannel)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK);
        if (isPrivateChannel) {
            threadAction = threadAction.setInvitable(false);
        }
        if (createWithQueue) {
            threadAction.queue(c -> {
                setCardsInfoThreadID(c.getId());
                String message = "Hello " + getPing() + "! This is your private channel.";
                MessageHelper.sendMessageToChannel(c, message);
            }, BotLogger::catchRestError);
            return null;
        }
        ThreadChannel threadChannel = null;
        if (useComplete) {
            threadChannel = threadAction.complete();
            setCardsInfoThreadID(threadChannel.getId());
        }
        return threadChannel;
    }

    /**
     * @param abilityID The ID of the ability - does not check if valid
     */
    public void addAbility(String abilityID) {
        getAbilities().add(abilityID);
    }

    public void removeAbility(String abilityID) {
        getAbilities().remove(abilityID);
    }

    public boolean hasAbility(String ability) {
        return getAbilities().contains(ability);
    }

    public boolean addExhaustedAbility(String ability) {
        return getExhaustedAbilities().add(ability);
    }

    public boolean removeExhaustedAbility(String ability) {
        return getExhaustedAbilities().remove(ability);
    }

    public void clearExhaustedAbilities() {
        getExhaustedAbilities().clear();
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

    public boolean hasTheZeroToken() {
        if (hasAbility("telepathic")) {
            for (Player p : getGame().getPlayers().values())
                if (p.getPromissoryNotesInPlayArea().contains(Constants.NAALU_PN))
                    return false;
            return true;
        } else if (getPromissoryNotesInPlayArea().contains(Constants.NAALU_PN)) {
            return true;
        } else
            return getGame().getStoredValue("naaluPNUser").equalsIgnoreCase(getFaction());
    }

    @JsonIgnore
    public Set<String> getSpecialUnitsOwned() {
        return new HashSet<>(getUnitsOwned().stream()
            .filter(u -> Mapper.getUnit(u).getFaction().isPresent())
            .collect(Collectors.toSet()));
    }

    public boolean hasUnit(String unitID) {
        return getUnitsOwned().contains(unitID);
    }

    public boolean ownsUnit(String unitID) {
        return getUnitsOwned().contains(unitID);
    }

    public boolean removeOwnedUnitByID(String unitID) {
        return getUnitsOwned().remove(unitID);
    }

    public boolean addOwnedUnitByID(String unitID) {
        return getUnitsOwned().add(unitID);
    }

    /**
     * Returns whether the player owns a unit containing the given substring.
     * 
     * @param unitIDSubstring The substring to
     * @return true if player owns a unit containing the given substring; false otherwise
     */
    public boolean ownsUnitSubstring(final String unitIDSubstring) {
        return getUnitModels().stream()
            .anyMatch(unit -> unit.getId().contains(unitIDSubstring));
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
            return allUnits.getFirst();
        }
        allUnits.sort((d1, d2) -> getUnitModelPriority(d2, unitHolder) - getUnitModelPriority(d1, unitHolder));

        return allUnits.getFirst();
    }

    private Integer getUnitModelPriority(UnitModel unit, UnitHolder unitHolder) {
        int score = 0;
        if (StringUtils.isNotBlank(unit.getFaction().orElse("")) && StringUtils.isNotBlank(unit.getUpgradesFromUnitId().orElse("")))
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
    private Map<String, Integer> getUnitsOwnedByBaseType() {
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

    public void setActionCard(String id, int oldID) {
        Collection<Integer> values = actionCards.values();
        int identifier = oldID;
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

    @JsonIgnore
    public Set<String> getSpecialPromissoryNotesOwned() {
        return getPromissoryNotesOwned().stream()
            .filter(pn -> Mapper.getPromissoryNotes().get(pn).isNotWellKnown())
            .collect(Collectors.toSet());
    }

    public boolean ownsPromissoryNote(String promissoryNoteID) {
        return getPromissoryNotesOwned().contains(promissoryNoteID);
    }

    public boolean removeOwnedPromissoryNoteByID(String promissoryNoteID) {
        return getPromissoryNotesOwned().remove(promissoryNoteID);
    }

    public boolean addOwnedPromissoryNoteByID(String promissoryNoteID) {
        return getPromissoryNotesOwned().add(promissoryNoteID);
    }

    public boolean hasPlayablePromissoryInHand(String pn) {
        return getPromissoryNotes().containsKey(pn) && !getPromissoryNotesOwned().contains(pn);
    }

    public void setPromissoryNote(String id) {
        Collection<Integer> values = promissoryNotes.values();
        int identifier = ThreadLocalRandom.current().nextInt(values.size() < 80 ? 100 : 1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(values.size() < 80 ? 100 : 1000);
        }
        promissoryNotes.put(id, identifier);
    }

    public Map<String, Integer> getPromissoryNotes() {
        return promissoryNotes;
    }

    public void setPromissoryNotes(Map<String, Integer> promissoryNotes) {
        this.promissoryNotes = promissoryNotes;
    }

    public void clearPromissoryNotes() {
        promissoryNotes.clear();
    }

    public void addPromissoryNoteToPlayArea(String id) {
        if (!getPromissoryNotesInPlayArea().contains(id)) {
            getPromissoryNotesInPlayArea().add(id);
        }
    }

    public void removePromissoryNoteFromPlayArea(String id) {
        getPromissoryNotesInPlayArea().remove(id);
    }

    public void setActionCard(String id, Integer identifier) {
        Collection<Integer> values = actionCards.values();
        int identifier2 = identifier;
        while (values.contains(identifier2)) {
            identifier2 = ThreadLocalRandom.current().nextInt(1000);
        }
        actionCards.put(id, identifier2);
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
        removePromissoryNoteFromPlayArea(id);
    }

    @JsonIgnore
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
        id = id.replace("extra1", "");
        id = id.replace("extra2", "");
        Collection<Integer> values = secrets.values();
        int identifier = ThreadLocalRandom.current().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = ThreadLocalRandom.current().nextInt(1000);
        }
        secrets.put(id, identifier);
    }

    public void setSecret(String id, Integer identifier) {
        id = id.replace("extra1", "");
        id = id.replace("extra2", "");
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
        idToRemove = idToRemove.replace("extra1", "");
        idToRemove = idToRemove.replace("extra2", "");
        return Mapper.getSecretObjective(idToRemove);
    }

    /**
     * @return Map of (SecretObjectiveModel ID, Random Number ID)
     */
    public Map<String, Integer> getSecretsScored() {
        return secretsScored;
    }

    @JsonIgnore
    public Map<String, Integer> getSecretsUnscored() {
        Map<String, Integer> secretsUnscored = new HashMap<>();
        for (Map.Entry<String, Integer> secret : secrets.entrySet()) {
            String id = secret.getKey();
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            if (!secretsScored.containsKey(id)) {
                secretsUnscored.put(id, secret.getValue());
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
        id = id.replace("extra1", "");
        id = id.replace("extra2", "");
        secretsScored.put(id, identifier);
    }

    public void setSecretScored(String id, Integer identifier) {
        id = id.replace("extra1", "");
        id = id.replace("extra2", "");
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

    @JsonIgnore
    public boolean enoughFragsForRelic() {
        updateFragments();
        int haz = getHrf();
        int ind = getIrf();
        int cult = getCrf();
        int frontier = getUrf();
        boolean blackMarket = hasAbility("fabrication") || getPromissoryNotes().containsKey("bmf");
        boolean exploration = getGame().isAgeOfExplorationMode();

        int targetToHit = 3;
        if (exploration || blackMarket) targetToHit = 2;

        int mostSingleType = frontier + Math.max(Math.max(haz, ind), cult);
        if (exploration && blackMarket) mostSingleType = haz + ind + cult + frontier;

        return mostSingleType >= targetToHit;
    }

    public int getNumberOfBluePrints() {
        int count = 0;
        if (isHasFoundCulFrag()) count++;
        if (isHasFoundHazFrag()) count++;
        if (isHasFoundIndFrag()) count++;
        if (isHasFoundUnkFrag()) count++;
        return count;
    }

    @Override
    public void setFragments(List<String> fragmentList) {
        super.setFragments(fragmentList);
        updateFragments();
    }

    public void addFragment(String fragmentID) {
        getFragments().add(fragmentID);
        updateFragments();
    }

    public void removeFragment(String fragmentID) {
        getFragments().remove(fragmentID);
        updateFragments();
    }

    private void updateFragments() {
        setCrf(0);
        setIrf(0);
        setHrf(0);
        setUrf(0);
        for (String cardID : getFragments()) {
            String color = Mapper.getExplore(cardID).getType().toLowerCase();
            int firstTime = 0;
            switch (color) {
                case Constants.CULTURAL -> {
                    setCrf(getCrf() + 1);
                    if (!isHasFoundCulFrag()) {
                        setHasFoundCulFrag(true);
                        firstTime++;
                    }
                }
                case Constants.INDUSTRIAL -> {
                    setIrf(getIrf() + 1);
                    if (!isHasFoundIndFrag()) {
                        setHasFoundIndFrag(true);
                        firstTime++;
                    }
                }
                case Constants.HAZARDOUS -> {
                    setHrf(getHrf() + 1);
                    if (!isHasFoundHazFrag()) {
                        setHasFoundHazFrag(true);
                        firstTime++;
                    }
                }
                case Constants.FRONTIER -> {
                    setUrf(getUrf() + 1);
                    if (!isHasFoundUnkFrag()) {
                        setHasFoundUnkFrag(true);
                        firstTime++;
                    }
                }
            }
            if (hasUnit("bentor_mech") && firstTime > 0) {
                int mechsRemain = 4 - ButtonHelper.getNumberOfUnitsOnTheBoard(getGame(), this, "mech", true);
                List<Button> buttons = new ArrayList<>(
                    Helper.getPlanetPlaceUnitButtons(this, getGame(), "mech", "placeOneNDone_skipbuild"));
                String message = getRepresentation()
                    + " due to your mech deploy ability, you may now place a mech on a planet you control.";
                for (int i = 0; i < firstTime && i < mechsRemain; i++) {
                    MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(), message, buttons);
                }
            }
        }
    }

    public void addRelic(String relicID) {
        if (!getRelics().contains(relicID) || Constants.ENIGMATIC_DEVICE.equals(relicID)) {
            if ("dynamiscore".equals(relicID) || "absol_dynamiscore".equals(relicID)) {
                setCommoditiesTotal(getCommoditiesTotal() + 2);
            }
            getRelics().add(relicID);
        }
    }

    public void removeRelic(String relicID) {
        if ("dynamiscore".equals(relicID) || "absol_dynamiscore".equals(relicID)) {
            setCommoditiesTotal(getCommoditiesTotal() - 2);
        }
        getRelics().remove(relicID);
    }

    public void addExhaustedRelic(String relicID) {
        getExhaustedRelics().add(relicID);
    }

    public void removeExhaustedRelic(String relicID) {
        getExhaustedRelics().remove(relicID);
    }

    @JsonIgnore
    public Member getMember() {
        Game game = getGame();
        if (game == null) return null;
        Guild guild = game.getGuild();
        if (guild == null) return null;
        return guild.getMemberById(getUserID());
    }

    @JsonIgnore
    public User getUser() {
        return AsyncTI4DiscordBot.jda.getUserById(getUserID());
    }

    @Override
    public String getUserName() {
        User userById = getUser();
        if (userById != null) {
            Member member = AsyncTI4DiscordBot.guildPrimary.getMemberById(getUserID());
            if (member != null) {
                setUserName(member.getEffectiveName());
            } else {
                setUserName(userById.getName());
            }
        }
        return super.getUserName();
    }

    @JsonIgnore
    public FactionModel getFactionModel() {
        return Mapper.getFaction(getFaction());
    }

    public void setFaction(Game game, String faction) {
        setFaction(faction);
        initLeaders();
        initAbilities(game);
    }

    /**
     * @return [FactionEmoji][PlayerPing][ColorEmoji][ColorName] or for Fog of War: [ColorEmoji][ColorName]
     */
    @JsonIgnore
    public String getRepresentation() {
        return getRepresentation(false, true);
    }

    /**
     * @return [FactionEmoji][PlayerPing][ColorEmoji][ColorName] even in Fog of War (will reveal faction/name)
     */
    @JsonIgnore
    public String getRepresentationUnfogged() {
        return getRepresentation(true, true);
    }

    /**
     * @return [FactionEmoji][PlayerName][ColorEmoji][ColorName] or for Fog of War: [ColorEmoji][ColorName] - won't ping player
     */
    @JsonIgnore
    public String getRepresentationNoPing() {
        return getRepresentation(false, false);
    }

    /**
     * @return [FactionEmoji][PlayerName][ColorEmoji][ColorName] even in Fog of War (will reveal faction/name) - won't ping player
     */
    @JsonIgnore
    public String getRepresentationUnfoggedNoPing() {
        return getRepresentation(true, false);
    }

    @JsonIgnore
    public String getRepresentation(boolean overrideFow, boolean ping) {
        return getRepresentation(overrideFow, ping, false);
    }

    @JsonIgnore
    public String getRepresentation(boolean overrideFow, boolean ping, boolean noColor) {
        return getRepresentation(overrideFow, ping, noColor, false);
    }

    @JsonIgnore
    public String getRepresentation(boolean overrideFow, boolean ping, boolean noColor, boolean noFactionIcon) {
        Game game = getGame();
        boolean privateGame = FoWHelper.isPrivateGame(game);
        if (privateGame && !overrideFow) {
            return ColorEmojis.getColorEmojiWithName(getColor());
        }

        if (game != null && game.isCommunityMode()) {
            Role roleForCommunity = getRoleForCommunity();
            if (roleForCommunity == null && !getTeamMateIDs().isEmpty()) {
                StringBuilder sb = new StringBuilder((noFactionIcon ? "" : getFactionEmoji()));
                for (String userID : getTeamMateIDs()) {
                    User userById = AsyncTI4DiscordBot.jda.getUserById(userID);
                    if (userById == null) {
                        continue;
                    }
                    if (ping) {
                        sb.append(" ").append(userById.getAsMention());
                    }
                }
                if (getColor() != null && !"null".equals(getColor()) && !noColor) {
                    sb.append(" ").append(ColorEmojis.getColorEmojiWithName(getColor()));
                }
                return sb.toString();
            } else if (roleForCommunity != null) {
                if (ping) {
                    return (noFactionIcon ? "" : getFactionEmoji() + " ") + roleForCommunity.getAsMention() + " "
                        + ColorEmojis.getColorEmojiWithName(getColor());
                } else {
                    return (noFactionIcon ? "" : getFactionEmoji() + " ") + roleForCommunity.getName() + " "
                        + ColorEmojis.getColorEmojiWithName(getColor());
                }
            } else {
                return (noFactionIcon ? "" : getFactionEmoji() + " ") + ColorEmojis.getColorEmojiWithName(getColor());
            }
        }

        // DEFAULT REPRESENTATION
        StringBuilder sb = new StringBuilder(getFactionEmoji());
        if (noFactionIcon) {
            sb = new StringBuilder();
        }
        if (ping) {
            sb.append(getPing());
        } else {
            sb.append(getUserName());
        }
        if (getColor() != null && !"null".equals(getColor()) && !noColor) {
            sb.append(" ").append(ColorEmojis.getColorEmojiWithName(getColor()));
        }
        return sb.toString();
    }

    @JsonIgnore
    public String getPing() {
        User userById = getUser();
        if (userById == null)
            return "";

        StringBuilder sb = new StringBuilder(userById.getAsMention());
        switch (getUserID()) {
            case Constants.bortId -> sb.append(MiscEmojis.BortWindow); // mysonisalsonamedbort
            case Constants.tspId -> sb.append(MiscEmojis.SpoonAbides); // tispoon
            case Constants.jazzId -> sb.append(MiscEmojis.Scout); // Jazzx
        }
        return sb.toString();
    }

    @NotNull
    public String getFactionEmoji() {
        String emoji = null;
        if (StringUtils.isNotBlank(super.getFactionEmoji()) && !"null".equals(super.getFactionEmoji())) {
            emoji = super.getFactionEmoji();
        }
        if (emoji == null && getFactionModel() != null) {
            emoji = getFactionModel().getFactionEmoji();
        }
        return emoji != null ? emoji : FactionEmojis.getFactionIcon(getFaction()).toString();
    }

    @JsonIgnore
    public String fogSafeEmoji() {
        if (getGame() != null && getGame().isFowMode())
            return ColorEmojis.getColorEmoji(getColor()).toString();
        return getFactionEmoji();
    }

    @JsonIgnore
    public String getFactionEmojiOrColor() {
        if (getGame().isFowMode() || FoWHelper.isPrivateGame(getGame())) {
            return ColorEmojis.getColorEmojiWithName(getColor());
        }
        return getFactionEmoji();
    }

    public String getFactionEmojiRaw() {
        return getFactionEmoji();
    }

    public boolean hasCustomFactionEmoji() {
        return StringUtils.isNotBlank(getFactionEmoji())
            && !"null".equals(getFactionEmoji())
            && getFactionModel() != null
            && !getFactionEmoji().equalsIgnoreCase(getFactionModel().getFactionEmoji());
    }

    private void initAbilities(Game game) {
        Set<String> abilities = new HashSet<>();
        for (String ability : getFactionStartingAbilities()) {
            AbilityModel model = Mapper.getAbilityOrReplacement(ability, game);
            if (model != null) {
                abilities.add(model.getAlias());
            }
        }
        setAbilities(abilities);

        if (hasAbility("cunning")) {
            List<GenericCardModel> allTraps = new ArrayList<>(Mapper.getTraps().values());
            allTraps.stream().forEach(trap -> setTrapCard(trap.getAlias()));
        }
    }

    @JsonIgnore
    public FactionModel getFactionSetupInfo() {
        if (getFaction() == null || "null".equals(getFaction()) || "keleres".equals(getFaction()))
            return null;
        FactionModel factionSetupInfo = Mapper.getFaction(getFaction());
        if (factionSetupInfo == null) {
            BotLogger.log("Could not get faction setup info for: " + getFaction());
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
        if (game != null && game.isBaseGameMode()) return;
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
        return AFKService.userIsAFK(getUserID());
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
            if (leader.getId().equalsIgnoreCase(leaderID)) {
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

    @Override
    public String getColor() {
        String color = super.getColor();
        return (color != null && !color.equals("null")) ? Mapper.getColorName(color) : "null";
    }

    @Override
    public void setColor(String color) {
        super.setColor(Mapper.getColorName(color));
    }

    @JsonIgnore
    public String getColorID() {
        return (getColor() != null && !getColor().equals("null")) ? Mapper.getColorID(getColor()) : "null";
    }

    public void addAllianceMember(String color) {
        if (!"null".equals(color)) {
            setAllianceMembers(getAllianceMembers() + color);
        }
    }

    public void removeAllianceMember(String color) {
        if (!"null".equals(color)) {
            setAllianceMembers(getAllianceMembers().replace(color, ""));
        }
    }

    public void initPNs() {
        if (getGame() != null && getColor() != null && getFaction() != null && Mapper.isValidColor(getColor())
            && Mapper.isValidFaction(getFaction())) {
            promissoryNotes.clear();
            List<String> promissoryNotes = Mapper.getColorPromissoryNoteIDs(getGame(), getColor()); //TODO: switch this to an explicit game.getPNSet() DeckModel
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

    @JsonIgnore
    public String getCCRepresentation() {
        return getTacticalCC() + "/" + getFleetCC() + "/" + getStrategicCC();
    }

    @Override
    public void setTacticalCC(int tacticalCC) {
        if (tacticalCC >= 0)
            super.setTacticalCC(tacticalCC);
    }

    @Override
    public void setFleetCC(int fleetCC) {
        if (fleetCC >= 0)
            super.setFleetCC(fleetCC);
    }

    @Override
    public void setStrategicCC(int strategicCC) {
        if (strategicCC >= 0)
            super.setStrategicCC(strategicCC);
    }

    @JsonIgnore
    public double getExpectedHits() {
        return getExpectedHitsTimes10() / 10.0;
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
                            int frequency = Collections.frequency(scoredPOEntry.getValue(), getUserID());
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

    @Override
    public void setTg(int tg) {
        super.setTg(Math.max(0, tg));
    }

    @JsonIgnore
    public String gainTG(int count) {
        String message = "(" + getTg() + " -> " + (getTg() + count) + ")";
        setTg(getTg() + count);
        return message;
    }

    @JsonIgnore
    public String gainTG(int count, boolean checkForPillage) {
        String message = gainTG(count);
        if (checkForPillage) {
            ButtonHelperAbilities.pillageCheck(this, getGame());
        }
        return message;
    }

    public void addFollowedSC(Integer sc) {
        getFollowedSCs().add(sc);
    }

    public void addFollowedSC(Integer sc, GenericInteractionCreateEvent event) {
        Game game = getGame();

        getFollowedSCs().add(sc);
        if (game != null && game.getActivePlayer() != null) {
            if (game.getStoredValue("endTurnWhenSCFinished").equalsIgnoreCase(sc + game.getActivePlayer().getFaction())) {
                for (Player p2 : game.getRealPlayers()) {
                    if (!p2.hasFollowedSC(sc)) {
                        return;
                    }
                }
                game.setStoredValue("endTurnWhenSCFinished", "");
                Player p2 = game.getActivePlayer();
                EndTurnService.endTurnAndUpdateMap(event, game, p2);
            }
            if (game.getStoredValue("fleetLogWhenSCFinished").equalsIgnoreCase(sc + game.getActivePlayer().getFaction())) {
                for (Player p2 : game.getRealPlayers()) {
                    if (!p2.hasFollowedSC(sc)) {
                        return;
                    }
                }
                game.setStoredValue("fleetLogWhenSCFinished", "");
                Player p2 = game.getActivePlayer();
                String message = p2.getRepresentation() + " Use buttons to end turn or do another action.";
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(p2, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, systemButtons);
            }
        }
    }

    public void removeFollowedSC(Integer sc) {
        getFollowedSCs().remove(sc);
    }

    public boolean hasFollowedSC(int sc) {
        return getFollowedSCs().contains(sc);
    }

    public void clearFollowedSCs() {
        getFollowedSCs().clear();
    }

    @JsonIgnore
    public int getAc() {
        return actionCards.size();
    }

    @JsonIgnore
    public int getPnCount() {
        return (getPromissoryNotes().size() - getPromissoryNotesInPlayArea().size());
    }

    @JsonIgnore
    public int getSo() {
        return secrets.size();
    }

    @JsonIgnore
    public int getSoScored() {
        return secretsScored.size();
    }

    public void addSC(int sc) {
        getSCs().add(sc);
    }

    public void removeSC(int sc) {
        getSCs().remove(sc);
    }

    public void clearSCs() {
        getSCs().clear();
    }

    @JsonIgnore
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

    @Override
    public void setCommodities(int comms) {
        super.setCommodities(Math.clamp(comms, 0, getCommoditiesTotal()));
        if (getCommoditiesTotal() == 0) super.setCommodities(comms);
    }

    @JsonIgnore
    public String getCommoditiesRepresentation() {
        return getCommodities() + "/" + getCommoditiesTotal();
    }

    @Override
    public List<String> getTeamMateIDs() {
        if (!super.getTeamMateIDs().contains(getUserID())) {
            super.getTeamMateIDs().addFirst(getUserID()); // a few things depend on the "primary" user being here
        }
        return super.getTeamMateIDs();
    }

    @Override
    public void setTeamMateIDs(List<String> teammateIDs) {
        Set<String> nonDuplicates = new HashSet<>(teammateIDs);
        super.getTeamMateIDs().clear();
        super.getTeamMateIDs().addAll(nonDuplicates);
    }

    public void addTeamMateID(String userID) {
        getTeamMateIDs().add(userID);
    }

    public void removeTeamMateID(String userID) {
        getTeamMateIDs().remove(userID);
    }

    public List<String> getNotResearchedFactionTechs() {
        return getFactionTechs().stream()
            .filter(tech -> !hasTech(tech))
            .filter(tech -> !getPurgedTechs().contains(tech))
            .toList();
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

    @JsonIgnore
    public boolean hasIIHQ() {
        return hasTech("iihq");
    }

    public boolean hasTech(String techID) {
        if ("det".equals(techID) || "amd".equals(techID)) {
            if (getTechs().contains("absol_" + techID)) {
                return true;
            }
        }
        return getTechs().contains(techID);
    }

    public boolean hasTechReady(String techID) {
        return hasTech(techID) && !getExhaustedTechs().contains(techID);
    }

    public boolean controlsMecatol(boolean includeAlliance) {
        if (includeAlliance)
            return CollectionUtils.containsAny(getPlanetsAllianceMode(), Constants.MECATOLS);
        return CollectionUtils.containsAny(getPlanets(), Constants.MECATOLS);
    }

    public boolean isPlayerMemberOfAlliance(Player player2) {
        return getAllianceMembers().contains(player2.getFaction());
    }

    @JsonIgnore
    public List<String> getPlanetsAllianceMode() {
        List<String> newPlanets = new ArrayList<>(getPlanets());
        if (!"".equalsIgnoreCase(getAllianceMembers())) {
            for (Player player2 : getGame().getRealPlayers()) {
                if (getAllianceMembers().contains(player2.getFaction())) {
                    newPlanets.addAll(player2.getPlanets());
                }
            }
        }
        return newPlanets;
    }

    public void loadDraftHand(List<String> saveString) {
        DraftBag newBag = new DraftBag();
        for (String item : saveString) {
            newBag.Contents.add(DraftItem.generateFromAlias(item));
        }
        draftHand = newBag;
    }

    public void loadCurrentDraftBag(List<String> saveString) {
        DraftBag newBag = new DraftBag();
        for (String item : saveString) {
            newBag.Contents.add(DraftItem.generateFromAlias(item));
        }
        currentDraftBag = newBag;
    }

    public void loadItemsToDraft(List<String> saveString) {
        List<DraftItem> items = new ArrayList<>();
        for (String item : saveString) {
            items.add(DraftItem.generateFromAlias(item));
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

    public boolean hasRelic(String relicID) {
        return getRelics().contains(relicID);
    }

    public boolean hasRelicReady(String relicID) {
        return hasRelic(relicID) && !getExhaustedRelics().contains(relicID);
    }

    public void clearExhaustedTechs() {
        getExhaustedTechs().clear();
    }

    public void clearExhaustedPlanets(boolean cleanAbilities) {
        getExhaustedPlanets().clear();
        if (cleanAbilities) {
            getExhaustedPlanetsAbilities().clear();
        }
    }

    public void clearExhaustedRelics() {
        getExhaustedRelics().clear();
    }

    public void addFactionTech(String techID) {
        if (getFactionTechs().contains(techID))
            return;
        getFactionTechs().add(techID);
    }

    public boolean removeFactionTech(String techID) {
        return getFactionTechs().remove(techID);
    }

    public void addTech(String techID) {
        if (getTechs().contains(techID)) {
            return;
        }
        getTechs().add(techID);
        doAdditionalThingsWhenAddingTech(techID);
    }

    public void gainCustodiaVigilia() {
        addPlanet("custodiavigilia");
        exhaustPlanet("custodiavigilia");

        if (getPlanets().contains(Constants.MR)) {
            Planet mecatolRex = getGame().getPlanetsInfo().get(Constants.MR);
            if (mecatolRex != null) {
                PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                mecatolRex.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                mecatolRex.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
            }
        }
    }

    public void removeCustodiaVigilia() {
        removePlanet("custodiavigilia");
        if (getPlanets().contains(Constants.MR)) {
            Planet mecatolRex = getGame().getPlanetsInfo().get(Constants.MR);
            if (mecatolRex != null) {
                mecatolRex.setSpaceCannonDieCount(0);
                mecatolRex.setSpaceCannonHitsOn(0);
            }
        }
    }

    private void doAdditionalThingsWhenAddingTech(String techID) {
        // Set ATS Armaments to 0 when adding tech (if it was removed we reset it)
        if ("dslaner".equalsIgnoreCase(techID)) {
            setAtsCount(0);
        }

        // Add Custodia Vigilia when researching IIHQ
        if ("iihq".equalsIgnoreCase(techID)) {
            gainCustodiaVigilia();
        }

        // Update Owned Units when Researching a Unit Upgrade
        TechnologyModel techModel = Mapper.getTech(techID);
        if (techID == null)
            return;

        if (techModel.isUnitUpgrade()) {
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

    // Provided because people make mistakes, also nekro exists, also weird homebrew exists
    private void doAdditionalThingsWhenRemovingTech(String techID) {
        // Remove Custodia Vigilia when un-researching IIHQ
        if (techID != null && techID.toLowerCase().contains("iihq")) {
            removeCustodiaVigilia();
        }

        // Update Owned Units when Researching a Unit Upgrade
        TechnologyModel techModel = Mapper.getTech(techID);
        if (techID == null || techModel == null)
            return;

        if (techModel.isUnitUpgrade()) {
            UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
            List<TechnologyModel> relevantTechs = getTechs().stream().map(Mapper::getTech)
                .filter(tech -> tech.getBaseUpgrade().orElse("").equals(unitModel.getBaseType())).toList();

            removeOwnedUnitByID(unitModel.getId());

            // Find another unit model to replace this lost model
            String replacementUnit = unitModel.getBaseType(); // default
            if (relevantTechs.isEmpty() && unitModel.getBaseType() != null) {
                // No other relevant unit upgrades
                FactionModel factionSetup = getFactionSetupInfo();
                replacementUnit = factionSetup.getUnits().stream().map(Mapper::getUnit)
                    .map(UnitModel::getId)
                    .filter(id -> id.contains(unitModel.getBaseType())).findFirst()
                    .orElse(replacementUnit);
            } else if (!relevantTechs.isEmpty()) {
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
        if (getTechs().contains(tech) && !getExhaustedTechs().contains(tech)) {
            getExhaustedTechs().add(tech);
        }
    }

    public void refreshTech(String tech) {
        getExhaustedTechs().removeAll(Collections.singleton(tech));
    }

    public void removeTech(String tech) {
        getExhaustedTechs().remove(tech);
        if (getTechs().remove(tech)) {
            doAdditionalThingsWhenRemovingTech(tech);
        }
    }

    public void purgeTech(String tech) {
        if (getTechs().contains(tech)) {
            removeTech(tech);
        }
        getPurgedTechs().add(tech);
    }

    public void addPlanet(String planet) {
        if (!getPlanets().contains(planet)) {
            getPlanets().add(planet);
        }
    }

    public void exhaustPlanet(String planet) {
        if (getPlanets().contains(planet) && !getExhaustedPlanets().contains(planet)) {
            getExhaustedPlanets().add(planet);
        }
        Game game = getGame();
        if (ButtonHelper.getUnitHolderFromPlanetName(planet, game) != null && game.isAbsolMode()
            && ButtonHelper.getUnitHolderFromPlanetName(planet, game).getTokenList()
                .contains("attachment_nanoforge.png")
            && !getExhaustedPlanetsAbilities().contains(planet)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("planetAbilityExhaust_" + planet, "Use Nano-Forge Ability"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(getCorrectChannel(),
                getRepresentation() + ", you may choose to exhaust the _Nano-Forge_ legendary ability to ready the planet it's attached to.", buttons);
        }
    }

    public void exhaustPlanetAbility(String planet) {
        if (getPlanets().contains(planet) && !getExhaustedPlanetsAbilities().contains(planet)) {
            getExhaustedPlanetsAbilities().add(planet);
        }
    }

    public void refreshPlanet(String planet) {
        boolean isRemoved = getExhaustedPlanets().remove(planet);
        if (isRemoved)
            refreshPlanet(planet);
    }

    public void refreshPlanetAbility(String planet) {
        boolean isRemoved = getExhaustedPlanetsAbilities().remove(planet);
        if (isRemoved)
            refreshPlanetAbility(planet);
    }

    public void removePlanet(String planet) {
        getPlanets().remove(planet);
        refreshPlanet(planet);
        refreshPlanetAbility(planet);
    }

    public void flipSearchWarrant() {
        setSearchWarrant(!isSearchWarrant());
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
        if (label != null && !".".equals(label) && !label.isEmpty()) {
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

    @JsonIgnore
    public boolean isRealPlayer() {
        return !(isDummy() || getFaction() == null || getColor() == null || "null".equals(getColor()));
    }

    @Override
    public String getFogFilter() {
        return super.getFogFilter() == null ? "default" : super.getFogFilter();
    }

    public void updateTurnStats(long turnTime) {
        setNumberOfTurns(getNumberOfTurns() + 1);
        setTotalTurnTime(getTotalTurnTime() + turnTime);
    }

    public void updateTurnStatsWithAverage(long turnTime) {
        long averageTime = Math.min(turnTime, getTotalTurnTime() / (getNumberOfTurns() + 1));
        setNumberOfTurns(getNumberOfTurns() + 1);
        setTotalTurnTime(getTotalTurnTime() + averageTime);
    }

    @Override
    public String getAutoCompleteRepresentation() {
        return getAutoCompleteRepresentation(false);
    }

    @JsonIgnore
    public String getAutoCompleteRepresentation(boolean reset) {
        if (reset || super.getAutoCompleteRepresentation() == null) {
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
            if (userName == null || userName.isBlank()) {
                userName = "No User";
            }

            String representation = color + " / " + faction + " / " + userName;
            setAutoCompleteRepresentation(representation);
            return super.getAutoCompleteRepresentation();
        }
        return super.getAutoCompleteRepresentation();
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

    public boolean hasPlanet(String planetID) {
        return getPlanets().contains(planetID);
    }

    public boolean hasPlanetReady(String planetID) {
        return hasPlanet(planetID) && !getExhaustedPlanets().contains(planetID);
    }

    public boolean hasCustodiaVigilia() {
        return getPlanets().contains("custodiavigilia");
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

    @JsonIgnore
    public Set<Player> getNeighbouringPlayers(boolean checkEquiv) {
        Game game = getGame();
        Set<Player> adjacentPlayers = new HashSet<>();
        Set<Player> realPlayers = new HashSet<>(
            game.getPlayers().values().stream().filter(Player::isRealPlayer).toList());

        Set<Tile> playersTiles = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerIsInSystem(game, tile, this, true)) {
                playersTiles.add(tile);
            }
        }

        for (Tile tile : playersTiles) {
            adjacentPlayers.addAll(FoWHelper.getAdjacentPlayers(game, tile.getPosition(), false));
            if (realPlayers.size() == adjacentPlayers.size())
                break;
        }
        adjacentPlayers.remove(this);
        if (checkEquiv && realPlayers.size() < 30) {
            for (Player p2 : realPlayers) {
                if (!adjacentPlayers.contains(p2) && p2.getNeighbouringPlayers(false).contains(this)) {
                    adjacentPlayers.add(p2);
                }
            }
        }
        return adjacentPlayers;
    }

    public boolean isNeighboursWith(Player player) {
        return getNeighbouringPlayers(true).contains(player);
    }

    @JsonIgnore
    public int getNeighbourCount() {
        return getNeighbouringPlayers(true).size();
    }

    public UnitModel getUnitFromUnitKey(UnitKey unit) {
        return getUnitFromAsyncID(unit.asyncID());
    }

    public UnitModel getUnitFromAsyncID(String asyncID) {
        // TODO: Maybe this sort can be better, idk
        return getUnitsByAsyncID(asyncID).stream().min(UnitModel::sortFactionUnitsFirst).orElse(null);
    }

    public boolean unitBelongsToPlayer(UnitKey unit) {
        if (unit == null) {
            return false;
        }
        return getColor().equals(AliasHandler.resolveColor(unit.getColorID()));
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
    public float getTotalResourceValueOfUnits(String type) {
        float count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count += ButtonHelper.checkValuesOfUnits(this, tile, type);
        }
        return count;
    }

    @JsonIgnore
    public int getTotalHPValueOfUnits(String type) {
        int count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count += ButtonHelper.checkHPOfUnits(this, tile, type);
        }
        return count;
    }

    @JsonIgnore
    public float getTotalCombatValueOfUnits(String type) {
        float count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count += ButtonHelper.checkCombatValuesOfUnits(this, tile, type);
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    @JsonIgnore
    public float getTotalUnitAbilityValueOfUnits() {
        float count = 0;
        for (Tile tile : getGame().getTileMap().values()) {
            count += ButtonHelper.checkUnitAbilityValuesOfUnits(this, getGame(), tile);
        }
        return Math.round(count * 10) / (float) 10.0;
    }

    @JsonIgnore
    public UserSettings getUserSettings() {
        return UserSettingsManager.get(getUserID());
    }

    @JsonIgnore
    public String getNextAvailableColour() {
        if (getColor() != null && !getColor().equals("null")) {
            return getColor();
        }
        return getNextAvailableColorIgnoreCurrent();
    }

    @JsonIgnore
    public String getNextAvailableColorIgnoreCurrent() {
        Predicate<ColorModel> nonExclusive = cm -> !ColorChangeHelper.colorIsExclusive(cm.getAlias(), this);
        String color = getUserSettings().getPreferredColors().stream()
            .filter(c -> !ColorChangeHelper.colorIsExclusive(c, this))
            .findFirst()
            .orElse(getGame().getUnusedColorsPreferringBase().stream().filter(nonExclusive).findFirst().map(ColorModel::getName).orElse(null));
        return Mapper.getColorName(color);
    }

    @JsonIgnore
    public boolean isSpeaker() {
        return getGame().getSpeakerUserID().equals(getUserID());
    }

    /**
     * @return Player's private channel if Fog of War game, otherwise the main
     *         (action) game channel
     */
    @JsonIgnore
    public MessageChannel getCorrectChannel() {
        if (getGame().isFowMode()) {
            return getPrivateChannel();
        }
        return getGame().getMainGameChannel();
    }

    public String bannerName() {
        String name = Mapper.getFaction(getFaction()).getFactionName().toUpperCase();
        if (name.contains("KELERES")) {
            return "THE COUNCIL KELERES";
        }
        if (name.contains("FRANKEN") && getDisplayName() != null && !getDisplayName().isEmpty() && !getDisplayName().equalsIgnoreCase("null")) {
            return getDisplayName().toUpperCase();
        }
        return name;
    }

    public String getFlexibleDisplayName() {
        String name = getFaction();
        if (getDisplayName() != null && !getDisplayName().isEmpty() && !getDisplayName().equals("null")) {
            name = getDisplayName();
        }
        return StringUtils.capitalize(name);
    }

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        FactionModel faction = getFactionModel();

        // TITLE
        StringBuilder title = new StringBuilder();
        title.append(getFactionEmoji()).append(" ");
        if (!"null".equals(getDisplayName()))
            title.append(getDisplayName()).append(" ");
        title.append(faction.getFactionNameWithSourceEmoji());
        eb.setTitle(title.toString());

        // // ICON
        // Emoji emoji = Emoji.fromFormatted(getFactionEmoji());
        // if (emoji instanceof CustomEmoji customEmoji) {
        // eb.setThumbnail(customEmoji.getImageUrl());
        // }

        // DESCRIPTION
        String desc = ColorEmojis.getColorEmojiWithName(getColor()) +
            "\n" + MiscEmojis.comm(getCommoditiesTotal());
        eb.setDescription(desc);

        // FIELDS
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

        // FOOTER
        eb.setFooter("");

        eb.setColor(Mapper.getColor(getColor()).primaryColor());
        return eb.build();
    }

    @JsonIgnore
    public Tile getHomeSystemTile() {
        Game game = getGame();
        if (getFaction() == null) {
            return null;
        }
        if (getHomeSystemPosition() != null) {
            Tile frankenHs = game.getTileByPosition(getHomeSystemPosition());
            if (frankenHs != null) {
                return frankenHs;
            }
        }

        if (hasAbility("mobile_command")) {
            if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, this, UnitType.Flagship).isEmpty()) {
                return null;
            }
            return ButtonHelper.getTilesOfPlayersSpecificUnits(game, this, UnitType.Flagship).getFirst();
        }
        if (!getFaction().contains("franken") && game.getTile(AliasHandler.resolveTile(getFaction())) != null) {
            return game.getTile(AliasHandler.resolveTile(getFaction()));
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

    @JsonIgnore
    public List<Integer> getUnfollowedSCs() {
        List<Integer> unfollowedSCs = new ArrayList<>();
        for (int sc : getGame().getPlayedSCsInOrder(this)) {
            if (!hasFollowedSC(sc)) {
                unfollowedSCs.add(sc);
            }
        }
        return unfollowedSCs;
    }

    public void checkCommanderUnlock(String factionToCheck) {
        CommanderUnlockCheckService.checkPlayer(this, factionToCheck);
    }

    @JsonIgnore
    public List<Player> getOtherRealPlayers() {
        return getGame().getRealPlayers().stream()
            .filter(p -> !p.equals(this))
            .toList();
    }

    @JsonIgnore
    public boolean isActivePlayer() {
        return this.equals(getGame().getActivePlayer());
    }

    public void clearDebt(Player player, int count) {
        String clearedPlayerColor = player.getColor();
        removeDebtTokens(clearedPlayerColor, count);
    }

    @JsonIgnore
    public boolean isGM() {
        return getGame().getPlayersWithGMRole().contains(this);
    }

    @JsonIgnore
    public boolean hasPriorityPosition() {
        return this.getPriorityPosition() != -1 && this.getPriorityPosition() != 0;
    }
}
