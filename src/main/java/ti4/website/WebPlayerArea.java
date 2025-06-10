package ti4.website;

import java.util.*;

import lombok.Data;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.*;

@Data
public class WebPlayerArea {
    @Data
    public static class UnitCountInfo {
        private final int unitCap;
        private final int deployedCount;
    }

    // Basic properties
    private String userName;
    private String faction;
    private String color;
    private String displayName;
    private boolean passed;
    private boolean eliminated;
    private boolean active;

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
    private List<Integer> unfollowedSCs;
    private List<Integer> exhaustedSCs;
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
    private Boolean isSpeaker;
    private List<String> neighbors;

    // army values
    private float spaceArmyRes;
    private float groundArmyRes;
    private float spaceArmyHealth;
    private float groundArmyHealth;
    private float spaceArmyCombat;
    private float groundArmyCombat;

    // card counts
    private Integer soCount;
    private Integer acCount;
    private Integer pnCount;

    // victory points
    private Integer totalVps;

    // Unit information map
    private Map<String, UnitCountInfo> unitCounts;

    // Nombox units by faction
    private Map<String, List<String>> nombox;


    public static WebPlayerArea fromPlayer(Player player, Game game) {
        WebPlayerArea webPlayerArea = new WebPlayerArea();

        // Basic properties
        webPlayerArea.setUserName(player.getUserName());
        webPlayerArea.setFaction(player.getFaction());
        webPlayerArea.setColor(player.getColor());
        webPlayerArea.setDisplayName(player.getDisplayName());
        webPlayerArea.setPassed(player.isPassed());
        webPlayerArea.setEliminated(player.isEliminated());
        webPlayerArea.setActive(player.isActivePlayer());

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
        webPlayerArea.setUnfollowedSCs(player.getUnfollowedSCs());
        webPlayerArea.setExhaustedSCs(player.getExhaustedSCs());
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
        webPlayerArea.setIsSpeaker(player.isSpeaker());
        webPlayerArea.setNeighbors(player.getNeighbouringPlayers(false).stream().map(Player::getColor).toList());

        // Army values
        webPlayerArea.setSpaceArmyRes(player.getTotalResourceValueOfUnits("space"));
        webPlayerArea.setGroundArmyRes(player.getTotalResourceValueOfUnits("ground"));
        webPlayerArea.setSpaceArmyHealth(player.getTotalHPValueOfUnits("space"));
        webPlayerArea.setGroundArmyHealth(player.getTotalHPValueOfUnits("ground"));
        webPlayerArea.setSpaceArmyCombat(player.getTotalCombatValueOfUnits("space"));
        webPlayerArea.setGroundArmyCombat(player.getTotalCombatValueOfUnits("ground"));

        // card counts
        webPlayerArea.setSoCount(player.getSo());
        webPlayerArea.setAcCount(player.getAc());
        webPlayerArea.setPnCount(player.getPnCount());

        // victory points
        webPlayerArea.setTotalVps(player.getTotalVictoryPoints());

        // get reinforcement count
        Map<Units.UnitKey, Integer> unitMapCount = new HashMap<>();
        Map<String, Tile> tileMap = game.getTileMap();
        for (Tile tile : tileMap.values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                fillUnits(unitMapCount, unitHolder);
            }
        }

        // Add nombox units to the count (excluding them from reinforcements)
        for (Player gamePlayer : game.getPlayers().values()) {
            UnitHolder nombox = gamePlayer.getNomboxTile().getSpaceUnitHolder();
            if (nombox != null) {
                fillUnits(unitMapCount, nombox);
            }
        }

        // Get counts of units on map to show how many are in reinforcements
        Map<String, UnitCountInfo> unitInfoMap = new HashMap<>();
        String playerColor = player.getColor();
        for (String unitID : Mapper.getUnitIDList()) {
            Units.UnitKey unitKey = Mapper.getUnitKey(unitID, playerColor);
            int unitCap = player.getUnitCap(unitID);
            int count = unitMapCount.getOrDefault(unitKey, 0);
            UnitCountInfo unitCountInfo = new UnitCountInfo(unitCap, count);
            unitInfoMap.put(unitID, unitCountInfo);
        }

        webPlayerArea.setUnitCounts(unitInfoMap);

        // Populate nombox data for all players
        Map<String, List<String>> nomboxData = new HashMap<>();
        for (Player gamePlayer : game.getPlayers().values()) {
            UnitHolder nombox = gamePlayer.getNomboxTile().getSpaceUnitHolder();
            if (nombox != null && !nombox.getUnitKeys().isEmpty()) {
                List<String> unitList = new ArrayList<>();

                // Group units by type to get counts
                Map<String, Integer> unitCounts = new HashMap<>();
                for (Units.UnitKey unitKey : nombox.getUnitKeys()) {
                    String unitId = unitKey.asyncID();
                    int count = nombox.getUnitCount(unitKey);
                    unitCounts.put(unitId, unitCounts.getOrDefault(unitId, 0) + count);
                }

                // Create "unitId,count" strings
                for (Map.Entry<String, Integer> entry : unitCounts.entrySet()) {
                    unitList.add(entry.getKey() + "," + entry.getValue());
                }

                nomboxData.put(gamePlayer.getFaction(), unitList);
            }
        }
        webPlayerArea.setNombox(nomboxData);
        return webPlayerArea;
    }

    private static void fillUnits(Map<Units.UnitKey, Integer> unitCount, UnitHolder unitHolder) {
        for (Units.UnitKey uk : unitHolder.getUnitKeys()) {
            if (uk.getUnitType() == Units.UnitType.Infantry || uk.getUnitType() == Units.UnitType.Fighter) {
                unitCount.put(uk, unitCount.getOrDefault(uk, 0) + 1);
                continue;
            }

            int count = unitCount.getOrDefault(uk, 0);
            count += unitHolder.getUnitCount(uk);
            unitCount.put(uk, count);
        }
    }

}