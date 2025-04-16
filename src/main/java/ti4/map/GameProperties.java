package ti4.map;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import ti4.helpers.Constants;

@Getter
@Setter
public class GameProperties {
    // Game metadata
    private @ExportableField String ownerID;
    private @ExportableField String ownerName = "";
    private @ExportableField String creationDate;
    private @ExportableField String name; //pbdXXXX
    private @ExportableField String customName = "";
    private @ExportableField String mapTemplateID;
    private @ExportableField String phaseOfGame = "";
    private @ExportableField int maxSOCountPerPlayer = 3;
    private @ExportableField int playerCountForMap = 6;
    private @ExportableField int strategyCardsPerPlayer = 1;
    private @ExportableField int round = 1;
    private @ExportableField int vp = 10;
    private @ExportableField long startedDate;
    private @ExportableField long lastModifiedDate;
    private @ExportableField long endedDate;
    private @ExportableField boolean hasEnded;

    // Deck IDs
    private @ExportableField String acDeckID = "action_cards_pok";
    private @ExportableField String soDeckID = "secret_objectives_pok";
    private @ExportableField String stage1PublicDeckID = "public_stage_1_objectives_pok";
    private @ExportableField String stage2PublicDeckID = "public_stage_2_objectives_pok";
    private @ExportableField String relicDeckID = "relics_pok";
    private @ExportableField String agendaDeckID = "agendas_pok";
    private @ExportableField String explorationDeckID = "explores_pok";
    private @ExportableField String technologyDeckID = "techs_pok";
    private @ExportableField String scSetID = "pok";
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
    private boolean showOwnedPNsInPlayerArea = false;
    private String hexBorderStyle = "off";
    private String textSize = "medium";
    private String outputVerbosity = Constants.VERBOSITY_VERBOSE;
    private int autoPingSpacer;
    private List<String> tags = new ArrayList<>();

    // Game modes / homebrew flags
    private @ExportableField boolean baseGameMode; // TODO: Make this obsolete
    private @ExportableField boolean prophecyOfKings = true;
    private @ExportableField boolean ageOfExplorationMode;
    private @ExportableField boolean minorFactionsMode;
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
    private String activePlayerID;
    private String launchPostThreadID;
    private @ExportableField String botMapUpdatesThreadID;
    private @ExportableField String tableTalkChannelID;
    private @ExportableField String mainChannelID;
    private String savedChannelID;
    private @ExportableField List<String> fogOfWarGMIDs = new ArrayList<>(1); // Game Masters

    // More complex objects below
    private @ExportableField String mapString;

    // Decks
    private List<String> secretObjectives;
    private List<String> actionCards;
    private List<String> agendas;
    private List<String> events; // ignis_aurora

    // Misc Helpers
    public String getID() {
        return getName();
    }
}
