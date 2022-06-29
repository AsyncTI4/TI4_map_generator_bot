package ti4.map;

import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;

import javax.annotation.CheckForNull;
import java.util.Map;
import java.util.*;

public class Player {

    private String userID;
    private String userName;

    private boolean passed = false;

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

    @CheckForNull
    private Role roleForCommunity = null;
    @CheckForNull
    private Channel channelForCommunity = null;


    private int crf = 0;
    private int hrf = 0;
    private int irf = 0;
    private int vrf = 0;
    private ArrayList<String> fragments = new ArrayList<>();
    private List<String> relics = new ArrayList<>();
    private List<String> exhaustedRelics = new ArrayList<>();
    private int SC = 0;


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

    @CheckForNull
    public Role getRoleForCommunity() {
        return roleForCommunity;
    }

    public void setRoleForCommunity(Role roleForCommunity) {
        this.roleForCommunity = roleForCommunity;
    }

    @CheckForNull
    public Channel getChannelForCommunity() {
        return channelForCommunity;
    }

    public void setChannelForCommunity(Channel channelForCommunity) {
        this.channelForCommunity = channelForCommunity;
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

    public void setSecretScored(String id) {
        Collection<Integer> values = secretsScored.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
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
        if (!relics.contains(relicID)) {
            relics.add(relicID);
        }
    }

    public void addExhaustedRelic(String relicID) {
        exhaustedRelics.add(relicID);
    }

    public void removeRelic(String relicID) {
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

    @CheckForNull
    public Leader getLeader(String leaderID) {
        for (Leader leader : leaders) {
            if (leader.getId().equals(leaderID) || leader.getName().equals(leaderID)){
                return leader;
            }
        }
        return null;
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
        return color != null ? color : "white";
    }

    public void setColor(String color) {
        this.color = AliasHandler.resolveColor(color);
        initPNs();
    }

    public void initPNs() {
        if (color != null && faction != null && Mapper.isColorValid(color) && Mapper.isFaction(faction)) {
            promissoryNotes.clear();
            List<String> promissoryNotes = Mapper.getPromissoryNotes(color, faction);
            for (String promissoryNote : promissoryNotes) {
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
        if (techs.contains(tech)) {
            exhaustedTechs.add(tech);
        }
    }

    public void refreshTech(String tech) {
        exhaustedTechs.remove(tech);
    }

    public void removeTech(String tech) {
        techs.remove(tech);
        exhaustedTechs.remove(tech);
    }

    public void addPlanet(String planet) {
        if (!planets.contains(planet)) {
            planets.add(planet);
        }
    }

    public void exhaustPlanet(String planet) {
        if (planets.contains(planet)) {
            exhaustedPlanets.add(planet);
        }
    }

    public void exhaustPlanetAbility(String planet) {
        if (planets.contains(planet)) {
            exhaustedPlanetsAbilities.add(planet);
        }
    }

    public void refreshPlanet(String planet) {
        exhaustedPlanets.remove(planet);
    }

    public void refreshPlanetAbility(String planet) {
        exhaustedPlanetsAbilities.remove(planet);
    }

    public void removePlanet(String planet) {
        planets.remove(planet);
        exhaustedPlanets.remove(planet);
        exhaustedPlanetsAbilities.remove(planet);
    }


    public int getCommoditiesTotal() {
        return commoditiesTotal;
    }

    public void setCommoditiesTotal(int commoditiesTotal) {
        this.commoditiesTotal = commoditiesTotal;
    }
}
