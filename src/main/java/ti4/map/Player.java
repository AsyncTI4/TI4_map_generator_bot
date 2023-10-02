package ti4.map;

import java.util.concurrent.ThreadLocalRandom;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import ti4.AsyncTI4DiscordBot;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.BotLogger;
import ti4.model.FactionModel;
import ti4.model.Franken.FrankenBag;
import ti4.model.Franken.FrankenItem;
import ti4.model.PlanetModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.TechnologyModel.TechnologyType;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import lombok.Getter;
import lombok.Setter;

import java.util.Map.Entry;
import java.util.*;

public class Player {

    private String userID;
    private String userName;

    private String  gameID;

    private boolean passed;
    private boolean readyToPassBag;
    private boolean searchWarrant;
    private boolean isDummy;

    private String faction;
    private String factionEmoji = null;

    @Setter
    private String playerStatsAnchorPosition;
    private String allianceMembers = "";
    private String color;
    private String autoCompleteRepresentation;

    private int tacticalCC = 3;
    private int fleetCC = 3;
    private int strategicCC = 2;

    private int tg;
    private int commodities;
    private int commoditiesTotal;
    private int stasisInfantry;

    private Set<Integer> followedSCs = new HashSet<>();

    private final LinkedHashMap<String, Integer> actionCards = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> trapCards = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> trapCardsPlanets = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> secrets = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> promissoryNotes = new LinkedHashMap<>();
    private HashSet<String> abilities = new HashSet<>();
    private HashSet<String> exhaustedAbilities = new HashSet<>();
    private HashSet<String> promissoryNotesOwned = new HashSet<>();
    private HashSet<String> unitsOwned = new HashSet<>();
    private List<String> promissoryNotesInPlayArea = new ArrayList<>();
    private List<String> techs = new ArrayList<>();
    private FrankenBag frankenHand = new FrankenBag();
    private FrankenBag currentFrankenBag = new FrankenBag();
    private FrankenBag frankenItemsToDraft = new FrankenBag();
    private List<String> exhaustedTechs = new ArrayList<>();
    private List<String> planets = new ArrayList<>();
    private List<String> exhaustedPlanets = new ArrayList<>();
    private List<String> exhaustedPlanetsAbilities = new ArrayList<>();
    private List<String> mahactCC = new ArrayList<>();

    @JsonProperty("leaders")
    private List<Leader> leaders = new ArrayList<>();

    private Map<String, Integer> debt_tokens = new LinkedHashMap<>(); //colour, count
    private final HashMap<String, String> fow_seenTiles = new HashMap<>();
    private final HashMap<String, Integer> unitCaps = new HashMap<>();
    private final HashMap<String, String> fow_customLabels = new HashMap<>();
    private String fowFogFilter;
    private boolean fogInitialized;

    @Nullable
    private String roleIDForCommunity;
    @Nullable
    private String privateChannelID;
    @Nullable
    private String cardsInfoThreadID;

    private int crf;
    private int hrf;
    private int irf;
    private int vrf;
    private ArrayList<String> fragments = new ArrayList<>();
    private List<String> relics = new ArrayList<>();
    private List<String> exhaustedRelics = new ArrayList<>();
    private LinkedHashSet<Integer> SCs = new LinkedHashSet<>();

    //BENTOR CONGLOMERATE ABILITY "Ancient Blueprints"
    private boolean hasFoundCulFrag;
    private boolean hasFoundHazFrag;
    private boolean hasFoundIndFrag;
    private boolean hasFoundUnkFrag;

    //OLRADIN POLICY ONCE PER ACTION EXHAUST PLANET ABILITIES
    @Setter
    private boolean hasUsedEconomyEmpowerAbility;
    @Setter
    private boolean hasUsedEconomyExploitAbility;
    @Setter
    private boolean hasUsedEnvironmentPreserveAbility;
    @Setter
    private boolean hasUsedPeopleConnectAbility;

    // Statistics
    private int numberOfTurns;
    private long totalTimeSpent;

    private final Tile nomboxTile = new Tile("nombox", "nombox");

    public Player() {
    }

    public Player(@JsonProperty("userID") String userID, @JsonProperty("userName") String userName, @JsonProperty("gameID") String gameID) {
        this.userID = userID;
        this.userName = userName;
        this.gameID = gameID;
    }

    public Game getGame() {
        return GameManager.getInstance().getGame(this.gameID);
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
            // BotLogger.log("Could not retrieve privateChannel for " + getName(), e);
        }
        return null;
    }

    public String getCardsInfoThreadID() {
        return cardsInfoThreadID;
    }

    public boolean hasPDS2Tech() {
        return getTechs().contains("ht2") || getTechs().contains("pds2") || getTechs().contains("dsgledpds") || getTechs().contains("dsmirvpds");
    }

    public boolean hasInf2Tech() {//"dszeliinf"
        return getTechs().contains("cl2") || getTechs().contains("so2") || getTechs().contains("inf2") || getTechs().contains("lw2") || getTechs().contains("dscymiinf")
            || getTechs().contains("dszeliinf");
    }

    public boolean hasWarsunTech() {
        return getTechs().contains("pws2") || getTechs().contains("dsrohdws") || getTechs().contains("ws") || "muaat".equalsIgnoreCase(getFaction());
    }

    public boolean hasFF2Tech() {
        return getTechs().contains("ff2") || getTechs().contains("hcf2") || getTechs().contains("dsflorff") || getTechs().contains("dslizhff");
    }

    public void setCardsInfoThreadID(String cardsInfoThreadID) {
        this.cardsInfoThreadID = cardsInfoThreadID;
    }

    @JsonIgnore
    public ThreadChannel getCardsInfoThread() {
        Game activeGame = getGame();
        TextChannel actionsChannel = activeGame.getMainGameChannel();
        if (activeGame.isFoWMode() || activeGame.isCommunityMode()) actionsChannel = (TextChannel) getPrivateChannel();
        if (actionsChannel == null) {
            BotLogger.log("`Helper.getPlayerCardsInfoThread`: actionsChannel is null for game, or community game private channel not set: " + activeGame.getName());
            return null;
        }

        String threadName = Constants.CARDS_INFO_THREAD_PREFIX + activeGame.getName() + "-" + getUserName().replaceAll("/", "");
        if (activeGame.isFoWMode()) {
            threadName = activeGame.getName() + "-" + "cards-info-" + getUserName().replaceAll("/", "") + "-private";
        }

        //ATTEMPT TO FIND BY ID
        String cardsInfoThreadID = getCardsInfoThreadID();
        try {
            if (cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !cardsInfoThreadID.isEmpty() && !"null".equals(cardsInfoThreadID)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using ID: " + cardsInfoThreadID + " for potential thread name: " + threadName, e);
        }

        //ATTEMPT TO FIND BY NAME
        try {
            if (cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !cardsInfoThreadID.isEmpty() && !"null".equals(cardsInfoThreadID)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        setCardsInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using name: " + threadName, e);
        }

        // CREATE NEW THREAD
        //Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!activeGame.isCommunityMode() && !activeGame.isFoWMode());
        if (activeGame.getName().contains("pbd100") || activeGame.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = actionsChannel.createThreadChannel(threadName, isPrivateChannel);
        threadAction.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        if (isPrivateChannel) {
            threadAction.setInvitable(false);
        }
        ThreadChannel threadChannel = threadAction.complete();
        setCardsInfoThreadID(threadChannel.getId());
        return threadChannel;
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

    public void setReadyToPassBag(boolean passed) {
        readyToPassBag = passed;
    }

    public HashSet<String> getAbilities() {
        return abilities;
    }

    public void setAbilities(HashSet<String> abilities) {
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

    public HashSet<String> getExhaustedAbilities() {
        return exhaustedAbilities;
    }

    public void setExhaustedAbilities(HashSet<String> exhaustedAbilities) {
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

    public HashMap<String, Integer> getUnitCaps() {
        return unitCaps;
    }

    public void setUnitCap(String unit, int cap) {
        unitCaps.put(unit, cap);
    }

    public LinkedHashMap<String, Integer> getActionCards() {
        return actionCards;
    }

    public LinkedHashMap<String, Integer> getTrapCards() {
        return trapCards;
    }

    public LinkedHashMap<String, String> getTrapCardsPlanets() {
        return trapCardsPlanets;
    }

    public HashSet<String> getPromissoryNotesOwned() {
        return promissoryNotesOwned;
    }

    public void setPromissoryNotesOwned(HashSet<String> promissoryNotesOwned) {
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

    public LinkedHashMap<String, Integer> getPromissoryNotes() {
        return promissoryNotes;
    }

    public List<String> getPromissoryNotesInPlayArea() {
        return promissoryNotesInPlayArea;
    }

    public HashSet<String> getUnitsOwned() {
        return unitsOwned;
    }

    public boolean hasUnit(String unit) {
        return unitsOwned.contains(unit);
    }

    public void setUnitsOwned(HashSet<String> unitsOwned) {
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

    public UnitModel getUnitByType(String unitType) {
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

        if (StringUtils.isNotBlank(unit.getFaction()) && StringUtils.isNotBlank(unit.getUpgradesFromUnitId()))
            score += 4;
        if (StringUtils.isNotBlank(unit.getFaction()))
            score += 3;
        if (StringUtils.isNotBlank(unit.getUpgradesFromUnitId()))
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
                String message = "> Warning - Player: " + getUserName() + " has more than one of the same unit type.\n> Unit Counts: `" + getUnitsOwnedByBaseType() + "`\n> Units Owned: `"
                    + getUnitsOwned() + "`";
                BotLogger.log(message);
                return message;
            }
        }
        return null;
    }

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

    public void setPromissoryNotes(LinkedHashMap<String, Integer> promissoryNotes) {
        this.promissoryNotes = promissoryNotes;
    }

    public void removePromissoryNotesInPlayArea(String id) {
        promissoryNotesInPlayArea.remove(id);
    }

    public void setActionCard(String id, Integer identifier) {
        actionCards.put(id, identifier);
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

    public LinkedHashMap<String, Integer> getSecrets() {
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

    public LinkedHashMap<String, Integer> getSecretsScored() {
        return secretsScored;
    }

    public void setSecretScored(String id) {
        Collection<Integer> values = secretsScored.values();
        List<Integer> allIDs = getGame().getPlayers().values().stream().flatMap(player -> player.getSecretsScored().values().stream()).toList();
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

    public int getVrf() {
        return vrf;
    }

    public ArrayList<String> getFragments() {
        return fragments;
    }

    public boolean enoughFragsForRelic() {
        boolean enough = false;
        int haz = 0;
        int ind = 0;
        int cult = 0;
        int frontier = 0;
        for (String id : fragments) {
            String[] cardInfo = Mapper.getExplore(id).split(";");
            if ("hazardous".equalsIgnoreCase(cardInfo[1])) {
                haz = haz + 1;
            } else if (cardInfo[1].equalsIgnoreCase(Constants.FRONTIER)) {
                frontier = frontier + 1;
            } else if ("industrial".equalsIgnoreCase(cardInfo[1])) {
                ind = ind + 1;
            } else if ("cultural".equalsIgnoreCase(cardInfo[1])) {
                cult = cult + 1;
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

    public void setFragments(ArrayList<String> fragmentList) {
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
            String color = Mapper.getExplore(cardID).split(";")[1].toLowerCase();
            switch (color) {
                case Constants.CULTURAL -> {
                    crf++;
                    hasFoundCulFrag = true;
                }
                case Constants.INDUSTRIAL -> {
                    irf++;
                    hasFoundIndFrag = true;
                }
                case Constants.HAZARDOUS -> {
                    hrf++;
                    hasFoundHazFrag = true;
                }
                case Constants.FRONTIER -> {
                    vrf++;
                    hasFoundUnkFrag = true;
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

    public String getUserID() {
        return userID;
    }

    public String getUserName() {
        User userById = AsyncTI4DiscordBot.jda.getUserById(userID);
        if (userById != null) {
            userName = userById.getName();
            Member member = AsyncTI4DiscordBot.guildPrimary.getMemberById(userID);
            if (member != null) userName = member.getEffectiveName();
        }
        return userName;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
        initLeaders();
        initAbilities();
    }

    @NotNull
    public String getFactionEmoji() {
        if (factionEmoji != null && !factionEmoji.isBlank() && !factionEmoji.isEmpty() && !"null".equals(factionEmoji)) {
            return factionEmoji;
        }
        return Helper.getFactionIconFromDiscord(getFaction());
    }

    public String getFactionEmojiOrColour() {
        if (getGame().isFoWMode()) {
            return Helper.getColourAsMention(getGame().getGuild(), getColor());
        }
        return getFactionEmoji();
    }

    public void setFactionEmoji(String factionEmoji) {
        this.factionEmoji = factionEmoji;
    }

    public boolean hasCustomFactionEmoji() {
        return factionEmoji != null && !factionEmoji.isBlank() && !factionEmoji.isEmpty() && !"null".equals(factionEmoji) && !factionEmoji.equalsIgnoreCase(Helper.getFactionIconFromDiscord(getFaction()));
    }

    private void initAbilities() {
        HashSet<String> abilities = new HashSet<>();
        for (String ability : getFactionStartingAbilities()) {
            if (!ability.isEmpty() && !ability.isBlank()) {
                abilities.add(ability);
            }
        }
        setAbilities(abilities);
        if (faction.equals(Constants.LIZHO)) {
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
        if (faction == null || "null".equals(faction) || "keleres".equals(faction)) return null;
        FactionModel factionSetupInfo = Mapper.getFactionSetup(faction);
        if (factionSetupInfo == null) {
            BotLogger.log("Could not get faction setup info for: " + faction);
            return null;
        }
        return factionSetupInfo;
    }

    private List<String> getFactionStartingAbilities() {
        FactionModel factionSetupInfo = getFactionSetupInfo();
        if (factionSetupInfo == null) return new ArrayList<>();
        return new ArrayList<>(factionSetupInfo.getAbilities());
    }

    private List<String> getFactionStartingLeaders() {
        FactionModel factionSetupInfo = getFactionSetupInfo();
        if (factionSetupInfo == null) return new ArrayList<>();
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

    public boolean hasUnexhaustedLeader(String leaderId) {
        if (hasLeader(leaderId)) {
            return !getLeaderByID(leaderId).map(Leader::isExhausted).orElse(true);
        }
        return false;
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
     * @return whether a player has access to this leader, typically by way of Yssaril Agent
     */
    public boolean hasExternalAccessToLeader(String leaderID) {
        if (!hasLeader(leaderID) && getLeaderIDs().contains("yssarilagent")) {
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
        if (getGame() != null && color != null && faction != null && Mapper.isColorValid(color) && Mapper.isFaction(faction)) {
            promissoryNotes.clear();
            List<String> promissoryNotes = Mapper.getColourFactionPromissoryNoteIDs(getGame(), color, faction);
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

    public int getPublicVictoryPoints() {
        Game activeGame = getGame();
        LinkedHashMap<String, List<String>> scoredPOs = activeGame.getScoredPublicObjectives();
        int vpCount = 0;
        for (Entry<String, List<String>> scoredPOEntry : scoredPOs.entrySet()) {
            if (scoredPOEntry.getValue().contains(getUserID())) {
                String poID = scoredPOEntry.getKey();
                try {
                    PublicObjectiveModel po = Mapper.getPublicObjective(poID);
                    if (po != null) {//IS A PO
                        vpCount += po.getPoints();
                    } else { //IS A CUSTOM PO
                        int frequency = Collections.frequency(scoredPOEntry.getValue(), userID);
                        int poValue = activeGame.getCustomPublicVP().getOrDefault(poID, 0);
                        vpCount += poValue * frequency;
                    }
                } catch (Exception e) {
                    BotLogger.log("`Player.getPublicVictoryPoints   map=" + activeGame.getName() + "  player=" + getUserName() + "` - error finding value of `PO_ID=" + poID, e);
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
        return getPublicVictoryPoints() + getSecretVictoryPoints() + getSupportForTheThroneVictoryPoints();
    }

    public void setTg(int tg) {
        this.tg = tg;
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

    public LinkedHashSet<Integer> getSCs() {
        return SCs;
    }

    public void setSCs(LinkedHashSet<Integer> SCs) {
        this.SCs = SCs;
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
            return Collections.min(getSCs());
        } catch (NoSuchElementException e) {
            return 100;
        }
    }

    public int getCommodities() {
        return commodities;
    }

    public void setCommodities(int commodities) {
        if(commodities > getCommoditiesTotal()){
            commodities = getCommoditiesTotal();
        }
        this.commodities = commodities;
    }

    public List<String> getTechs() {
        return techs;
    }

    public FrankenBag getFrankenHand() {
        return frankenHand;
    }

    public void setFrankenHand(FrankenBag hand) {
        frankenHand = hand;
    }

    public FrankenBag getCurrentFrankenBag() {
        return currentFrankenBag;
    }

    public void setCurrentFrankenBag(FrankenBag bag) {
        currentFrankenBag = bag;
    }

    public FrankenBag getFrankenDraftQueue() {
        return frankenItemsToDraft;
    }

    public boolean hasTech(String techID) {
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

    public void loadFrankenHand(List<String> saveString) {
        FrankenBag newBag = new FrankenBag();
        for(String item : saveString){
            newBag.Contents.add(FrankenItem.GenerateFromAlias(item));
        }
        this.frankenHand = newBag;
    }

    public void loadCurrentFrankenBag(List<String> saveString) {
        FrankenBag newBag = new FrankenBag();
        for(String item : saveString){
            newBag.Contents.add(FrankenItem.GenerateFromAlias(item));
        }
        this.currentFrankenBag = newBag;
    }

    public void loadFrankenItemsToDraft(List<String> saveString) {
        List<FrankenItem> items = new ArrayList<>();
        for(String item : saveString){
            items.add(FrankenItem.GenerateFromAlias(item));
        }
        this.frankenItemsToDraft.Contents = items;
    }

    public void queueFrankenItemToDraft(FrankenItem item) {
        this.frankenItemsToDraft.Contents.add(item);
    }

    public void resetFrankenItemDraftQueue() {
        this.frankenItemsToDraft.Contents.clear();
    }

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

    public void addTech(String techID) {
        if (techs.contains(techID)) {
            return;
        }
        techs.add(techID);

        doAdditionalThingsWhenAddingTech(techID);
    }

    private void doAdditionalThingsWhenAddingTech(String techID) {
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
        if (techID == null) return;

        if (techModel.getType() == TechnologyType.UNITUPGRADE) {
            UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
            if (unitModel != null && unitModel.getBaseType() != null) {
                // Remove all non-faction-upgrade matching units
                String asyncId = unitModel.getAsyncId();
                List<UnitModel> unitsToRemove = getUnitsByAsyncID(asyncId).stream()
                    .filter(unit -> unit.getFaction() == null || unit.getUpgradesFromUnitId() == null).toList();
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
        if (techID == null) return;

        if (techModel.getType() == TechnologyType.UNITUPGRADE) {
            UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
            List<TechnologyModel> relevantTechs = getTechs().stream().map(Mapper::getTech).filter(tech -> tech.getBaseUpgrade().equals(unitModel.getBaseType())).toList();
            removeOwnedUnitByID(unitModel.getId());

            // Find another unit model to replace this lost model
            String replacementUnit = unitModel.getBaseType(); // default
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
        if (isRemoved) refreshTech(tech);
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
    }

    public void exhaustPlanetAbility(String planet) {
        if (planets.contains(planet) && !exhaustedPlanetsAbilities.contains(planet)) {
            exhaustedPlanetsAbilities.add(planet);
        }
    }

    public void refreshPlanet(String planet) {
        boolean isRemoved = exhaustedPlanets.remove(planet);
        if (isRemoved) refreshPlanet(planet);
    }

    public void refreshPlanetAbility(String planet) {
        boolean isRemoved = exhaustedPlanetsAbilities.remove(planet);
        if (isRemoved) refreshPlanetAbility(planet);
    }

    public void removePlanet(String planet) {
        planets.remove(planet);
        refreshPlanet(planet);
        refreshPlanetAbility(planet);
    }

    public int getStasisInfantry() {
        return stasisInfantry;
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
        if (tileID == null) tileID = "0b";

        String label = fow_customLabels.get(position);
        if (label == null) label = "";

        return new Tile(tileID, position, player, true, label);
    }

    public HashMap<String, String> getFogTiles() {
        return fow_seenTiles;
    }

    public HashMap<String, String> getFogLabels() {
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
     * @return true if the player is: not a "dummy", faction != null, color != null, & color != "null"
     */
    @JsonIgnore
    public boolean isRealPlayer() {
        return !(isDummy || faction == null || color == null || "null".equals(color));
    }

    /**
     * @return true if the player is: a "dummy", faction == null, color == null, & color == "null"
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
                faction = Mapper.getFactionRepresentations().get(faction);
            }

            String color = getColor();
            if (color == null || "null".equals(color)) color = "No Color";

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

    //BENTOR CONGLOMERATE ABILITY "Ancient Blueprints"
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

    public void addDebtTokens(String tokenColour, int count) {
        if (debt_tokens.containsKey(tokenColour)) {
            debt_tokens.put(tokenColour, debt_tokens.get(tokenColour) + count);
        } else {
            debt_tokens.put(tokenColour, count);
        }
    }

    public void removeDebtTokens(String tokenColour, int count) {
        if (debt_tokens.containsKey(tokenColour)) {
            debt_tokens.put(tokenColour, Math.max(debt_tokens.get(tokenColour) - count, 0));
        }
    }

    public void clearAllDebtTokens(String tokenColour) {
        debt_tokens.remove(tokenColour);
    }

    public int getDebtTokenCount(String tokenColour) {
        return debt_tokens.getOrDefault(tokenColour, 0);
    }

    public String getPlayerStatsAnchorPosition() {
        if ("null".equals(playerStatsAnchorPosition)) return null;
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
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String colorID = Mapper.getColorID(getColor());
        String mechKey = colorID + "_mf.png";
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty()) continue;

            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean hasProductionUnitInSystem(Tile tile) {
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String colorID = Mapper.getColorID(getColor());
        String mechKey;
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty()) continue;
            mechKey = colorID + "_sd.png";
            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
            mechKey = colorID + "_csd.png";
            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
            if (hasUnit("arborec_mech")) {
                mechKey = colorID + "_mf.png";
                if (unitHolder.getUnits().get(mechKey) != null) {
                    return true;
                }
            }
            if (hasUnit("arborec_infantry") || hasTech("lw2")) {
                mechKey = colorID + "_gf.png";
                if (unitHolder.getUnits().get(mechKey) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<Player> getNeighbouringPlayers() {
        Game activeGame = getGame();
        Set<Player> adjacentPlayers = new HashSet<>();
        Set<Player> realPlayers = new HashSet<>(activeGame.getPlayers().values().stream().filter(Player::isRealPlayer).toList());

        Set<Tile> playersTiles = new HashSet<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerIsInSystem(activeGame, tile, this)) {
                playersTiles.add(tile);
            }
        }

        for (Tile tile : playersTiles) {
            adjacentPlayers.addAll(FoWHelper.getAdjacentPlayers(activeGame, tile.getPosition(), false));
            if (realPlayers.size() == adjacentPlayers.size()) break;
        }
        adjacentPlayers.remove(this);
        return adjacentPlayers;
    }

    public int getNeighbourCount() {
        return getNeighbouringPlayers().size();
    }
}
