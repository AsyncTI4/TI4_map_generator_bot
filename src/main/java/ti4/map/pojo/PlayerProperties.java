package ti4.map.pojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import lombok.Data;

@Data
public class PlayerProperties {
    // Basic information
    private String userID;
    private String userName;
    private String faction;
    private String color;
    private String playerStatsAnchorPosition;
    private String homeSystemPosition;
    private String autoCompleteRepresentation;

    // Personalization
    private String allianceMembers = "";
    private String factionEmoji;
    private String displayName;
    private String decalSet;
    private String notes = "";
    private String fogFilter;

    // Channels & IDs
    private @Nullable String roleIDForCommunity;
    private @Nullable String privateChannelID;
    private @Nullable String cardsInfoThreadID;
    private @Nullable String bagInfoThreadID;

    // State and settings
    private boolean passed;
    private boolean readyToPassBag;
    private boolean searchWarrant;
    private boolean dummy;
    private boolean autoPassOnWhensAfters;
    private boolean eliminated;
    private boolean fogInitialized;

    // Player area stuff
    private int tacticalCC = 3;
    private int fleetCC = 3;
    private int strategicCC = 2;
    private int inRoundTurnCount;
    private int tg;
    private int commodities;
    private int commoditiesTotal;
    // private int commoditiesBonus; // TODO
    // private int commoditiesBase;
    private int crf;
    private int hrf;
    private int irf;
    private int urf;
    private int stasisInfantry;
    private int autoSaboPassMedian;
    private int actualHits;
    private int expectedHitsTimes10;
    private int totalExpenses;

    // Statistics
    private int numberOfTurns;
    private long totalTurnTime;

    // BENTOR CONGLOMERATE ABILITY "Ancient Blueprints"
    private boolean hasFoundCulFrag;
    private boolean hasFoundHazFrag;
    private boolean hasFoundIndFrag;
    private boolean hasFoundUnkFrag;

    // LANEFIR TECH "ATS Armaments"
    private int atsCount;

    //Stat tracking
    private int sarweenCounter;
    private int pillageCounter;

    //Omega Phase
    private int priorityPosition;

    //uydai tracking
    private int pathTokenCounter;
    private int harvestCounter;

    // OLRADIN POLICY ONCE PER ACTION EXHAUST PLANET ABILITIES
    private boolean hasUsedEconomyEmpowerAbility;
    private boolean hasUsedEconomyExploitAbility;
    private boolean hasUsedEnvironmentPreserveAbility;
    private boolean hasUsedEnvironmentPlunderAbility;
    private boolean hasUsedPeopleConnectAbility;

    private Set<String> abilities = new HashSet<>();
    private Set<String> exhaustedAbilities = new HashSet<>();
    private Set<String> promissoryNotesOwned = new HashSet<>();
    private Set<String> unitsOwned = new HashSet<>();
    private Set<Integer> followedSCs = new HashSet<>();
    private Set<Integer> SCs = new LinkedHashSet<>();
    private List<String> transactionItems = new ArrayList<>();
    private List<String> promissoryNotesInPlayArea = new ArrayList<>();
    private List<String> techs = new ArrayList<>();
    private List<String> spentThingsThisWindow = new ArrayList<>();
    private List<String> bombardUnits = new ArrayList<>();
    private List<String> teamMateIDs = new ArrayList<>();
    private List<String> exhaustedTechs = new ArrayList<>();
    private List<String> planets = new ArrayList<>();
    private List<String> exhaustedPlanets = new ArrayList<>();
    private List<String> exhaustedPlanetsAbilities = new ArrayList<>();
    private List<String> fragments = new ArrayList<>();
    private List<String> relics = new ArrayList<>();
    private List<String> exhaustedRelics = new ArrayList<>();
    private List<String> mahactCC = new ArrayList<>();
    private List<String> factionTechs = new ArrayList<>();
    private List<String> purgedTechs = new ArrayList<>();
}
