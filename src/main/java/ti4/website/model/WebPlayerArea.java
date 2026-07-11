package ti4.website.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.image.DrawingUtil;
import ti4.image.Mapper;

@Data
public class WebPlayerArea {
    public enum FactionImageType {
        DISCORD, // Discord custom emoji URL
        EMOJI, // Unicode emoji character for NotoEmoji font
        IMAGE // Local image converted to WebP URL
    }

    private record UnitCountInfo(int unitCap, int deployedCount) {}

    /**
     * @param tradeGoodsStored TODO: MemePhilosopher uncomment this private final boolean active;
     */
    public record BreakthroughInfo(String breakthroughId, boolean unlocked, boolean exhausted, int tradeGoodsStored) {}

    public record PlotCardInfo(String plotAlias, Integer identifier, List<String> factions) {}

    // Basic properties
    private String userName;
    private String faction;
    private String factionImage;
    private FactionImageType factionImageType;
    private String color;
    private String colorDisplayName;
    private String displayName;
    private String discordId;
    private String cardsInfoThreadLink;
    private boolean passed;
    private boolean eliminated;
    private boolean active;
    private boolean hasZeroToken;

    // Command counters
    private int tacticalCC;
    private int fleetCC;
    private int strategicCC;
    private int ccReinf;

    // Resources
    private int tg;
    private int commodities;
    private int commoditiesTotal;

    // Resource and influence totals
    private int resources;
    private int influence;
    private int totResources;
    private int totInfluence;

    // Optimal resource and influence calculations
    private int optimalResources;
    private int optimalInfluence;
    private int flexValue;

    // Total optimal resource and influence calculations (all planets)
    private int totOptimalResources;
    private int totOptimalInfluence;
    private int totFlexValue;

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
    private List<String> customPromissoryNotes;

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
    private Map<String, Integer> knownUnscoredSecrets;
    private Integer numUnscoredSecrets;

    // Additional properties
    private String flexibleDisplayName;
    private Set<Integer> scs;
    private Boolean isSpeaker;
    private Boolean isTyrant;
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

    // secret objectives
    private Integer numScoreableSecrets;

    // Unit information map
    private Map<String, UnitCountInfo> unitCounts;

    // Nombox units by faction )mostly cabal)
    private Map<String, List<String>> nombox;

    // Special token reinforcements
    private Integer sleeperTokensReinf;
    private List<String> ghostWormholesReinf;
    private Integer breachTokensReinf;
    private Integer galvanizeTokensReinf;

    // Mahact faction-specific: edict "stolen" fleet supply
    private List<String> mahactEdict;

    // Debt tokens: debt that this player is OWED by other players (faction/color -> count)
    private Map<String, Integer> debtTokens;

    // Breakthrough (Thunder's Edge)
    // TODO: MemePhilosopher make this a list
    private BreakthroughInfo breakthrough;

    // Plot cards (Firmament/Obsidian)
    private List<PlotCardInfo> plotCards;

    // Faction abilities
    private List<String> abilities;

    // Decal ID
    private String decalId;

    // Nekro Z Assimilator targets (Thunder's Edge) - factions whose flagships have been assimilated
    private List<String> valefarZTargets;

    public static WebPlayerArea fromPlayer(Player player, Game game) {
        WebPlayerArea webPlayerArea = new WebPlayerArea();

        // Basic properties
        webPlayerArea.userName = player.getUserName();
        webPlayerArea.faction = player.getFaction();

        // Set faction image and type
        FactionImageResult factionImageResult = getFactionImagePathAndType(player);
        webPlayerArea.factionImage = factionImageResult.path;
        webPlayerArea.factionImageType = factionImageResult.type;

        webPlayerArea.color = player.getColor();
        webPlayerArea.colorDisplayName = player.getColorDisplayName();
        webPlayerArea.displayName = player.getDisplayName();
        webPlayerArea.discordId = player.getUserID();
        webPlayerArea.cardsInfoThreadLink = player.getCardsInfoThreadJumpLink();
        webPlayerArea.passed = player.isPassed();
        webPlayerArea.eliminated = player.isEliminated();
        webPlayerArea.active = player.isActivePlayer();
        webPlayerArea.hasZeroToken = player.hasTheZeroToken();

        // Command counters
        webPlayerArea.tacticalCC = player.getTacticalCC();
        webPlayerArea.fleetCC = player.getFleetCC();
        webPlayerArea.strategicCC = player.getStrategicCC();

        // Calculate CC reinforcements
        int ccReinf = calculateCCReinforcements(player, game);
        webPlayerArea.ccReinf = ccReinf;

        // Resources
        webPlayerArea.tg = player.getTg();
        webPlayerArea.commodities = player.getCommodities();
        webPlayerArea.commoditiesTotal = player.getCommoditiesTotal();

        // Resource and influence totals
        Integer resources = Helper.getPlayerResourcesAvailable(player, game);
        Integer influence = Helper.getPlayerInfluenceAvailable(player, game);
        Integer totResources = Helper.getPlayerResourcesTotal(player, game);
        Integer totInfluence = Helper.getPlayerInfluenceTotal(player, game);

        webPlayerArea.resources = resources != null ? resources : 0;
        webPlayerArea.influence = influence != null ? influence : 0;
        webPlayerArea.totResources = totResources != null ? totResources : 0;
        webPlayerArea.totInfluence = totInfluence != null ? totInfluence : 0;

        // Optimal resource and influence calculations
        Integer optimalResources = Helper.getPlayerOptimalResourcesAvailable(player, game);
        Integer optimalInfluence = Helper.getPlayerOptimalInfluenceAvailable(player, game);
        Integer flexValue = Helper.getPlayerFlexResourcesInfluenceAvailable(player, game);

        webPlayerArea.optimalResources = optimalResources != null ? optimalResources : 0;
        webPlayerArea.optimalInfluence = optimalInfluence != null ? optimalInfluence : 0;
        webPlayerArea.flexValue = flexValue != null ? flexValue : 0;

        // Total optimal resource and influence calculations (all planets)
        Integer totOptimalResources = Helper.getPlayerOptimalResourcesTotal(player, game);
        Integer totOptimalInfluence = Helper.getPlayerOptimalInfluenceTotal(player, game);
        Integer totFlexValue = Helper.getPlayerFlexResourcesInfluenceTotal(player, game);

        webPlayerArea.totOptimalResources = totOptimalResources != null ? totOptimalResources : 0;
        webPlayerArea.totOptimalInfluence = totOptimalInfluence != null ? totOptimalInfluence : 0;
        webPlayerArea.totFlexValue = totFlexValue != null ? totFlexValue : 0;

        // Fragments
        webPlayerArea.crf = player.getCrf();
        webPlayerArea.hrf = player.getHrf();
        webPlayerArea.irf = player.getIrf();
        webPlayerArea.urf = player.getUrf();

        // Units and combat
        webPlayerArea.stasisInfantry = player.getStasisInfantry();
        webPlayerArea.actualHits = player.getActualHits();
        webPlayerArea.expectedHitsTimes10 = player.getExpectedHitsTimes10();
        webPlayerArea.unitsOwned = player.getUnitsOwned();

        // Strategy cards and promissory notes
        webPlayerArea.followedSCs = player.getFollowedSCs();
        webPlayerArea.unfollowedSCs = player.getUnfollowedSCs();
        webPlayerArea.exhaustedSCs = player.getExhaustedSCs();
        webPlayerArea.promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();

        // Custom promissory notes (faction-specific only, excluding generic ones)
        List<String> customPromissoryNotes = new ArrayList<>();
        for (String pnID : player.getPromissoryNotesOwned()) {
            var pnModel = Mapper.getPromissoryNote(pnID);
            if (pnModel != null && pnModel.getFaction().isPresent()) {
                customPromissoryNotes.add(pnID);
            }
        }
        webPlayerArea.customPromissoryNotes = customPromissoryNotes;

        // Technologies
        webPlayerArea.techs = player.getTechs();
        webPlayerArea.exhaustedTechs = player.getExhaustedTechs();
        webPlayerArea.factionTechs = player.getFactionTechs();
        webPlayerArea.notResearchedFactionTechs = player.getNotResearchedFactionTechs();

        // Planets
        webPlayerArea.planets = player.getPlanets();
        webPlayerArea.exhaustedPlanets = new ArrayList<>(player.getExhaustedPlanets());
        webPlayerArea.exhaustedPlanetAbilities = player.getExhaustedPlanetsAbilities();

        // Relics and fragments
        webPlayerArea.fragments = player.getFragments();
        webPlayerArea.relics = player.getRelics();
        webPlayerArea.exhaustedRelics = player.getExhaustedRelics();

        // Leaders and secrets
        webPlayerArea.leaders = player.getLeaders();
        webPlayerArea.leaderIDs = player.getLeaderIDs();
        webPlayerArea.secretsScored = player.getSecretsScored();

        Map<String, Integer> unscoredSecrets = player.getSecretsUnscored();
        // Known unscored secrets (populated if search warrant is in play)
        if (player.isSearchWarrant()) {
            webPlayerArea.knownUnscoredSecrets = unscoredSecrets;
        } else {
            webPlayerArea.knownUnscoredSecrets = new HashMap<>();
        }

        webPlayerArea.numUnscoredSecrets = unscoredSecrets.size();

        // Additional properties
        webPlayerArea.flexibleDisplayName = player.getFlexibleDisplayName();
        webPlayerArea.scs = player.getSCs();
        webPlayerArea.isSpeaker = player.isSpeaker();
        webPlayerArea.isTyrant = player.isTyrant();
        webPlayerArea.neighbors = player.getNeighbouringPlayers(false).stream()
                .map(Player::getColor)
                .toList();

        // Army values
        webPlayerArea.spaceArmyRes = player.getTotalResourceValueOfUnits("space");
        webPlayerArea.groundArmyRes = player.getTotalResourceValueOfUnits("ground");
        webPlayerArea.spaceArmyHealth = player.getTotalHPValueOfUnits("space");
        webPlayerArea.groundArmyHealth = player.getTotalHPValueOfUnits("ground");
        webPlayerArea.spaceArmyCombat = player.getTotalCombatValueOfUnits("space");
        webPlayerArea.groundArmyCombat = player.getTotalCombatValueOfUnits("ground");

        // card counts
        webPlayerArea.soCount = player.getSo();
        webPlayerArea.acCount = player.getAcCount();
        webPlayerArea.pnCount = player.getPnCount();

        // victory points
        webPlayerArea.totalVps = player.getTotalVictoryPoints();

        // secret objectives
        webPlayerArea.numScoreableSecrets = player.getMaxSOCount();

        // Faction abilities
        webPlayerArea.abilities = new ArrayList<>(player.getAbilities());

        // Decal ID
        webPlayerArea.decalId = player.getDecalSet();

        // Nekro Z Assimilator targets (Thunder's Edge)
        // Parse valefarZ stored value which contains factions whose flagships have been assimilated
        List<String> valefarZTargets = new ArrayList<>();
        if (player.hasUnlockedBreakthrough("nekrobt")) {
            String valefarZValue = game.getStoredValue("valefarZ");
            if (!valefarZValue.isEmpty()) {
                for (String faction : valefarZValue.split("\\|")) {
                    if (!faction.isEmpty()) {
                        valefarZTargets.add(faction);
                    }
                }
            }
        }
        webPlayerArea.valefarZTargets = valefarZTargets;

        // Breakthrough info (Thunder's Edge)
        if (game.isThundersEdge()) {
            List<BreakthroughInfo> breakthroughs = new ArrayList<>();
            for (String btID : player.getBreakthroughIDs()) {
                boolean unl = player.isBreakthroughUnlocked(btID);
                boolean exh = player.isBreakthroughExhausted(btID);
                boolean act = player.isBreakthroughActive(btID);
                int tgs = player.getBreakthroughTGs(btID);

                // TODO: MemePhilosopher replace this ...
                breakthroughs.add(new BreakthroughInfo(btID, unl, exh, tgs));
                // ... with this
                // breakthroughs.add(new BreakthroughInfo(btID, unl, exh, act, tgs));
            }

            // TODO: MemePhilosopher make this a list
            if (!breakthroughs.isEmpty()) {
                webPlayerArea.breakthrough = breakthroughs.getFirst();
            } else {
                webPlayerArea.breakthrough = null;
            }
        } else {
            webPlayerArea.breakthrough = null;
        }

        // Plot cards (Firmament/Obsidian)
        List<PlotCardInfo> plotCardsList = new ArrayList<>();
        if (player.hasAbility("bladesorchestra") || player.hasAbility("plotsplots")) {
            // Only reveal plot names when player has become Obsidian (bladesorchestra ability)
            // Firmament players (plotsplots only) should not have plot names revealed
            boolean isObsidian = player.hasAbility("bladesorchestra");
            for (Map.Entry<String, Integer> plotEntry : player.getPlotCards().entrySet()) {
                String plotAlias = plotEntry.getKey();
                Integer identifier = plotEntry.getValue();
                List<String> factions = player.getPlotCardsFactions().getOrDefault(plotAlias, new ArrayList<>());
                // Set plotAlias to null if player hasn't become Obsidian yet
                String plotAliasToUse = isObsidian ? plotAlias : null;
                plotCardsList.add(new PlotCardInfo(plotAliasToUse, identifier, factions));
            }
        }
        webPlayerArea.plotCards = plotCardsList;

        // Special token reinforcements
        // Sleeper tokens (Titans faction only)
        if (player.hasAbility("awaken") && !game.isTwilightsFallMode()) {
            webPlayerArea.sleeperTokensReinf = 5 - game.getSleeperTokensPlacedCount();
        } else {
            webPlayerArea.sleeperTokensReinf = 0;
        }

        // Ghost wormhole tokens (Ghost faction only)
        if ("ghost".equalsIgnoreCase(player.getFaction())) {
            List<String> ghostWormholesInReinf = new ArrayList<>();

            // Check which wormholes are on the map
            boolean alphaOnMap = false;
            boolean betaOnMap = false;
            boolean gammaOnMap = false;

            String alphaID = Mapper.getTokenID("creussalpha");
            String betaID = Mapper.getTokenID("creussbeta");
            String gammaID = Mapper.getTokenID("creussgamma");

            for (Tile tile : game.getTiles()) {
                Set<String> tileTokens = tile.getSpaceUnitHolder().getTokenList();
                alphaOnMap |= tileTokens.contains(alphaID);
                betaOnMap |= tileTokens.contains(betaID);
                gammaOnMap |= tileTokens.contains(gammaID);
            }

            // Add wormholes that are NOT on the map (i.e., in reinforcements)
            if (!alphaOnMap) ghostWormholesInReinf.add("creussalpha");
            if (!betaOnMap) ghostWormholesInReinf.add("creussbeta");
            if (!gammaOnMap) ghostWormholesInReinf.add("creussgamma");

            webPlayerArea.ghostWormholesReinf = ghostWormholesInReinf;
        } else {
            webPlayerArea.ghostWormholesReinf = new ArrayList<>();
        }

        // Breach tokens (Crimson Rebellion faction only)
        if (player.hasAbility("incursion")) {
            int maxBreachTokens = 7;
            int totalBreaches = (int) game.getTiles().stream()
                    .flatMap(t -> t.getUnitHolderValues().stream())
                    .flatMap(uh -> uh.getTokenList().stream())
                    .filter(tok ->
                            Constants.TOKEN_BREACH_ACTIVE.equals(tok) || Constants.TOKEN_BREACH_INACTIVE.equals(tok))
                    .count();
            webPlayerArea.breachTokensReinf = Math.max(0, maxBreachTokens - totalBreaches);
        } else {
            webPlayerArea.breachTokensReinf = 0;
        }

        // Galvanize tokens (Bastion faction only)
        if (player.hasAbility("galvanize")) {
            int maxGalvanizeTokens = 7;
            int totGalvanized = game.getTiles().stream()
                    .flatMap(t -> t.getUnitHolderValues().stream())
                    .mapToInt(UnitHolder::getTotalGalvanizedCount)
                    .sum();
            webPlayerArea.galvanizeTokensReinf = Math.max(0, maxGalvanizeTokens - totGalvanized);
        } else {
            webPlayerArea.galvanizeTokensReinf = 0;
        }

        // get reinforcement count
        Map<Units.UnitKey, Integer> unitMapCount = new HashMap<>();
        Map<String, Tile> tileMap = game.getTileMap();
        for (Tile tile : tileMap.values()) {
            for (UnitHolder unitHolder : tile.getUnitHolderValues()) {
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

        webPlayerArea.unitCounts = unitInfoMap;

        // Populate nombox data for players with captured units
        Map<String, List<String>> nomboxData = new HashMap<>();
        UnitHolder nombox = player.getNomboxTile().getSpaceUnitHolder();
        if (nombox != null && !nombox.getUnitKeys().isEmpty()) {
            // Group units by their actual faction (captured units)
            Map<String, Map<String, Integer>> unitsByFaction = new HashMap<>();
            for (Units.UnitKey unitKey : nombox.getUnitKeys()) {
                String unitId = unitKey.asyncID();
                // Get the actual player/faction that owns this captured unit
                Player unitOwner = game.getPlayerByColorID(unitKey.colorID()).orElse(null);
                if (unitOwner != null) {
                    String unitFaction = unitOwner.getFaction();
                    int count = nombox.getUnitCount(unitKey);

                    unitsByFaction.computeIfAbsent(unitFaction, _ -> new HashMap<>());
                    Map<String, Integer> unitCounts = unitsByFaction.get(unitFaction);
                    unitCounts.put(unitId, unitCounts.getOrDefault(unitId, 0) + count);
                }
            }

            // Create "unitId,count" strings for each faction
            for (Map.Entry<String, Map<String, Integer>> factionEntry : unitsByFaction.entrySet()) {
                String faction = factionEntry.getKey();
                List<String> unitList = new ArrayList<>();
                for (Map.Entry<String, Integer> unitEntry :
                        factionEntry.getValue().entrySet()) {
                    unitList.add(unitEntry.getKey() + "," + unitEntry.getValue());
                }
                nomboxData.put(faction, unitList);
            }
        }
        webPlayerArea.nombox = nomboxData;

        if (player.hasAbility("edict") || player.hasAbility("edict_y")) {
            webPlayerArea.mahactEdict = player.getMahactCC();
        } else {
            webPlayerArea.mahactEdict = new ArrayList<>();
        }

        // Debt tokens: debt that this player is OWED by other players (only include entries with count > 0)
        Map<String, Integer> debtTokens = new HashMap<>();
        for (Map.Entry<String, Integer> debtEntry : player.getDebtTokens().entrySet()) {
            if (debtEntry.getValue() > 0) {
                debtTokens.put(debtEntry.getKey(), debtEntry.getValue());
            }
        }
        webPlayerArea.debtTokens = debtTokens;

        return webPlayerArea;
    }

    private static void fillUnits(Map<Units.UnitKey, Integer> unitCount, UnitHolder unitHolder) {
        for (Units.UnitKey uk : unitHolder.getUnitKeys()) {
            if (uk.unitType() == Units.UnitType.Infantry || uk.unitType() == Units.UnitType.Fighter) {
                unitCount.put(uk, unitCount.getOrDefault(uk, 0) + 1);
                continue;
            }

            int count = unitCount.getOrDefault(uk, 0);
            count += unitHolder.getUnitCount(uk);
            unitCount.put(uk, count);
        }
    }

    private static int calculateCCReinforcements(Player player, Game game) {
        String playerColor = player.getColor();
        if (playerColor == null) {
            return 0;
        }
        // Default CC limit is 16 in TI4
        int ccLimit = 16;
        if (!game.getStoredValue("ccLimit").isEmpty()) {
            ccLimit = Integer.parseInt(game.getStoredValue("ccLimit"));
        }
        if (!game.getStoredValue("ccLimit" + playerColor).isEmpty()) {
            ccLimit = Integer.parseInt(game.getStoredValue("ccLimit" + playerColor));
        }
        int ccCount = Helper.getCCCount(game, playerColor);
        int remainingReinforcements = ccLimit - ccCount;
        return Math.max(0, remainingReinforcements);
    }

    /**
     * Result class for faction image path and type
     */
    private record FactionImageResult(String path, FactionImageType type) {}

    /**
     * Gets the appropriate faction image path and type for web interface.
     * Reuses logic from DrawingUtil.getPlayerFactionIconImageScaled to maintain consistency.
     */
    private static FactionImageResult getFactionImagePathAndType(Player player) {
        if (player == null) {
            return new FactionImageResult(null, FactionImageType.IMAGE);
        }

        // Reuse the same logic as DrawingUtil.getPlayerFactionIconImageScaled
        Emoji factionEmoji = Emoji.fromFormatted(player.getFactionEmoji());

        // 1) Discord custom emoji - return Discord URL and DISCORD type
        if (player.hasCustomFactionEmoji() && factionEmoji instanceof CustomEmoji factionCustomEmoji) {
            return new FactionImageResult(factionCustomEmoji.getImageUrl(), FactionImageType.DISCORD);
        }
        // 2) Unicode emoji - return the formatted emoji string and EMOJI type
        else if (player.hasCustomFactionEmoji() && factionEmoji instanceof UnicodeEmoji uni) {
            return new FactionImageResult(uni.getFormatted(), FactionImageType.EMOJI);
        }

        // 3) Local faction image - use DrawingUtil.getFactionIconPath directly
        String factionFile = DrawingUtil.getFactionIconPath(player.getFaction());
        if (factionFile != null) {
            // Convert absolute path to relative path from resources
            String resourcePath = Storage.getResourcePath();
            if (factionFile.startsWith(resourcePath)) {
                String relativePath = factionFile.substring(resourcePath.length());
                // Ensure path starts with /
                relativePath = relativePath.startsWith("/") ? relativePath : "/" + relativePath;

                // Convert PNG to WebP and prepend web URL
                if (relativePath.endsWith(".png")) {
                    relativePath = relativePath.replace(".png", ".webp");
                }

                return new FactionImageResult("https://images.asyncti4.com" + relativePath, FactionImageType.IMAGE);
            } else {
                // If path doesn't start with resource path, return as-is (shouldn't happen)
                return new FactionImageResult(factionFile, FactionImageType.IMAGE);
            }
        }

        return new FactionImageResult(null, FactionImageType.IMAGE);
    }
}
