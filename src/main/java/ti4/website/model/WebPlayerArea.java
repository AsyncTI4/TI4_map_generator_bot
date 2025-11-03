package ti4.website.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.image.DrawingUtil;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@Data
public class WebPlayerArea {
    public enum FactionImageType {
        DISCORD, // Discord custom emoji URL
        EMOJI, // Unicode emoji character for NotoEmoji font
        IMAGE // Local image converted to WebP URL
    }

    @Data
    private static class UnitCountInfo {
        private final int unitCap;
        private final int deployedCount;
    }

    @Data
    public static class BreakthroughInfo {
        private final String breakthroughId;
        private final boolean unlocked;
        private final boolean exhausted;
        private final int tradeGoodsStored;
    }

    @Data
    public static class PlotCardInfo {
        private final String plotAlias;
        private final Integer identifier;
        private final List<String> factions;
    }

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
    private BreakthroughInfo breakthrough;

    // Plot cards (Firmament/Obsidian)
    private List<PlotCardInfo> plotCards;

    // Faction abilities
    private List<String> abilities;

    public static WebPlayerArea fromPlayer(Player player, Game game) {
        WebPlayerArea webPlayerArea = new WebPlayerArea();

        // Basic properties
        webPlayerArea.setUserName(player.getUserName());
        webPlayerArea.setFaction(player.getFaction());

        // Set faction image and type
        FactionImageResult factionImageResult = getFactionImagePathAndType(player);
        webPlayerArea.setFactionImage(factionImageResult.path);
        webPlayerArea.setFactionImageType(factionImageResult.type);

        webPlayerArea.setColor(player.getColor());
        webPlayerArea.setColorDisplayName(player.getColorDisplayName());
        webPlayerArea.setDisplayName(player.getDisplayName());
        webPlayerArea.setDiscordId(player.getUserID());
        webPlayerArea.setCardsInfoThreadLink(player.getCardsInfoThreadJumpLink());
        webPlayerArea.setPassed(player.isPassed());
        webPlayerArea.setEliminated(player.isEliminated());
        webPlayerArea.setActive(player.isActivePlayer());
        webPlayerArea.setHasZeroToken(player.hasTheZeroToken());

        // Command counters
        webPlayerArea.setTacticalCC(player.getTacticalCC());
        webPlayerArea.setFleetCC(player.getFleetCC());
        webPlayerArea.setStrategicCC(player.getStrategicCC());

        // Calculate CC reinforcements
        int ccReinf = calculateCCReinforcements(player, game);
        webPlayerArea.setCcReinf(ccReinf);

        // Resources
        webPlayerArea.setTg(player.getTg());
        webPlayerArea.setCommodities(player.getCommodities());
        webPlayerArea.setCommoditiesTotal(player.getCommoditiesTotal());

        // Resource and influence totals
        Integer resources = Helper.getPlayerResourcesAvailable(player, game);
        Integer influence = Helper.getPlayerInfluenceAvailable(player, game);
        Integer totResources = Helper.getPlayerResourcesTotal(player, game);
        Integer totInfluence = Helper.getPlayerInfluenceTotal(player, game);

        webPlayerArea.setResources(resources != null ? resources : 0);
        webPlayerArea.setInfluence(influence != null ? influence : 0);
        webPlayerArea.setTotResources(totResources != null ? totResources : 0);
        webPlayerArea.setTotInfluence(totInfluence != null ? totInfluence : 0);

        // Optimal resource and influence calculations
        Integer optimalResources = Helper.getPlayerOptimalResourcesAvailable(player, game);
        Integer optimalInfluence = Helper.getPlayerOptimalInfluenceAvailable(player, game);
        Integer flexValue = Helper.getPlayerFlexResourcesInfluenceAvailable(player, game);

        webPlayerArea.setOptimalResources(optimalResources != null ? optimalResources : 0);
        webPlayerArea.setOptimalInfluence(optimalInfluence != null ? optimalInfluence : 0);
        webPlayerArea.setFlexValue(flexValue != null ? flexValue : 0);

        // Total optimal resource and influence calculations (all planets)
        Integer totOptimalResources = Helper.getPlayerOptimalResourcesTotal(player, game);
        Integer totOptimalInfluence = Helper.getPlayerOptimalInfluenceTotal(player, game);
        Integer totFlexValue = Helper.getPlayerFlexResourcesInfluenceTotal(player, game);

        webPlayerArea.setTotOptimalResources(totOptimalResources != null ? totOptimalResources : 0);
        webPlayerArea.setTotOptimalInfluence(totOptimalInfluence != null ? totOptimalInfluence : 0);
        webPlayerArea.setTotFlexValue(totFlexValue != null ? totFlexValue : 0);

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

        // Custom promissory notes (faction-specific only, excluding generic ones)
        List<String> customPromissoryNotes = new ArrayList<>();
        for (String pnID : player.getPromissoryNotesOwned()) {
            var pnModel = Mapper.getPromissoryNote(pnID);
            if (pnModel != null && pnModel.getFaction().isPresent()) {
                customPromissoryNotes.add(pnID);
            }
        }
        webPlayerArea.setCustomPromissoryNotes(customPromissoryNotes);

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

        // Known unscored secrets (populated if search warrant is in play)
        if (player.isSearchWarrant()) {
            webPlayerArea.setKnownUnscoredSecrets(player.getSecretsUnscored());
        } else {
            webPlayerArea.setKnownUnscoredSecrets(new HashMap<>());
        }

        webPlayerArea.setNumUnscoredSecrets(
                player.getSecretsUnscored() != null
                        ? player.getSecretsUnscored().size()
                        : 0);

        // Additional properties
        webPlayerArea.setFlexibleDisplayName(player.getFlexibleDisplayName());
        webPlayerArea.setScs(player.getSCs());
        webPlayerArea.setIsSpeaker(player.isSpeaker());
        webPlayerArea.setNeighbors(player.getNeighbouringPlayers(false).stream()
                .map(Player::getColor)
                .toList());

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

        // secret objectives
        webPlayerArea.setNumScoreableSecrets(player.getMaxSOCount());

        // Faction abilities
        webPlayerArea.setAbilities(new ArrayList<>(player.getAbilities()));

        // Breakthrough info (Thunder's Edge)
        if (game.isThundersEdge()) {
            String breakthroughId = player.getBreakthroughID();
            if (breakthroughId != null && !breakthroughId.isEmpty()) {
                webPlayerArea.setBreakthrough(new BreakthroughInfo(
                        breakthroughId,
                        player.isBreakthroughUnlocked(),
                        player.isBreakthroughExhausted(),
                        player.getBreakthroughTGs()));
            } else {
                webPlayerArea.setBreakthrough(null);
            }
        } else {
            webPlayerArea.setBreakthrough(null);
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
        webPlayerArea.setPlotCards(plotCardsList);

        // Special token reinforcements
        // Sleeper tokens (Titans faction only)
        if (player.hasAbility("awaken") && !game.isTwilightsFallMode()) {
            webPlayerArea.setSleeperTokensReinf(5 - game.getSleeperTokensPlacedCount());
        } else {
            webPlayerArea.setSleeperTokensReinf(0);
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

            for (Tile tile : game.getTileMap().values()) {
                Set<String> tileTokens = tile.getUnitHolders().get("space").getTokenList();
                alphaOnMap |= tileTokens.contains(alphaID);
                betaOnMap |= tileTokens.contains(betaID);
                gammaOnMap |= tileTokens.contains(gammaID);
            }

            // Add wormholes that are NOT on the map (i.e., in reinforcements)
            if (!alphaOnMap) ghostWormholesInReinf.add("creussalpha");
            if (!betaOnMap) ghostWormholesInReinf.add("creussbeta");
            if (!gammaOnMap) ghostWormholesInReinf.add("creussgamma");

            webPlayerArea.setGhostWormholesReinf(ghostWormholesInReinf);
        } else {
            webPlayerArea.setGhostWormholesReinf(new ArrayList<>());
        }

        // Breach tokens (Crimson Rebellion faction only)
        if (player.hasAbility("incursion")) {
            int maxBreachTokens = 7;
            int totalBreaches = (int) game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .flatMap(uh -> uh.getTokenList().stream())
                    .filter(tok ->
                            tok.equals(Constants.TOKEN_BREACH_ACTIVE) || tok.equals(Constants.TOKEN_BREACH_INACTIVE))
                    .count();
            webPlayerArea.setBreachTokensReinf(Math.max(0, maxBreachTokens - totalBreaches));
        } else {
            webPlayerArea.setBreachTokensReinf(0);
        }

        // Galvanize tokens (Bastion faction only)
        if (player.hasAbility("galvanize")) {
            int maxGalvanizeTokens = 7;
            int totGalvanized = game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .collect(Collectors.summingInt(UnitHolder::getTotalGalvanizedCount));
            webPlayerArea.setGalvanizeTokensReinf(Math.max(0, maxGalvanizeTokens - totGalvanized));
        } else {
            webPlayerArea.setGalvanizeTokensReinf(0);
        }

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

        // Populate nombox data for players with captured units
        Map<String, List<String>> nomboxData = new HashMap<>();
        UnitHolder nombox = player.getNomboxTile().getSpaceUnitHolder();
        if (nombox != null && !nombox.getUnitKeys().isEmpty()) {
            // Group units by their actual faction (captured units)
            Map<String, Map<String, Integer>> unitsByFaction = new HashMap<>();
            for (Units.UnitKey unitKey : nombox.getUnitKeys()) {
                String unitId = unitKey.asyncID();
                // Get the actual player/faction that owns this captured unit
                Player unitOwner = game.getPlayerByColorID(unitKey.getColorID()).orElse(null);
                if (unitOwner != null) {
                    String unitFaction = unitOwner.getFaction();
                    int count = nombox.getUnitCount(unitKey);

                    unitsByFaction.computeIfAbsent(unitFaction, k -> new HashMap<>());
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
        webPlayerArea.setNombox(nomboxData);

        // Mahact edict: only applies if the player is mahact and loads the mahact's "stolen" fleet supply
        if (player.hasAbility("edict")) {
            webPlayerArea.setMahactEdict(player.getMahactCC());
        } else {
            webPlayerArea.setMahactEdict(new ArrayList<>());
        }

        // Debt tokens: debt that this player is OWED by other players (only include entries with count > 0)
        Map<String, Integer> debtTokens = new HashMap<>();
        for (Map.Entry<String, Integer> debtEntry : player.getDebtTokens().entrySet()) {
            if (debtEntry.getValue() > 0) {
                debtTokens.put(debtEntry.getKey(), debtEntry.getValue());
            }
        }
        webPlayerArea.setDebtTokens(debtTokens);

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
    private static class FactionImageResult {
        final String path;
        final FactionImageType type;

        FactionImageResult(String path, FactionImageType type) {
            this.path = path;
            this.type = type;
        }
    }

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
