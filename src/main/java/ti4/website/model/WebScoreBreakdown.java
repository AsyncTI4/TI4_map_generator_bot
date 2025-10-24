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

    // Hardcoded metadata for losable agendas
    private static final Set<String> LOSABLE_AGENDAS = Set.of("political_censure", "mutiny");

    public static WebScoreBreakdown fromPlayer(Player player, Game game) {
        if (player == null || game == null) {
            WebScoreBreakdown breakdown = new WebScoreBreakdown();
            breakdown.entries = new ArrayList<>();
            return breakdown;
        }

        WebScoreBreakdown breakdown = new WebScoreBreakdown();
        breakdown.entries = new ArrayList<>();

        addScoredEntries(player, game, breakdown.entries);
        addQualifiesEntries(player, game, breakdown.entries);
        addPotentialEntries(player, game, breakdown.entries);
        addUnscoredEntries(player, game, breakdown.entries);

        return breakdown;
    }

    private static void addScoredEntries(Player player, Game game, List<ScoreBreakdownEntry> entries) {
        if (player == null || game == null || entries == null) {
            return;
        }

        // Scored PO1's and PO2's
        Map<String, List<String>> scoredPublics = game.getScoredPublicObjectives();
        if (scoredPublics != null) {
            for (Map.Entry<String, List<String>> poEntry : scoredPublics.entrySet()) {
                String poKey = poEntry.getKey();
                List<String> scoringPlayers = poEntry.getValue();

                if (scoringPlayers == null || !scoringPlayers.contains(player.getUserID())) {
                    continue;
                }

                PublicObjectiveModel po = Mapper.getPublicObjective(poKey);
                if (po == null) continue;

                // Handle custodians - can be multi-scored
                if (Constants.CUSTODIAN.equals(poKey)) {
                    int count = Collections.frequency(scoringPlayers, player.getUserID());
                    for (int i = 0; i < count; i++) {
                        ScoreBreakdownEntry entry =
                                createEntry(EntryType.CUSTODIAN, null, null, EntryState.SCORED, false, 1, null, null);
                        entry.setDescription(
                                buildDescription(EntryType.CUSTODIAN, EntryState.SCORED, null, null, player, game));
                        entries.add(entry);
                    }
                    continue;
                }

                // Handle imperial - can be multi-scored
                if (Constants.IMPERIAL_RIDER.equals(poKey)) {
                    int count = Collections.frequency(scoringPlayers, player.getUserID());
                    for (int i = 0; i < count; i++) {
                        ScoreBreakdownEntry entry =
                                createEntry(EntryType.IMPERIAL, null, null, EntryState.SCORED, false, 1, null, null);
                        entry.setDescription(
                                buildDescription(EntryType.IMPERIAL, EntryState.SCORED, null, null, player, game));
                        entries.add(entry);
                    }
                    continue;
                }

                // Determine type based on stage
                EntryType type;
                if (Mapper.getPublicObjectivesStage1().containsKey(poKey)) {
                    type = EntryType.PO_1;
                } else if (Mapper.getPublicObjectivesStage2().containsKey(poKey)) {
                    type = EntryType.PO_2;
                } else {
                    continue;
                }

                ScoreBreakdownEntry entry =
                        createEntry(type, poKey, null, EntryState.SCORED, false, po.getPoints(), null, null);
                entry.setDescription(buildDescription(type, EntryState.SCORED, poKey, null, player, game));
                entries.add(entry);
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
                entries.add(entry);
            }
        }

        // SFTT (Support for the Throne) - losable
        if (hasSupportForThrone(player, game)) {
            ScoreBreakdownEntry entry = createEntry(EntryType.SFTT, null, null, EntryState.SCORED, true, 1, null, null);
            entry.setDescription(buildDescription(EntryType.SFTT, EntryState.SCORED, null, null, player, game));
            entries.add(entry);
        }

        // Shard of the Throne - losable
        if (player.hasRelic("shard") || player.hasRelic("absol_shardofthethrone1")) {
            ScoreBreakdownEntry entry =
                    createEntry(EntryType.SHARD, null, null, EntryState.SCORED, true, 1, null, null);
            entry.setDescription(buildDescription(EntryType.SHARD, EntryState.SCORED, null, null, player, game));
            entries.add(entry);
        }

        // Styx token - losable (homebrew)
        if (hasStyx(player)) {
            ScoreBreakdownEntry entry = createEntry(EntryType.STYX, null, null, EntryState.SCORED, true, 1, null, null);
            entry.setDescription(buildDescription(EntryType.STYX, EntryState.SCORED, null, null, player, game));
            entries.add(entry);
        }

        // Agenda points (from customPublicVP)
        Map<String, Integer> customVP = game.getCustomPublicVP();
        if (customVP != null) {
            for (Map.Entry<String, Integer> vpEntry : customVP.entrySet()) {
                String key = vpEntry.getKey();
                Integer vp = vpEntry.getValue();

                // Skip custodian/imperial which we already handled
                if (Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key)) {
                    continue;
                }

                boolean losable = isAgendaLosable(key);
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.AGENDA, null, key, EntryState.SCORED, losable, vp, null, null);
                entry.setDescription(buildDescription(EntryType.AGENDA, EntryState.SCORED, null, key, player, game));
                entries.add(entry);
            }
        }
    }

    private static void addQualifiesEntries(Player player, Game game, List<ScoreBreakdownEntry> entries) {
        if (player == null || game == null || entries == null) {
            return;
        }

        // Revealed publics where player meets threshold
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

                // Get progress and threshold
                int progress = ListPlayerInfoService.getPlayerProgressOnObjective(poKey, game, player);
                int threshold = ListPlayerInfoService.getObjectiveThreshold(poKey, game);

                // If player qualifies (meets or exceeds threshold)
                if (progress >= threshold) {
                    PublicObjectiveModel po = Mapper.getPublicObjective(poKey);
                    if (po == null) continue;

                    // Determine type
                    EntryType type;
                    if (Mapper.getPublicObjectivesStage1().containsKey(poKey)) {
                        type = EntryType.PO_1;
                    } else if (Mapper.getPublicObjectivesStage2().containsKey(poKey)) {
                        type = EntryType.PO_2;
                    } else {
                        continue;
                    }

                    ScoreBreakdownEntry entry = createEntry(
                            type, poKey, null, EntryState.QUALIFIES, false, po.getPoints(), progress, threshold);
                    entry.setDescription(buildDescription(type, EntryState.QUALIFIES, poKey, null, player, game));
                    entries.add(entry);
                }
            }
        }

        // Latvinia if held and has 4 tech types but not scored
        if (player.hasRelic("bookoflatvinia") || player.hasRelic("baldrick_bookoflatvinia")) {
            int techTypes = countUniqueTechTypes(player);

            if (techTypes >= 4) {
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.LATVINIA, null, null, EntryState.QUALIFIES, false, 1, techTypes, 4);
                entry.setDescription(
                        buildDescription(EntryType.LATVINIA, EntryState.QUALIFIES, null, null, player, game));
                entries.add(entry);
            }
        }

        // Crown if player has crown AND controls tomb planet
        if (player.hasRelic("emphidia") || player.hasRelic("baldrick_crownofemphidia")) {
            if (controlsTombOfEmphidia(player, game)) {
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.CROWN, null, null, EntryState.QUALIFIES, false, 1, null, null);
                entry.setTombInPlay(true);
                entry.setDescription(buildDescription(EntryType.CROWN, EntryState.QUALIFIES, null, null, player, game));
                entries.add(entry);
            }
        }
    }

    private static void addPotentialEntries(Player player, Game game, List<ScoreBreakdownEntry> entries) {
        if (player == null || game == null || entries == null) {
            return;
        }

        // Get all revealed unscored publics, sorted by point value (descending)
        List<PublicObjectiveInfo> unscoredPublics = getUnscoredRevealedPublics(player, game);
        unscoredPublics.sort(
                Comparator.comparingInt(PublicObjectiveInfo::getPointValue).reversed());

        // Determine how many potential publics to show
        int potentialPublicsCount = 1; // Base: show highest value unscored public

        // Check for Imperial holder + untapped
        if (hasImperialUntapped(player, game)) {
            potentialPublicsCount++;
        }

        // Check for Winnu hero unlocked and unused
        if (hasWinnuHeroAvailable(player)) {
            potentialPublicsCount++;
        }

        // Add potential publics (up to calculated count)
        for (int i = 0; i < Math.min(potentialPublicsCount, unscoredPublics.size()); i++) {
            PublicObjectiveInfo info = unscoredPublics.get(i);

            // Get progress info
            int progress = ListPlayerInfoService.getPlayerProgressOnObjective(info.getKey(), game, player);
            int threshold = ListPlayerInfoService.getObjectiveThreshold(info.getKey(), game);

            ScoreBreakdownEntry entry = createEntry(
                    info.getType(),
                    info.getKey(),
                    null,
                    EntryState.POTENTIAL,
                    false,
                    info.getPointValue(),
                    progress,
                    threshold);
            entry.setDescription(
                    buildDescription(info.getType(), EntryState.POTENTIAL, info.getKey(), null, player, game));
            entries.add(entry);
        }

        // Custodians (if Imperial + untapped OR Winnu hero available)
        if ((hasImperialUntapped(player, game) || hasWinnuHeroAvailable(player))
                && !hasControlOfMecatol(player, game)
                && !hasPlayerScoredObjective(player, Constants.CUSTODIAN, game)) {
            ScoreBreakdownEntry entry =
                    createEntry(EntryType.CUSTODIAN, null, null, EntryState.POTENTIAL, false, 1, null, null);
            entry.setDescription(buildDescription(EntryType.CUSTODIAN, EntryState.POTENTIAL, null, null, player, game));
            entries.add(entry);
        }

        // Every drawn unscored secret
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

        // Crown of Emphidia (if held and not already in QUALIFIES)
        if ((player.hasRelic("emphidia") || player.hasRelic("baldrick_crownofemphidia"))
                && !alreadyHasEntry(entries, EntryType.CROWN, EntryState.QUALIFIES)) {

            boolean tombInPlay = isTombInPlay(game);
            boolean controlsTomb = controlsTombOfEmphidia(player, game);

            // If tomb in play and controls it, would be in QUALIFIES (already handled)
            // So only add to POTENTIAL if doesn't qualify
            if (!tombInPlay || !controlsTomb) {
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.CROWN, null, null, EntryState.POTENTIAL, false, 1, null, null);
                entry.setTombInPlay(tombInPlay);
                entry.setDescription(buildDescription(EntryType.CROWN, EntryState.POTENTIAL, null, null, player, game));
                entries.add(entry);
            }
        }

        // Latvinia with progress tracker (if held and not in QUALIFIES)
        if ((player.hasRelic("bookoflatvinia") || player.hasRelic("baldrick_bookoflatvinia"))
                && !alreadyHasEntry(entries, EntryType.LATVINIA, EntryState.QUALIFIES)) {

            int techTypes = countUniqueTechTypes(player);

            // Only add if doesn't qualify (< 4 tech types)
            if (techTypes < 4) {
                ScoreBreakdownEntry entry =
                        createEntry(EntryType.LATVINIA, null, null, EntryState.POTENTIAL, false, 1, techTypes, 4);
                entry.setDescription(
                        buildDescription(EntryType.LATVINIA, EntryState.POTENTIAL, null, null, player, game));
                entries.add(entry);
            }
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
        if ((player.hasRelic("emphidia") || player.hasRelic("baldrick_crownofemphidia"))
                && !alreadyHasEntry(entries, EntryType.CROWN)) {

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

            List<String> scoringOpportunities = new ArrayList<>();
            scoringOpportunities.add("status phase");

            if (hasImperialUntapped(player, game)) {
                scoringOpportunities.add("imperial");
            }

            if (hasWinnuHeroAvailable(player)) {
                scoringOpportunities.add("winnu hero");
            }

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
                    List<String> custodianOpportunities = new ArrayList<>();
                    if (hasImperialUntapped(player, game)) {
                        custodianOpportunities.add("imperial");
                    }
                    if (hasWinnuHeroAvailable(player)) {
                        custodianOpportunities.add("winnu hero");
                    }
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

    private static List<PublicObjectiveInfo> getUnscoredRevealedPublics(Player player, Game game) {
        List<PublicObjectiveInfo> result = new ArrayList<>();
        Map<String, Integer> revealedPublics = game.getRevealedPublicObjectives();
        if (revealedPublics == null) return result;

        for (String poKey : revealedPublics.keySet()) {
            // Skip if scored
            if (hasPlayerScoredObjective(player, poKey, game)) {
                continue;
            }

            // Skip special objectives (custodian, imperial)
            if (Constants.CUSTODIAN.equals(poKey) || Constants.IMPERIAL_RIDER.equals(poKey)) {
                continue;
            }

            PublicObjectiveModel po = Mapper.getPublicObjective(poKey);
            if (po == null) continue;

            // Determine type
            EntryType type;
            if (Mapper.getPublicObjectivesStage1().containsKey(poKey)) {
                type = EntryType.PO_1;
            } else if (Mapper.getPublicObjectivesStage2().containsKey(poKey)) {
                type = EntryType.PO_2;
            } else {
                continue;
            }

            result.add(new PublicObjectiveInfo(poKey, type, po.getPoints()));
        }

        return result;
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
        return player.getSCs().contains(8) && !player.getExhaustedSCs().contains(8);
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
        return player.getPlanets().contains("mr");
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

        // Check if Imperial SC (8) is in the game and not yet played
        if (!game.getScTradeGoods().containsKey(8)) {
            return false; // Imperial not in this game
        }

        // Find who has Imperial
        Player imperialHolder = null;
        for (Player p : game.getRealPlayers()) {
            if (p.getSCs().contains(8)) {
                imperialHolder = p;
                break;
            }
        }

        // If Imperial has been played (exhausted), can't draw
        if (imperialHolder != null && imperialHolder.getExhaustedSCs().contains(8)) {
            return false;
        }

        // Player must either have Imperial or have a strategy token to follow
        boolean hasImperial = player.getSCs().contains(8);
        boolean hasStrategyToken = player.getStrategicCC() > 0;

        return hasImperial || hasStrategyToken;
    }

    private static boolean hasSupportForThrone(Player player, Game game) {
        if (game == null) return false;

        // SFTT is typically an agenda that gives a point
        return game.getLaws().containsKey("support_for_the_throne")
                || (game.getCustomPublicVP() != null && game.getCustomPublicVP().containsKey("support_for_the_throne"));
    }

    private static boolean hasStyx(Player player) {
        if (player == null) return false;
        return player.hasRelic("styx");
    }

    private static boolean alreadyHasEntry(List<ScoreBreakdownEntry> entries, EntryType type) {
        if (entries == null) return false;
        return entries.stream().anyMatch(e -> e.getType() == type);
    }

    private static boolean alreadyHasEntry(List<ScoreBreakdownEntry> entries, EntryType type, EntryState state) {
        if (entries == null) return false;
        return entries.stream().anyMatch(e -> e.getType() == type && e.getState() == state);
    }

    // Helper class for sorting publics
    private static class PublicObjectiveInfo {
        private final String key;
        private final EntryType type;
        private final int pointValue;

        public PublicObjectiveInfo(String key, EntryType type, int pointValue) {
            this.key = key;
            this.type = type;
            this.pointValue = pointValue;
        }

        public String getKey() {
            return key;
        }

        public EntryType getType() {
            return type;
        }

        public int getPointValue() {
            return pointValue;
        }
    }
}
