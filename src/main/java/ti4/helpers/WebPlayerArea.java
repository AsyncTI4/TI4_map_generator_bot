package ti4.helpers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Data;
import ti4.map.Leader;
import ti4.map.Player;

@Data
public class WebPlayerArea {
    // Basic properties
    private String userName;
    private String faction;
    private String color;
    private String displayName;
    private boolean passed;
    private boolean eliminated;

    // Command counters
    private int tacticalCC;
    private int fleetCC;
    private int strategicCC;

    // Resources
    private int tg;
    private int commodities;
    private int commoditiesTotal;

    // Fragments
    private int crf;
    private int hrf;
    private int irf;
    private int urf;

    // Units and combat
    private int stasisInfantry;
    private int actualHits;
    private int expectedHitsTimes10;
    private Set<String> unitsOwned;

    // Strategy cards and promissory notes
    private Set<Integer> followedSCs;
    private List<String> promissoryNotesInPlayArea;

    // Technologies
    private List<String> techs;
    private List<String> exhaustedTechs;
    private List<String> factionTechs;
    private List<String> notResearchedFactionTechs;

    // Planets
    private List<String> planets;
    private List<String> exhaustedPlanets;
    private List<String> exhaustedPlanetAbilities;

    // Relics and fragments
    private List<String> fragments;
    private List<String> relics;
    private List<String> exhaustedRelics;

    // Leaders and secrets
    private List<Leader> leaders;
    private List<String> leaderIDs;
    private Map<String, Integer> secretsScored;

    // Additional properties
    private String flexibleDisplayName;
    private Set<Integer> scs;

    public static WebPlayerArea fromPlayer(Player player) {
        WebPlayerArea webPlayerArea = new WebPlayerArea();

        // Basic properties
        webPlayerArea.setUserName(player.getUserName());
        webPlayerArea.setFaction(player.getFaction());
        webPlayerArea.setColor(player.getColor());
        webPlayerArea.setDisplayName(player.getDisplayName());
        webPlayerArea.setPassed(player.isPassed());
        webPlayerArea.setEliminated(player.isEliminated());

        // Command counters
        webPlayerArea.setTacticalCC(player.getTacticalCC());
        webPlayerArea.setFleetCC(player.getFleetCC());
        webPlayerArea.setStrategicCC(player.getStrategicCC());

        // Resources
        webPlayerArea.setTg(player.getTg());
        webPlayerArea.setCommodities(player.getCommodities());
        webPlayerArea.setCommoditiesTotal(player.getCommoditiesTotal());

        // Fragments
        webPlayerArea.setCrf(player.getCrf());
        webPlayerArea.setHrf(player.getHrf());
        webPlayerArea.setIrf(player.getIrf());
        webPlayerArea.setUrf(player.getUrf());

        // Units and combat
        webPlayerArea.setStasisInfantry(player.getStasisInfantry());
        webPlayerArea.setActualHits(player.getActualHits());
        webPlayerArea.setExpectedHitsTimes10(player.getExpectedHitsTimes10());
        webPlayerArea.setUnitsOwned(player.getUnitsOwned());

        // Strategy cards and promissory notes
        webPlayerArea.setFollowedSCs(player.getFollowedSCs());
        webPlayerArea.setPromissoryNotesInPlayArea(player.getPromissoryNotesInPlayArea());

        // Technologies
        webPlayerArea.setTechs(player.getTechs());
        webPlayerArea.setExhaustedTechs(player.getExhaustedTechs());
        webPlayerArea.setFactionTechs(player.getFactionTechs());
        webPlayerArea.setNotResearchedFactionTechs(player.getNotResearchedFactionTechs());

        // Planets
        webPlayerArea.setPlanets(player.getPlanets());
        webPlayerArea.setExhaustedPlanets(player.getExhaustedPlanets());
        webPlayerArea.setExhaustedPlanetAbilities(player.getExhaustedPlanetsAbilities());

        // Relics and fragments
        webPlayerArea.setFragments(player.getFragments());
        webPlayerArea.setRelics(player.getRelics());
        webPlayerArea.setExhaustedRelics(player.getExhaustedRelics());

        // Leaders and secrets
        webPlayerArea.setLeaders(player.getLeaders());
        webPlayerArea.setLeaderIDs(player.getLeaderIDs());
        webPlayerArea.setSecretsScored(player.getSecretsScored());

        // Additional properties
        webPlayerArea.setFlexibleDisplayName(player.getFlexibleDisplayName());
        webPlayerArea.setScs(player.getSCs());

        return webPlayerArea;
    }
}