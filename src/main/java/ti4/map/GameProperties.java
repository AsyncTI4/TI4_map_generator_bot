package ti4.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.Constants;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;

@Getter
@Setter
public class GameProperties {
    // Game metadata
    private @ExportableField String ownerID;
    private @ExportableField String ownerName = "";

    // can be removed?
    // need to migrate old creationDate only games to creationDateTime (at midnight)
    @Deprecated
    private @ExportableField String creationDate;

    private @ExportableField long creationDateTime;

    private @ExportableField String name; // pbdXXXX
    private @ExportableField String customName = "";
    private @ExportableField String mapTemplateID;
    private @ExportableField String phaseOfGame = "";
    private @ExportableField int maxSOCountPerPlayer = 3;
    private @ExportableField int playerCountForMap = 6;
    private @ExportableField int strategyCardsPerPlayer = 1;
    private @ExportableField int round = 1;
    private @ExportableField int vp = 10;

    @Deprecated // can be removed?
    private @ExportableField long startedDate;

    private @ExportableField long lastModifiedDate;
    private @ExportableField long endedDate;
    private @ExportableField boolean hasEnded;
    private @ExportableField boolean replacementMade;

    // Deck IDs
    private @ExportableField String acDeckID = "action_cards_pok";
    private @ExportableField String soDeckID = "secret_objectives_pok";
    private @ExportableField String stage1PublicDeckID = "public_stage_1_objectives_pok";
    private @ExportableField String stage2PublicDeckID = "public_stage_2_objectives_pok";
    private @ExportableField String relicDeckID = "relics_pok_te";
    private @ExportableField String agendaDeckID = "agendas_pok";
    private @ExportableField String explorationDeckID = "explores_pok";
    private @ExportableField String technologyDeckID = "techs_pok_c4";
    private @ExportableField String scSetID = "te";
    private @ExportableField String eventDeckID = "";

    // Transient Game Data
    private String activeSystem;
    private String currentAgendaInfo = "";
    private String currentACDrawStatusInfo = "";
    private String latestCommand = "";
    private String latestOutcomeVotedFor = "";
    private String playersWhoHitPersistentNoAfter = "";
    private String playersWhoHitPersistentNoWhen = "";
    private String savedMessage;
    private boolean hasHadAStatusPhase;
    private boolean naaluAgent;
    private boolean warfareAction;
    private boolean l1Hero;
    private boolean temporaryPingDisable;
    private boolean dominusOrb;
    private boolean componentAction;
    private boolean justPlayedComponentAC;
    private boolean hasHackElectionBeenPlayed;

    // Aggregate Game Stats
    private @ExportableField int activationCount;
    private @ExportableField int buttonPressCount;
    private @ExportableField int mapImageGenerationCount;
    private @ExportableField int numberOfPurgedFragments;
    private @ExportableField int pingSystemCounter;

    // Customization Flags/Settings
    private boolean botFactionReacts;
    private boolean botColorReacts;
    private boolean botStratReacts;
    private boolean botShushing;
    private boolean ccNPlasticLimit = true;
    private boolean injectRulesLinks = true;
    private boolean newTransactionMethod = true;
    private boolean nomadCoin;
    private boolean queueSO = true;
    private boolean showBanners = true;
    private boolean showBubbles = true;
    private boolean showFullComponentTextEmbeds;
    private boolean showGears = true;
    private boolean showMapSetup;
    private boolean showUnitTags;
    private boolean stratPings = true;
    private boolean testBetaFeaturesMode;
    private boolean showOwnedPNsInPlayerArea;
    private String hexBorderStyle = "off"; // values are off/dash/solid
    private String textSize = "medium";
    private String outputVerbosity = Constants.VERBOSITY_VERBOSE;
    private int autoPingSpacer;
    private List<String> tags = new ArrayList<>();

    // Game modes / homebrew flags
    private @ExportableField boolean baseGameMode; // TODO: Make this obsolete
    private @ExportableField boolean thundersEdge;
    private @ExportableField boolean twilightsFallMode;
    private @ExportableField boolean prophecyOfKings = true;
    private @ExportableField boolean ageOfExplorationMode;
    private @ExportableField boolean facilitiesMode;
    private @ExportableField boolean minorFactionsMode;
    private @ExportableField boolean totalWarMode;
    private @ExportableField boolean dangerousWildsMode;
    private @ExportableField boolean civilizedSocietyMode;
    private @ExportableField boolean ageOfFightersMode;
    private @ExportableField boolean mercenariesForHireMode;
    private @ExportableField boolean adventOfTheWarsunMode;
    private @ExportableField boolean culturalExchangeProgramMode;
    private @ExportableField boolean conventionsOfWarAbandonedMode;
    private @ExportableField boolean rapidMobilizationMode;
    private @ExportableField boolean weirdWormholesMode;
    private @ExportableField boolean noFractureMode;
    private @ExportableField boolean callOfTheVoidMode;
    private @ExportableField boolean cosmicPhenomenaeMode;
    private @ExportableField boolean monumentToTheAgesMode;
    private @ExportableField boolean wildWildGalaxyMode;
    private @ExportableField boolean zealousOrthodoxyMode;
    private @ExportableField boolean stellarAtomicsMode;
    private @ExportableField boolean noSwapMode;
    private @ExportableField boolean veiledHeartMode;
    private @ExportableField boolean limitedWhispersMode;
    private @ExportableField boolean ageOfCommerceMode;
    private @ExportableField boolean hiddenAgendaMode;
    private @ExportableField boolean ordinianC1Mode;
    private @ExportableField boolean liberationC4Mode;
    private @ExportableField boolean allianceMode;
    private @ExportableField boolean communityMode;
    private @ExportableField boolean competitiveTIGLGame;
    private @ExportableField boolean fowMode;
    private @ExportableField boolean lightFogMode;
    private @ExportableField boolean cptiExploreMode;
    private @ExportableField boolean absolMode;
    private @ExportableField boolean discordantStarsMode;
    private @ExportableField boolean unchartedSpaceStuff;
    private @ExportableField boolean miltyModMode;
    private @ExportableField boolean promisesPromisesMode;
    private @ExportableField boolean flagshippingMode;
    private @ExportableField boolean redTapeMode;
    private @ExportableField boolean omegaPhaseMode;
    private @ExportableField boolean homebrew;
    private @ExportableField boolean homebrewSCMode;
    private @ExportableField String spinMode = "OFF";
    private @ExportableField boolean fastSCFollowMode;
    private @ExportableField boolean extraSecretMode;
    private @ExportableField boolean votcMode;
    private @ExportableField boolean reverseSpeakerOrder;

    // Discord Snowflakes
    private @ExportableField String guildID;
    private String speakerUserID = "";
    private String tyrantUserID = "";
    private String activePlayerID;
    private String launchPostThreadID;
    private @ExportableField String botMapUpdatesThreadID;
    private @ExportableField String tableTalkChannelID;
    private @ExportableField String mainChannelID;
    private String savedChannelID;

    // More complex objects below
    private @ExportableField String mapString;

    // Decks
    private List<String> secretObjectives;
    private List<String> actionCards;
    private List<String> agendas;
    private List<String> mandates;
    private List<String> events; // ignis_aurora

    private Map<String, Integer> discardActionCards = new LinkedHashMap<>();
    private Map<String, ACStatus> discardACStatus = new HashMap<>();

    // Priority Track
    private PriorityTrackMode priorityTrackMode = PriorityTrackMode.NONE;

    public boolean hasAnyPriorityTrackMode() {
        return priorityTrackMode != PriorityTrackMode.NONE;
    }

    public boolean hasFullPriorityTrackMode() {
        return priorityTrackMode == PriorityTrackMode.FULL;
    }

    // Misc Helpers
    public String getID() {
        return name;
    }
}
