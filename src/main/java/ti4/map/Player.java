package ti4.map;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.message.BotLogger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.Map;
import java.util.Map.Entry;
import java.util.*;

public class Player {
    
    private String userID;
    private String userName;

    private boolean passed = false;
    private boolean searchWarrant = false;
    private boolean isDummy = false;

    private String faction;
    private String color;
    private String autoCompleteRepresentation = null;

    private int tacticalCC = 3;
    private int fleetCC = 3;
    private int strategicCC = 2;

    private int tg = 0;
    private int commodities = 0;
    private int commoditiesTotal = 0;
    private int stasisInfantry = 0;

    private Set<Integer> followedSCs = new HashSet<>();

    private LinkedHashMap<String, Integer> actionCards = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> secrets = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> promissoryNotes = new LinkedHashMap<>();
    private HashSet<String> factionAbilities = new HashSet<>();
    private List<String> promissoryNotesInPlayArea = new ArrayList<>();
    private List<String> techs = new ArrayList<>();
    private List<String> exhaustedTechs = new ArrayList<>();
    private List<String> planets = new ArrayList<>();
    private List<String> exhaustedPlanets = new ArrayList<>();
    private List<String> exhaustedPlanetsAbilities = new ArrayList<>();
    private List<String> mahactCC = new ArrayList<>();

    @JsonProperty("leaders")
    private List<Leader> leaders = new ArrayList<>();

    private HashMap<String,String> debt_tokens = new HashMap<>();
    private HashMap<String,String> fow_seenTiles = new HashMap<>();
    private HashMap<String,String> fow_customLabels = new HashMap<>();
    private String fowFogFilter = null;
    private boolean fogInitialized = false;

    @Nullable
    private Role roleForCommunity = null;
    @Nullable
    private MessageChannel privateChannel = null;
    @Nullable
    private String cardsInfoThreadID = null;

    private int crf = 0;
    private int hrf = 0;
    private int irf = 0;
    private int vrf = 0;
    private ArrayList<String> fragments = new ArrayList<>();
    private List<String> relics = new ArrayList<>();
    private List<String> exhaustedRelics = new ArrayList<>();
    private LinkedHashSet<Integer> SCs = new LinkedHashSet<>();

    //BENTOR CONGLOMERATE ABILITY "Ancient Blueprints"
    private boolean hasFoundCulFrag = false;
    private boolean hasFoundHazFrag = false;
    private boolean hasFoundIndFrag = false;
    private boolean hasFoundUnkFrag = false;

    // Statistics
    private int numberOfTurns = 0;
    private long totalTimeSpent = 0;

    private Tile nomboxTile = new Tile("nombox", "nombox");

    public Player() {
    }

    public Player(@JsonProperty("userID") String userID,
                  @JsonProperty("userName") String userName) {
        this.userID = userID;
        this.userName = userName;
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

    @Nullable
    public Role getRoleForCommunity() {
        return roleForCommunity;
    }

    public void setRoleForCommunity(Role roleForCommunity) {
        this.roleForCommunity = roleForCommunity;
    }

    @Nullable
    public MessageChannel getPrivateChannel() {
        return privateChannel;
    }

    public void setPrivateChannel(MessageChannel privateChannel) {
        this.privateChannel = privateChannel;
    }

    public String getCardsInfoThreadID() {
        return cardsInfoThreadID;
    }

    public void setCardsInfoThreadID(String cardsInfoThreadID) {
        this.cardsInfoThreadID = cardsInfoThreadID;
    }

    @JsonIgnore
    public ThreadChannel getCardsInfoThread(ti4.map.Map activeMap) {
        TextChannel actionsChannel = (TextChannel) activeMap.getMainGameChannel();
        if (activeMap.isFoWMode()) actionsChannel = (TextChannel) getPrivateChannel();
        if (actionsChannel == null) {
            BotLogger.log("`Helper.getPlayerCardsInfoThread`: actionsChannel is null for game: " + activeMap.getName());
            return null;
        }

        String threadName = Constants.CARDS_INFO_THREAD_PREFIX + activeMap.getName() + "-" + getUserName().replaceAll("/", "");
        if(activeMap.isFoWMode())
            {
                threadName = activeMap.getName() + "-" + "cards-info-"+ getUserName().replaceAll("/", "") + "-private";
            }

        //ATTEMPT TO FIND BY ID
        String cardsInfoThreadID = getCardsInfoThreadID();
        try {
            if (cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !cardsInfoThreadID.isEmpty() && !cardsInfoThreadID.equals("null")) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;
    
                ThreadChannel threadChannel = MapGenerator.jda.getThreadChannelById(cardsInfoThreadID);
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
            // TODO: handle exception
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using ID: " + cardsInfoThreadID + " for potential thread name: " + threadName, e);
        }

        //ATTEMPT TO FIND BY NAME
        try {
            if (cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !cardsInfoThreadID.isEmpty() && !cardsInfoThreadID.equals("null")) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;
    
                ThreadChannel threadChannel = MapGenerator.jda.getThreadChannelById(cardsInfoThreadID);
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
            // TODO: handle exception
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using name: " + threadName, e);
        }
        
        // CREATE NEW THREAD
        //Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!activeMap.isCommunityMode() && !activeMap.isFoWMode());
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

    public HashSet<String> getFactionAbilities() {
        return factionAbilities;
    }

    public void setFactionAbilities(HashSet<String> factionAbilities) {
        this.factionAbilities = factionAbilities;
    }

    /**
     * @param abilityID The ID of the ability - does not check if valid
     * @see faction_abilities.properties
     */
    public void addFactionAbility(String abilityID) {
        factionAbilities.add(abilityID);
    }

    public void removeFactionAbility(String abilityID) {
        factionAbilities.remove(abilityID);
    }

    public LinkedHashMap<String, Integer> getActionCards() {
        return actionCards;
    }

    public LinkedHashMap<String, Integer> getPromissoryNotes() {
        return promissoryNotes;
    }

    public List<String> getPromissoryNotesInPlayArea() {
        return promissoryNotesInPlayArea;
    }

    public void setActionCard(String id) {
        Collection<Integer> values = actionCards.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
        }
        actionCards.put(id, identifier);
    }

    public void setPromissoryNote(String id) {
        Collection<Integer> values = promissoryNotes.values();
        int identifier = new Random().nextInt(100);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(100);
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
        List<String> replaced = new ArrayList<>();
        for (String id : promissoryNotesInPlayArea) {
            id = id.replace("torquoise", "turquoise");
            replaced.add(id);
        }

        this.promissoryNotesInPlayArea = replaced;
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

    public void setPromissoryNote(String id, Integer identifier) {
        id = id.replace("torquoise", "turquoise");
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
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
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

    public void setSecretScored(String id, ti4.map.Map map) {
        Collection<Integer> values = secretsScored.values();
        List<Integer> allIDs = map.getPlayers().values().stream().flatMap(player -> player.getSecretsScored().values().stream()).toList();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier) || allIDs.contains(identifier)) {
            identifier = new Random().nextInt(1000);
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
            if (relicID.equals("dynamiscore")){
                commoditiesTotal += 2;
            }
            relics.add(relicID);
        }
    }

    public void addExhaustedRelic(String relicID) {
        exhaustedRelics.add(relicID);
    }

    public void removeRelic(String relicID) {
        if (relicID.equals("dynamiscore")){
            commoditiesTotal -= 2;
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
        User userById = MapGenerator.jda.getUserById(userID);
        if (userById != null) {
            userName = userById.getName();
            Member member = MapGenerator.guildPrimary.getMemberById(userID);
            if (member != null) userName = member.getEffectiveName();
        }
        return userName;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
        initPNs();
        initLeaders();
        initAbilities();
    }

    private void initAbilities() {
        HashSet<String> abilities = new HashSet<>();
        for (String ability : getFactionStartingAbilities()) {
            if (ability.isEmpty() || ability.isBlank()){
                continue;
            } else {
                abilities.add(ability);
            }
        }
        setFactionAbilities(abilities);
    }

    @JsonIgnore
    public String[] getFactionSetupInfo() {
        if (faction == null || faction.equals("null") || faction.equals("keleres")) return null;
        String factionSetupInfo = Mapper.getPlayerSetup(faction);
        if (factionSetupInfo == null) {
            BotLogger.log("Could not get faction setup info for: " + faction);
            return null;
        }
        long count = factionSetupInfo.chars().filter(ch -> ch == ';').count();
        int expectedTokenCount = 7;
        if (count != expectedTokenCount) {
            BotLogger.log("Faction setup raw text is incorrectly formatted (needs " + (expectedTokenCount - 1) + " ; to split properly):\n> " + factionSetupInfo);
            return null;
        }
        String[] setupInfo = factionSetupInfo.split(";");
        return setupInfo;
    }
    
    private List<String> getFactionStartingAbilities() {
        String[] factionSetupInfo = getFactionSetupInfo();
        if(factionSetupInfo == null) return new ArrayList<String>();
        return Arrays.asList(getFactionSetupInfo()[7].split(","));
    }

    public void initLeaders() {
        if (faction != null && Mapper.isFaction(faction)) {
            leaders.clear();
            HashMap<String, HashMap<String, ArrayList<String>>> leadersInfo = Mapper.getLeadersInfo();
            HashMap<String, ArrayList<String>> factionLeaders = leadersInfo.get(faction);
            if (factionLeaders != null) {
                for (Map.Entry<String, ArrayList<String>> factionLeaderEntry : factionLeaders.entrySet()) {
                    String leaderType = factionLeaderEntry.getKey();
                    ArrayList<String> uniqueLeaders = factionLeaderEntry.getValue();
                    if (uniqueLeaders.isEmpty()){
                        Leader leader = new Leader(leaderType, "");
                        leaders.add(leader);
                    } else {
                        for (String uniqueLeader : uniqueLeaders) {
                            Leader leader = new Leader(leaderType, uniqueLeader);
                            leaders.add(leader);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    public Leader getLeader(String leaderID) {
        for (Leader leader : leaders) {
            if (leader.getId().equals(leaderID) || leader.getName().equals(leaderID)){
                return leader;
            }
        }
        return null;
    }

    public List<Leader> getLeaders() {
        return leaders;
    }

    public void setLeaders(List<Leader> leaders) {
        this.leaders = leaders;
    }

    public boolean removeLeader(String leaderID) {
        Leader leaderToPurge = null;
        for (Leader leader : leaders) {
            if (leader.getId().equals(leaderID) || leader.getName().equals(leaderID)){
                leaderToPurge = leader;
                break;
            }
        }
        if (leaderToPurge == null){
            return false;
        }
        return leaders.remove(leaderToPurge);
    }

    public boolean addLeader(String leaderID) {
        Leader leaderToPurge = null;
        for (Leader leader : leaders) {
            if (leader.getId().equals(leaderID) || leader.getName().equals(leaderID)){
                return false;
            }
        }
        if (leaderToPurge == null){
            return false;
        }
        return leaders.remove(leaderToPurge);
    }

    public String getColor() {
        return color != null ? color : "null";
    }

    public void setColor(String color) {
        if (!color.equals("null")) {
            this.color = AliasHandler.resolveColor(color);
        }
        initPNs();
    }

    public void changeColor(String color) {
        if (!color.equals("null")) {
            this.color = AliasHandler.resolveColor(color);
        }
    }

    public void initPNs() {
        if (color != null && faction != null && Mapper.isColorValid(color) && Mapper.isFaction(faction)) {
            promissoryNotes.clear();
            List<String> promissoryNotes = Mapper.getPromissoryNotes(color, faction);
            for (String promissoryNote : promissoryNotes) {
                if ("mahact".equals(faction) && promissoryNote.endsWith("_an")){
                    continue;
                }
                setPromissoryNote(promissoryNote);
            }
        }
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

    public int getPublicVictoryPoints(ti4.map.Map map) {
        LinkedHashMap<String, List<String>> scoredPOs = map.getScoredPublicObjectives();
        int vpCount = 0;
        for (Entry<String, List<String>> scoredPOEntry : scoredPOs.entrySet()) {
            if (scoredPOEntry.getValue().contains(getUserID())) {
                String poID = scoredPOEntry.getKey();
                try {
                    String poText = Mapper.getPublicObjective(poID);
                    if (poText != null) {//IS A PO 
                        int poValue = Integer.valueOf(poText.split(";")[3]);
                        vpCount += poValue;
                    } else { //IS A CUSTOM PO
                        int frequency = Collections.frequency(scoredPOEntry.getValue(), userID);
                        int poValue = map.getCustomPublicVP().getOrDefault(poID, 0);
                        vpCount += poValue * frequency;
                    }
                } catch (Exception e) {
                    BotLogger.log("`Player.getPublicVictoryPoints   map=" + map.getName() + "  player=" + getUserName() + "` - error finding value of `PO_ID=" + poID, e);
                }
            }
        }

        return vpCount;
    }

    @JsonIgnore
    public int getSecretVictoryPoints() {
        return getSecretsScored().size();
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
    public int getTotalVictoryPoints(ti4.map.Map activeMap) {
        return getPublicVictoryPoints(activeMap) + getSecretVictoryPoints() + getSupportForTheThroneVictoryPoints();
    }

    public void setTg(int tg) {
        this.tg = tg;
    }

    public void setFollowedSCs(Set<Integer> followedSCs) {
        this.followedSCs = followedSCs;
    }

    public void addFollowedSC(Integer sc) {
        this.followedSCs.add(sc);
    }

    public void removeFollowedSC(Integer sc) {
        this.followedSCs.remove(sc);
    }

    public boolean hasFollowedSC(int sc) {
        return getFollowedSCs().contains(sc);
    }

    public void clearFollowedSCs() {
        this.followedSCs.clear();
    }

    public Set<Integer> getFollowedSCs() {
        return this.followedSCs;
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
        this.commodities = commodities;
    }

    public List<String> getTechs() {
        return techs;
    }

    public List<String> getPlanets() {
        return planets;
    }

    public void setPlanets(List<String> planets) {
        this.planets = planets;
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

    public void addTech(String tech) {
        if (!techs.contains(tech)) {
            techs.add(tech);
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
        boolean isRemoved = techs.remove(tech);
        if (isRemoved) removeTech(tech);
        refreshTech(tech);
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
        if(isRemoved) refreshPlanet(planet);
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
        if(label != null && !label.equals(".") && !label.equals("")) {
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

    public HashMap<String,String> getFogTiles() {
        return fow_seenTiles;
    }

    public HashMap<String,String> getFogLabels() {
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

    @JsonIgnore
    public boolean isRealPlayer() {
        return !(isDummy || faction == null || color == null || color.equals("null"));
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
        if (reset || this.autoCompleteRepresentation == null) {
            String faction = getFaction();
            if (faction == null || faction == "null") {
                faction = "No Faction";
            } else {
                faction = Mapper.getFactionRepresentations().get(faction);
            }
    
            String color = getColor();
            if (color == null || color == "null") color = "No Color";
    
            String userName = getUserName();
            if (userName == null || userName.isEmpty() || userName.isBlank()) {
                userName = "No User";
            }
            
            String representation = color + " / " + faction + " / " + userName;
            setAutoCompleteRepresentation(representation);
            return getAutoCompleteRepresentation();
        }
        return this.autoCompleteRepresentation;
    }

    public void setAutoCompleteRepresentation(String representation) {
        this.autoCompleteRepresentation = representation;
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
}
