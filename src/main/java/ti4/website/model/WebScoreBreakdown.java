package ti4.website.model;

import java.util.*;
import lombok.Data;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.model.PublicObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.info.ListPlayerInfoService;

@Data
public class WebScoreBreakdown {
    private List<ScoreBreakdownEntry> entries;

    @Data
    public static class ScoreBreakdownEntry {
        private EntryType type;
        private String objectiveKey; // For objectives: the ID (e.g., "po1_expand", "secret_diversify")
        private String agendaKey; // For agendas: the agenda ID
        private String description; // Free text description
        private EntryState state;
        private boolean losable;
        private Integer currentProgress; // Optional - for trackable objectives/relics
        private Integer totalProgress; // Optional - threshold to score
        private Integer pointValue; // How many VP this entry is worth

        // Special properties
        private Boolean tombInPlay; // Only for CROWN type - null for others
        private Boolean
                canDrawSecret; // Only for IMPERIAL type - indicates if the player can draw a secret instead of scoring
    }

    public enum EntryType {
        PO_1, // Stage 1 public objective (1 VP)
        PO_2, // Stage 2 public objective (2 VP)
        SECRET, // Secret objective (1 VP)
        CUSTODIAN, // Custodians token from Mecatol Rex
        IMPERIAL, // Imperial strategy card scoring
        CROWN, // Crown of Emphidia relic
        LATVINIA, // Book of Latvinia (score with 4 tech types)
        SFTT, // Support for the Throne (agenda)
        SHARD, // Shard of the Throne (relic, 1 VP when held)
        STYX, // Styx token (homebrew, 1 VP)
        AGENDA // Other agenda-based points
    }

    public enum EntryState {
        SCORED, // Already scored/active
        QUALIFIES, // Meets requirements but not yet scored
        POTENTIAL, // Could achieve in future
        UNSCORED // Drawn/held but cannot currently score
    }

    // Constants for magic values
    private static final int IMPERIAL_STRATEGY_CARD = 8;
    private static final String MECATOL_REX_PLANET = "mr";
    private static final int LATVINIA_TECH_TYPE_THRESHOLD = 4;

    // Relic ID constants
    private static final String RELIC_SHARD = "shard";
    private static final String RELIC_SHARD_ABSOL = "absol_shardofthethrone1";
    private static final String RELIC_CROWN = "emphidia";
    private static final String RELIC_CROWN_BALDRICK = "baldrick_crownofemphidia";
    private static final String RELIC_LATVINIA = "bookoflatvinia";
    private static final String RELIC_LATVINIA_BALDRICK = "baldrick_bookoflatvinia";
    private static final String RELIC_STYX = "styx";

    // Custom public objective names (used in game.getRevealedPublicObjectives())
    private static final String CUSTOM_PO_SHARD = "Shard of the Throne";
    private static final String CUSTOM_PO_SHARD_PREFIX = "Shard of the Throne (";
    private static final String CUSTOM_PO_SUPPORT_FOR_THRONE = "support_for_the_throne";

    // Hardcoded metadata for losable agendas
    private static final Set<String> LOSABLE_AGENDAS = Set.of("political_censure", "mutiny");

    // Known relic and special keys to skip when processing customPublicVP
    private static final Set<String> KNOWN_RELIC_AND_SPECIAL_KEYS = Set.of(
            Constants.CUSTODIAN,
            Constants.IMPERIAL_RIDER,
            RELIC_SHARD,
            RELIC_SHARD_ABSOL,
            CUSTOM_PO_SHARD,
            RELIC_CROWN,
            RELIC_CROWN_BALDRICK,
            RELIC_LATVINIA,
            RELIC_LATVINIA_BALDRICK,
            RELIC_STYX,
            CUSTOM_PO_SUPPORT_FOR_THRONE);

    public static WebScoreBreakdown fromPlayer(Player player, Game game) {
        if (player == null || game == null) {
            WebScoreBreakdown breakdown = new WebScoreBreakdown();
            breakdown.entries = new ArrayList<>();
            return breakdown;
        }

        WebScoreBreakdown breakdown = new WebScoreBreakdown();
        breakdown.entries = new ArrayList<>();

        addScoredEntries(player, game, breakdown.entries);
        addQualifiesAndPotentialEntries(player, game, breakdown.entries);
        addUnscoredEntries(player, game, breakdown.entries);

        return breakdown;
    }

    private static void addScoredEntries(Player player, Game game, List<ScoreBreakdownEntry> entries) {
        if (player == null || game == null || entries == null) {
            return;
        }

        // Collect scored entries first, then add them in the correct order
        List<ScoreBreakdownEntry> po1Entries = new ArrayList<>();
        List<ScoreBreakdownEntry> po2Entries = new ArrayList<>();
        List<ScoreBreakdownEntry> secretEntries = new ArrayList<>();
        List<ScoreBreakdownEntry> custodianEntries = new ArrayList<>();
        List<ScoreBreakdownEntry> imperialEntries = new ArrayList<>();
        List<ScoreBreakdownEntry> relicEntries = new ArrayList<>();
        List<ScoreBreakdownEntry> agendaEntries = new ArrayList<>();

        // Process scored public objectives (PO1, PO2, Custodian, Imperial)
        Map<String, List<String>> scoredPublics = game.getScoredPublicObjectives();
        if (scoredPublics != null) {
            for (Map.Entry<String, List<String>> poEntry : scoredPublics.entrySet()) {
                String poKey = poEntry.getKey();
                List<String> scoringPlayers = poEntry.getValue();

                if (scoringPlayers == null || !scoringPlayers.contains(player.getUserID())) {
                    continue;
                }

                // Handle custodians - can be multi-scored (check BEFORE the Mapper check)
                if (Constants.CUSTODIAN.equals(poKey)) {
                    addMultiScoredEntries(player, poKey, scoringPlayers, EntryType.CUSTODIAN, game, custodianEntries);
                    continue;
                }

                // Handle imperial - can be multi-scored (check BEFORE the Mapper check)
                if (Constants.IMPERIAL_RIDER.equals(poKey)) {
                    addMultiScoredEntries(player, poKey, scoringPlayers, EntryType.IMPERIAL, game, imperialEntries);
                    continue;
                }

                // Try to get as a real public objective
                PublicObjectiveModel po = Mapper.getPublicObjective(poKey);
                if (po == null) continue; // Not a real PO, skip it

                // Determine type based on stage
                Optional<EntryType> typeOpt = getPublicObjectiveType(poKey);
                if (typeOpt.isEmpty()) continue;
                EntryType type = typeOpt.get();

                ScoreBreakdownEntry entry =
                        createEntry(type, poKey, null, EntryState.SCORED, false, po.getPoints(), null, null);
                entry.setDescription(buildDescription(type, EntryState.SCORED, poKey, null, player, game));

                if (type == EntryType.PO_1) {
                    po1Entries.add(entry);
                } else {
                    po2Entries.add(entry);
                }
            }
        }

        // Scored Secrets
        Map<String, Integer> secretsScored = player.getSecretsScored();
        if (secretsScored != null) {
            for (String secretKey : secretsScored.keySet()) {
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.SECRET, secretKey, null, EntryState.SCORED, false, 1, null, null);
                entry.setDescription(
                        buildDescription(EntryType.SECRET, EntryState.SCORED, secretKey, null, player, game));
                secretEntries.add(entry);
            }
        }

        // SFTT (Support for the Throne) - losable
        // Count each SFTT card in play area (excluding player's own)
        List<ScoreBreakdownEntry> sfttEntries = new ArrayList<>();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotesInPlayArea != null) {
            for (String pnId : promissoryNotesInPlayArea) {
                // SFTT promissory notes end with "_sftt" (e.g., "blue_sftt", "red_sftt")
                if (pnId != null && pnId.endsWith("_sftt")) {
                    // Check if this is not the player's own SFTT
                    String pnColor = pnId.substring(0, pnId.length() - 5); // Remove "_sftt"
                    if (!pnColor.equals(player.getColor())) {
                        ScoreBreakdownEntry entry =
                                createEntry(EntryType.SFTT, null, null, EntryState.SCORED, true, 1, null, null);
                        entry.setDescription("Support for the Throne (" + pnColor + ")");
                        sfttEntries.add(entry);
                    }
                }
            }
        }

        // Shard of the Throne - losable
        if (hasShardOfTheThrone(player)) {
            ScoreBreakdownEntry entry =
                    createEntry(EntryType.SHARD, null, null, EntryState.SCORED, true, 1, null, null);
            entry.setDescription(buildDescription(EntryType.SHARD, EntryState.SCORED, null, null, player, game));
            relicEntries.add(entry);
        }

        // Styx token - losable (homebrew)
        if (hasStyx(player)) {
            ScoreBreakdownEntry entry = createEntry(EntryType.STYX, null, null, EntryState.SCORED, true, 1, null, null);
            entry.setDescription(buildDescription(EntryType.STYX, EntryState.SCORED, null, null, player, game));
            relicEntries.add(entry);
        }

        // Agenda points (from customPublicVP)
        // Note: customPublicVP defines the point value, but we need to check scoredPublicObjectives
        // to see which players actually have it scored
        Map<String, Integer> customVP = game.getCustomPublicVP();
        if (customVP != null && scoredPublics != null) {
            for (Map.Entry<String, Integer> vpEntry : customVP.entrySet()) {
                String key = vpEntry.getKey();
                Integer vp = vpEntry.getValue();

                // Skip custodian/imperial/relics which we already handled as specific types
                // Note: Shard can appear as both relic ID and custom PO name in the map
                if (KNOWN_RELIC_AND_SPECIAL_KEYS.contains(key) || key.startsWith(CUSTOM_PO_SHARD_PREFIX)) {
                    continue;
                }

                // Check if this player actually scored this custom objective
                List<String> scoringPlayers = scoredPublics.get(key);
                if (scoringPlayers == null || !scoringPlayers.contains(player.getUserID())) {
                    continue; // This player doesn't have this custom objective scored
                }

                // Check if this is a multi-scored custom objective
                int count = Collections.frequency(scoringPlayers, player.getUserID());
                boolean losable = isAgendaLosable(key);

                for (int i = 0; i < count; i++) {
                    ScoreBreakdownEntry entry =
                            createEntry(EntryType.AGENDA, null, key, EntryState.SCORED, losable, vp, null, null);
                    entry.setDescription(key);
                    agendaEntries.add(entry);
                }
            }
        }

        // Add entries in the correct order: PO1 → PO2 → Secrets → SFTT → Custodians → Imperial → Relics → Agendas
        entries.addAll(po1Entries);
        entries.addAll(po2Entries);
        entries.addAll(secretEntries);
        entries.addAll(sfttEntries);
        entries.addAll(custodianEntries);
        entries.addAll(imperialEntries);
        entries.addAll(relicEntries);
        entries.addAll(agendaEntries);
    }

    private static void addQualifiesAndPotentialEntries(Player player, Game game, List<ScoreBreakdownEntry> entries) {
        if (player == null || game == null || entries == null) {
            return;
        }

        // This method handles all QUALIFIES and POTENTIAL entries
        // The state is determined by whether the player meets the requirements

        // Determine max total public objectives based on scoring opportunities
        int maxPublicObjectives = 1; // Base: 1 scoring opportunity (status phase)

        // Check for Imperial holder + untapped
        if (hasImperialUntapped(player, game)) {
            maxPublicObjectives++;
        }

        // Check for Winnu hero unlocked and unused
        if (hasWinnuHeroAvailable(player)) {
            maxPublicObjectives++;
        }

        // Build list of all candidate public objectives with their state
        List<PublicObjectiveCandidate> candidates = new ArrayList<>();
        Map<String, Integer> revealedPublics = game.getRevealedPublicObjectives();
        if (revealedPublics != null) {
            for (String poKey : revealedPublics.keySet()) {
                // Skip if already scored
                if (hasPlayerScoredObjective(player, poKey, game)) {
                    continue;
                }

                // Skip special objectives
                if (Constants.CUSTODIAN.equals(poKey) || Constants.IMPERIAL_RIDER.equals(poKey)) {
                    continue;
                }

                PublicObjectiveModel po = Mapper.getPublicObjective(poKey);
                if (po == null) continue;

                // Determine type
                Optional<EntryType> typeOpt = getPublicObjectiveType(poKey);
                if (typeOpt.isEmpty()) continue;
                EntryType type = typeOpt.get();

                // Get progress and threshold
                int progress = ListPlayerInfoService.getPlayerProgressOnObjective(poKey, game, player);
                int threshold = ListPlayerInfoService.getObjectiveThreshold(poKey, game);

                // Determine if QUALIFIES or POTENTIAL
                EntryState state = progress >= threshold ? EntryState.QUALIFIES : EntryState.POTENTIAL;

                // Get point value from the objective model
                int pointValue = po.getPoints();

                candidates.add(new PublicObjectiveCandidate(poKey, type, pointValue, state, progress, threshold));
            }
        }

        // Sort candidates by: 1) point value (desc), 2) state (QUALIFIES before POTENTIAL), 3) progress % (desc)
        candidates.sort(Comparator.comparingInt((PublicObjectiveCandidate c) -> c.pointValue)
                .thenComparingInt(c -> c.state == EntryState.QUALIFIES ? 0 : 1)
                .thenComparingDouble(c -> c.threshold > 0 ? (double) c.progress / c.threshold : 0.0)
                .reversed());

        // Add top N candidates to entries
        for (int i = 0; i < Math.min(maxPublicObjectives, candidates.size()); i++) {
            PublicObjectiveCandidate candidate = candidates.get(i);
            ScoreBreakdownEntry entry = createEntry(
                    candidate.type,
                    candidate.key,
                    null,
                    candidate.state,
                    false,
                    candidate.pointValue,
                    candidate.progress,
                    candidate.threshold);
            entry.setDescription(buildDescription(candidate.type, candidate.state, candidate.key, null, player, game));
            entries.add(entry);
        }

        // Imperial points - can score one from Imperial SC and one from Winnu Hero (ADDITIVE)
        // QUALIFIES if has Mecatol Rex, otherwise will be added as POTENTIAL in addPotentialEntries
        boolean hasMecatol = hasControlOfMecatol(player, game);
        EntryState imperialEntryState = hasMecatol ? EntryState.QUALIFIES : EntryState.POTENTIAL;

        // Imperial SC point - QUALIFIES if has Mecatol
        if (hasImperialUntapped(player, game)) {
            ScoreBreakdownEntry entry =
                    createEntry(EntryType.IMPERIAL, null, null, imperialEntryState, false, 1, null, null);

            // Check if the Imperial holder can also draw a secret (not at cap)
            boolean canDrawSecret = canImperialHolderDrawSecret(player, game);
            entry.setCanDrawSecret(canDrawSecret);

            // Update description based on whether they can draw a secret
            if (canDrawSecret) {
                entry.setDescription("Imperial Point (or draw secret)");
            } else {
                entry.setDescription("Imperial Point");
            }
            entries.add(entry);
        }

        // Winnu Hero point - QUALIFIES if has Mecatol
        if (hasWinnuHeroAvailable(player)) {
            ScoreBreakdownEntry entry =
                    createEntry(EntryType.IMPERIAL, null, null, imperialEntryState, false, 1, null, null);
            entry.setDescription("Imperial Point (Winnu Hero)");
            entries.add(entry);
        }

        // Drawn secrets - always POTENTIAL (never qualifies since secrets are drawn randomly)
        Map<String, Integer> secrets = player.getSecrets();
        if (secrets != null) {
            for (String secretKey : secrets.keySet()) {
                // Don't leak secret info - set objectiveKey to null for POTENTIAL secrets
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.SECRET, null, null, EntryState.POTENTIAL, false, 1, null, null);
                entry.setDescription(
                        buildDescription(EntryType.SECRET, EntryState.POTENTIAL, null, null, player, game));
                entries.add(entry);
            }
        }

        // Crown of Emphidia - state depends on tomb status
        if (hasCrownOfEmphidia(player)) {
            boolean tombInPlay = isTombInPlay(game);
            boolean controlsTomb = controlsTombOfEmphidia(player, game);

            // QUALIFIES if tomb in play and player controls it
            // POTENTIAL if tomb in play but player doesn't control it
            // Will be UNSCORED if tomb not in play (handled in addUnscoredEntries)
            if (tombInPlay) {
                EntryState crownState = controlsTomb ? EntryState.QUALIFIES : EntryState.POTENTIAL;

                ScoreBreakdownEntry entry = createEntry(EntryType.CROWN, null, null, crownState, false, 1, null, null);
                entry.setTombInPlay(true);
                entry.setDescription(buildDescription(EntryType.CROWN, crownState, null, null, player, game));
                entries.add(entry);
            }
        }

        // Latvinia - state depends on tech types
        if (hasBookOfLatvinia(player)) {
            int techTypes = countUniqueTechTypes(player);
            EntryState latvniaState =
                    (techTypes >= LATVINIA_TECH_TYPE_THRESHOLD) ? EntryState.QUALIFIES : EntryState.POTENTIAL;

            ScoreBreakdownEntry entry = createEntry(
                    EntryType.LATVINIA, null, null, latvniaState, false, 1, techTypes, LATVINIA_TECH_TYPE_THRESHOLD);
            entry.setDescription(buildDescription(EntryType.LATVINIA, latvniaState, null, null, player, game));
            entries.add(entry);
        }
    }

    private static void addUnscoredEntries(Player player, Game game, List<ScoreBreakdownEntry> entries) {
        if (player == null || game == null || entries == null) {
            return;
        }

        // If can legally draw another secret this round
        if (canDrawAnotherSecret(player, game)) {
            ScoreBreakdownEntry entry =
                    createEntry(EntryType.SECRET, null, null, EntryState.UNSCORED, false, 1, null, null);
            entry.setDescription("Can draw 1 more secret this round");
            entries.add(entry);
        }

        // Crown if held but tomb NOT in play and not already added
        if (hasCrownOfEmphidia(player) && !alreadyHasEntry(entries, EntryType.CROWN)) {
            boolean tombInPlay = isTombInPlay(game);

            // Only add as UNSCORED if tomb not in play
            if (!tombInPlay) {
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.CROWN, null, null, EntryState.UNSCORED, false, 1, null, null);
                entry.setTombInPlay(false);
                entry.setDescription("Tomb of Emphidia not in play");
                entries.add(entry);
            }
        }
    }

    // Helper methods

    private static ScoreBreakdownEntry createEntry(
            EntryType type,
            String objectiveKey,
            String agendaKey,
            EntryState state,
            boolean losable,
            int pointValue,
            Integer currentProgress,
            Integer totalProgress) {

        ScoreBreakdownEntry entry = new ScoreBreakdownEntry();
        entry.setType(type);
        entry.setObjectiveKey(objectiveKey);
        entry.setAgendaKey(agendaKey);
        entry.setState(state);
        entry.setLosable(losable);
        entry.setPointValue(pointValue);
        entry.setCurrentProgress(currentProgress);
        entry.setTotalProgress(totalProgress);
        entry.setDescription(""); // Set later based on context

        return entry;
    }

    private static String buildDescription(
            EntryType type, EntryState state, String objectiveKey, String agendaKey, Player player, Game game) {
        // For SCORED entries - just show the name
        if (state == EntryState.SCORED) {
            switch (type) {
                case PO_1:
                case PO_2:
                    PublicObjectiveModel po = Mapper.getPublicObjective(objectiveKey);
                    return po != null ? po.getName() : objectiveKey;
                case SECRET:
                    return Mapper.getSecretObjective(objectiveKey) != null
                            ? Mapper.getSecretObjective(objectiveKey).getName()
                            : objectiveKey;
                case CUSTODIAN:
                    return "Custodians Point";
                case IMPERIAL:
                    return "Imperial Point";
                case CROWN:
                    return "Crown of Emphidia";
                case LATVINIA:
                    return "Book of Latvinia";
                case SFTT:
                    return "Support for the Throne";
                case SHARD:
                    return "Shard of the Throne";
                case STYX:
                    return "Styx Token";
                case AGENDA:
                    return agendaKey != null ? agendaKey : "Agenda Point";
                default:
                    return "";
            }
        }

        // For POTENTIAL PO entries - show when they can be scored
        if (state == EntryState.POTENTIAL && (type == EntryType.PO_1 || type == EntryType.PO_2)) {
            PublicObjectiveModel po = Mapper.getPublicObjective(objectiveKey);
            String baseName = po != null ? po.getName() : objectiveKey;

            List<String> scoringOpportunities = getScoringOpportunities(player, game);
            return baseName + " (" + String.join(" or ", scoringOpportunities) + ")";
        }

        // For other POTENTIAL/QUALIFIES entries - just show the name
        if (state == EntryState.POTENTIAL || state == EntryState.QUALIFIES) {
            switch (type) {
                case SECRET:
                    // Secrets should never leak the name unless SCORED
                    // POTENTIAL and QUALIFIES both hide the secret
                    return "Drawn Secret";
                case CUSTODIAN:
                    List<String> custodianOpportunities = getScoringOpportunities(player, game);
                    return "Custodians (" + String.join(" or ", custodianOpportunities) + ")";
                case CROWN:
                    return "Crown of Emphidia";
                case LATVINIA:
                    return "Book of Latvinia";
                case PO_1:
                case PO_2:
                    PublicObjectiveModel po = Mapper.getPublicObjective(objectiveKey);
                    return po != null ? po.getName() : objectiveKey;
                default:
                    return "";
            }
        }

        // UNSCORED state - description set elsewhere
        return "";
    }

    private static boolean isAgendaLosable(String agendaKey) {
        return LOSABLE_AGENDAS.contains(agendaKey);
    }

    private static boolean hasPlayerScoredObjective(Player player, String objectiveKey, Game game) {
        Map<String, List<String>> scoredPublics = game.getScoredPublicObjectives();
        if (scoredPublics == null) return false;

        List<String> scoringPlayers = scoredPublics.get(objectiveKey);
        return scoringPlayers != null && scoringPlayers.contains(player.getUserID());
    }

    private static int countPlayerScoresForObjective(Player player, String objectiveKey, Game game) {
        Map<String, List<String>> scoredPublics = game.getScoredPublicObjectives();
        if (scoredPublics == null) return 0;

        List<String> scoringPlayers = scoredPublics.get(objectiveKey);
        if (scoringPlayers == null) return 0;

        return Collections.frequency(scoringPlayers, player.getUserID());
    }

    private static boolean isTombInPlay(Game game) {
        if (game == null || game.getTileMap() == null) return false;

        return game.getTileMap().values().stream()
                .filter(Objects::nonNull)
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .filter(Objects::nonNull)
                .anyMatch(uh -> uh.getTokenList().contains("attachment_tombofemphidia.png"));
    }

    private static boolean controlsTombOfEmphidia(Player player, Game game) {
        if (player == null || game == null || game.getTileMap() == null) return false;

        return game.getTileMap().values().stream()
                .filter(Objects::nonNull)
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .filter(Objects::nonNull)
                .filter(uh -> uh.getTokenList().contains("attachment_tombofemphidia.png"))
                .filter(uh -> uh instanceof Planet)
                .map(uh -> (Planet) uh)
                .anyMatch(planet -> player.getPlanets().contains(planet.getName()));
    }

    private static int countUniqueTechTypes(Player player) {
        if (player == null) return 0;

        Set<TechnologyType> types = new HashSet<>();
        Collection<String> techs = player.getTechs();
        if (techs == null) return 0;

        for (String techID : techs) {
            TechnologyModel tech = Mapper.getTech(techID);
            if (tech != null && tech.getTypes() != null) {
                types.addAll(tech.getTypes());
            }
        }
        return types.size();
    }

    private static boolean hasImperialUntapped(Player player, Game game) {
        if (player == null) return false;
        return player.getSCs().contains(IMPERIAL_STRATEGY_CARD)
                && !player.getExhaustedSCs().contains(IMPERIAL_STRATEGY_CARD);
    }

    private static boolean hasWinnuHeroAvailable(Player player) {
        if (player == null) return false;

        Optional<Leader> heroOpt = player.getLeader("winnuhero");
        if (!heroOpt.isPresent()) return false;

        Leader hero = heroOpt.get();
        return hero.isActive() && !hero.isExhausted();
    }

    private static boolean hasControlOfMecatol(Player player, Game game) {
        if (player == null) return false;
        return player.getPlanets().contains(MECATOL_REX_PLANET);
    }

    private static boolean canImperialHolderDrawSecret(Player player, Game game) {
        if (player == null || game == null) return false;

        // Check if player is the Imperial holder
        if (!player.getSCs().contains(IMPERIAL_STRATEGY_CARD)) {
            return false;
        }

        // Check secret limit
        int maxSecrets = player.getMaxSOCount();
        int currentSecrets =
                player.getSecrets().size() + player.getSecretsScored().size();

        // Can draw a secret if not at cap
        return currentSecrets < maxSecrets;
    }

    private static boolean canDrawAnotherSecret(Player player, Game game) {
        if (player == null || game == null) return false;

        // Check secret limit
        int maxSecrets = player.getMaxSOCount();
        int currentSecrets =
                player.getSecrets().size() + player.getSecretsScored().size();
        if (currentSecrets >= maxSecrets) {
            return false;
        }

        // Find who has Imperial (if anyone picked it)
        Player imperialHolder = null;
        for (Player p : game.getRealPlayers()) {
            if (p.getSCs().contains(IMPERIAL_STRATEGY_CARD)) {
                imperialHolder = p;
                break;
            }
        }

        // If no one picked Imperial, can't draw secrets via Imperial
        if (imperialHolder == null) {
            return false;
        }

        // If Imperial has been played (exhausted), can't draw
        if (imperialHolder.getExhaustedSCs().contains(IMPERIAL_STRATEGY_CARD)) {
            return false;
        }

        // If this player is the Imperial holder, they can't draw a secret via Imperial
        // because the primary is either score an Imperial point OR draw a secret (not both)
        boolean isImperialHolder = player.getSCs().contains(IMPERIAL_STRATEGY_CARD);
        if (isImperialHolder) {
            return false;
        }

        // Player must have a strategy token to follow Imperial
        boolean hasStrategyToken = player.getStrategicCC() > 0;

        return hasStrategyToken;
    }

    private static boolean hasStyx(Player player) {
        if (player == null) return false;
        return player.hasRelic(RELIC_STYX);
    }

    private static boolean alreadyHasEntry(List<ScoreBreakdownEntry> entries, EntryType type) {
        if (entries == null) return false;
        return entries.stream().anyMatch(e -> e.getType() == type);
    }

    private static boolean alreadyHasEntry(List<ScoreBreakdownEntry> entries, EntryType type, EntryState state) {
        if (entries == null) return false;
        return entries.stream().anyMatch(e -> e.getType() == type && e.getState() == state);
    }

    private static boolean alreadyHasEntryForObjective(List<ScoreBreakdownEntry> entries, String objectiveKey) {
        if (entries == null || objectiveKey == null) return false;
        return entries.stream().anyMatch(e -> objectiveKey.equals(e.getObjectiveKey()));
    }

    private static boolean hasShardOfTheThrone(Player player) {
        if (player == null) return false;
        return player.hasRelic(RELIC_SHARD) || player.hasRelic(RELIC_SHARD_ABSOL);
    }

    private static boolean hasCrownOfEmphidia(Player player) {
        if (player == null) return false;
        return player.hasRelic(RELIC_CROWN) || player.hasRelic(RELIC_CROWN_BALDRICK);
    }

    private static boolean hasBookOfLatvinia(Player player) {
        if (player == null) return false;
        return player.hasRelic(RELIC_LATVINIA) || player.hasRelic(RELIC_LATVINIA_BALDRICK);
    }

    private static Optional<EntryType> getPublicObjectiveType(String poKey) {
        if (poKey == null) return Optional.empty();

        if (Mapper.getPublicObjectivesStage1().containsKey(poKey)) {
            return Optional.of(EntryType.PO_1);
        } else if (Mapper.getPublicObjectivesStage2().containsKey(poKey)) {
            return Optional.of(EntryType.PO_2);
        }
        return Optional.empty();
    }

    private static void addMultiScoredEntries(
            Player player,
            String poKey,
            List<String> scoringPlayers,
            EntryType type,
            Game game,
            List<ScoreBreakdownEntry> targetList) {

        int count = Collections.frequency(scoringPlayers, player.getUserID());
        for (int i = 0; i < count; i++) {
            ScoreBreakdownEntry entry = createEntry(type, null, null, EntryState.SCORED, false, 1, null, null);
            entry.setDescription(buildDescription(type, EntryState.SCORED, null, null, player, game));
            targetList.add(entry);
        }
    }

    private static List<String> getScoringOpportunities(Player player, Game game) {
        List<String> opportunities = new ArrayList<>();
        opportunities.add("status phase");

        if (hasImperialUntapped(player, game)) {
            opportunities.add("imperial");
        }

        if (hasWinnuHeroAvailable(player)) {
            opportunities.add("winnu hero");
        }

        return opportunities;
    }

    // Helper class for public objective candidates with state information
    private static class PublicObjectiveCandidate {
        private final String key;
        private final EntryType type;
        private final int pointValue;
        private final EntryState state;
        private final int progress;
        private final int threshold;

        public PublicObjectiveCandidate(
                String key, EntryType type, int pointValue, EntryState state, int progress, int threshold) {
            this.key = key;
            this.type = type;
            this.pointValue = pointValue;
            this.state = state;
            this.progress = progress;
            this.threshold = threshold;
        }
    }
}
