package ti4.map;

import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;

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


    private int crf = 0;
    private int hrf = 0;
    private int irf = 0;
    private int vrf = 0;
    private HashSet<String> relics = new HashSet<>();
    private int SC = 0;


    public Player(String userID, String userName) {
        this.userID = userID;
        this.userName = userName;
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

    public void setCrf(int crf) {
        this.crf = crf;
    }

    public int getIrf() {
        return irf;
    }

    public void setIrf(int irf) {
        this.irf = irf;
    }

    public int getHrf() {
        return hrf;
    }

    public void setHrf(int hrf) {
        this.hrf = hrf;
    }

    public int getVrf() {
        return vrf;
    }

    public void setVrf(int vrf) {
        this.vrf = vrf;
    }

    public String getUserID() {
        return userID;
    }

    public String getUserName() {
        return userName;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
        initPNs();
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
