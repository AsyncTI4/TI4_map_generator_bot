package ti4.map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.*;

public class Player {
    
    private String userID;
    private String userName;

    private boolean passed = false;
    private boolean searchWarrant = false;
    private boolean isDummy = false;

    private String faction;
    private String color;

    private int tacticalCC = 3;
    private int fleetCC = 3;
    private int strategicCC = 2;

    private int tg = 0;
    private int commodities = 0;
    private int commoditiesTotal = 0;

    private LinkedHashMap<String, Integer> actionCards = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> secrets = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> promissoryNotes = new LinkedHashMap<>();
    private List<String> promissoryNotesInPlayArea = new ArrayList<>();
    private List<String> techs = new ArrayList<>();
    private List<String> exhaustedTechs = new ArrayList<>();
    private List<String> planets = new ArrayList<>();
    private List<String> exhaustedPlanets = new ArrayList<>();
    private List<String> exhaustedPlanetsAbilities = new ArrayList<>();
    private List<String> mahactCC = new ArrayList<>();
    private List<Leader> leaders = new ArrayList<>();

    private HashMap<String,String> debt_tokens = new HashMap<>();
    private HashMap<String,String> fow_seenTiles = new HashMap<>();
    private HashMap<String,String> fow_customLabels = new HashMap<>();
    private String fowFogFilter = null;

    @Nullable
    private Role roleForCommunity = null;
    @Nullable
    private MessageChannel privateChannel = null;


    private int crf = 0;
    private int hrf = 0;
    private int irf = 0;
    private int vrf = 0;
    private ArrayList<String> fragments = new ArrayList<>();
    private List<String> relics = new ArrayList<>();
    private List<String> exhaustedRelics = new ArrayList<>();
    private int SC = 0;

    // Statistics
    private int numberOfTurns = 0;
    private long totalTimeSpent = 0;

    public Player(String userID, String userName) {
        this.userID = userID;
        this.userName = userName;
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

    public void setPromissoryNotesInPlayArea(List<String> promissoryNotesInPlayArea) {
        this.promissoryNotesInPlayArea = promissoryNotesInPlayArea;
    }

    public void removePromissoryNotesInPlayArea(String id) {
        promissoryNotesInPlayArea.remove(id);
    }

    public void setActionCard(String id, Integer identifier) {
        actionCards.put(id, identifier);
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
                case Constants.CULTURAL -> crf++;
                case Constants.INDUSTRIAL -> irf++;
                case Constants.HAZARDOUS -> hrf++;
                case Constants.FRONTIER -> vrf++;
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

    public void setTg(int tg) {
        this.tg = tg;
    }

    public int getAc() {
        return actionCards.size();
    }

    public int getPnCount() {
        return (promissoryNotes.size() - promissoryNotesInPlayArea.size());
    }

    public int getSo() {
        return secrets.size();
    }

    public int getSoScored() {
        return secretsScored.size();
    }

    public int getSC() {
        return SC;
    }

    public void setSC(int SC) {
        this.SC = SC;
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

    public void cleanExhaustedPlanets() {
        exhaustedPlanets.clear();
        exhaustedPlanetsAbilities.clear();
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

    public Tile buildFogTile(String position) {
        String tileID = fow_seenTiles.get(position);
        if (tileID == null) tileID = "0b";

        String label = fow_customLabels.get(position);
        if (label == null) label = "";

        return new Tile(tileID, position, true, label);
    }

    public HashMap<String,String> getFogTiles() {
        return fow_seenTiles;
    }

    public HashMap<String,String> getFogLabels() {
        return fow_customLabels;
    }

    public boolean isDummy() {
        return isDummy;
    }
    
    public void setDummy(boolean isDummy) {
        this.isDummy = isDummy;
    }

    public boolean isActivePlayer() {
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
}
