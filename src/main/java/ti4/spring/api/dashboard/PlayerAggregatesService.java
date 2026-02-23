package ti4.spring.api.dashboard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.executors.ExecutorServiceManager;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.model.TechnologyModel;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
@Service
/**
 * Builds and caches per-player dashboard aggregates.
 *
 * <p>Cache-busting is based on a deterministic hash of completed game IDs: completed game names are
 * deduplicated, sorted ascending, concatenated with a stable delimiter, and hashed with SHA-256. If
 * hash, count, or aggregate version differs from the stored row, a background recompute is queued.
 *
 * <p>The dashboard call is non-blocking: this service returns fresh cached data when available. If the
 * cache is stale, it schedules refresh work asynchronously and returns an empty aggregate shell until
 * recompute completes.
 */
class PlayerAggregatesService {

    private static final int CURRENT_AGGREGATES_VERSION = 2;
    private static final int FAILED_AGGREGATES_VERSION = -1;
    private static final String HASH_DELIMITER = "\u001F";
    private static final JsonMapper mapper = ti4.json.JsonMapperManager.basic();

    private final PlayerAggregatesCacheRepository repository;

    /**
     * Returns aggregates for the player and triggers cache refresh when stale.
     *
     * <p>Behavior:
     * 1) Compute current completed-game hash.
     * 2) Compare against cached row.
     * 3) Queue async recompute if stale/missing.
     * 4) Return fresh cached payload if present, otherwise an empty aggregate shell.
     */
    PlayerDashboardResponse.PlayerAggregates getOrQueueRefresh(String userId, List<ManagedGame> playerGames) {
        CacheContext context = buildCacheContext(userId, playerGames);
        if (needsRefresh(context)) {
            queueRecompute(context.userId(), context.completedGameIds(), context.completedGamesHash());
        }
        return buildResponseFromCache(context);
    }

    /**
     * Builds the cache context used for refresh and response decisions.
     */
    private CacheContext buildCacheContext(String userId, List<ManagedGame> playerGames) {
        List<String> completedGameIds = getTrackedCompletedGameIds(playerGames);
        String completedGamesHash = hashCompletedGameIds(completedGameIds);
        PlayerAggregatesCache cached = repository.findById(userId).orElse(null);
        boolean shouldRecompute = shouldRecompute(cached, completedGamesHash, completedGameIds.size());
        return new CacheContext(userId, completedGameIds, completedGamesHash, cached, shouldRecompute);
    }

    /**
     * Determines whether the aggregate cache should be refreshed for this context.
     */
    private static boolean needsRefresh(CacheContext context) {
        return context.shouldRecompute();
    }

    /**
     * Builds API response from cache context, returning an empty shell when stale/invalid.
     */
    private PlayerDashboardResponse.PlayerAggregates buildResponseFromCache(CacheContext context) {
        if (context.shouldRecompute()) {
            return emptyAggregates(
                    false,
                    context.completedGamesHash(),
                    context.completedGameIds().size(),
                    0,
                    context.cached() == null ? null : context.cached().getComputedAtEpochMs(),
                    context.completedGameIds());
        }

        if (context.cached() == null) {
            return emptyAggregates(
                    false,
                    context.completedGamesHash(),
                    context.completedGameIds().size(),
                    0,
                    null,
                    context.completedGameIds());
        }

        StoredAggregates stored =
                parseStoredAggregates(context.cached().getAggregatesJson()).orElse(null);
        if (stored == null || !isStoredAggregateUsable(stored)) {
            queueRecompute(context.userId(), context.completedGameIds(), context.completedGamesHash());
            return emptyAggregates(
                    false,
                    context.completedGamesHash(),
                    context.completedGameIds().size(),
                    0,
                    context.cached().getComputedAtEpochMs(),
                    context.completedGameIds());
        }

        int eligibleGameCount = Optional.ofNullable(stored.eligibleGameCount()).orElse(0);
        PlayerDashboardResponse.TechStats techStats = new PlayerDashboardResponse.TechStats(
                Optional.ofNullable(stored.techById()).orElse(Map.of()));
        PlayerDashboardResponse.FactionWinStats factionWinStats = new PlayerDashboardResponse.FactionWinStats(
                Optional.ofNullable(stored.factionWinsById()).orElse(Map.of()));

        return new PlayerDashboardResponse.PlayerAggregates(
                true,
                context.completedGamesHash(),
                context.completedGameIds().size(),
                eligibleGameCount,
                CURRENT_AGGREGATES_VERSION,
                context.cached().getComputedAtEpochMs(),
                context.completedGameIds(),
                techStats,
                factionWinStats);
    }

    private static boolean isStoredAggregateUsable(StoredAggregates stored) {
        return stored.version() == CURRENT_AGGREGATES_VERSION
                && stored.techById() != null
                && stored.eligibleGameCount() != null
                && stored.factionWinsById() != null;
    }

    private static PlayerDashboardResponse.PlayerAggregates emptyAggregates(
            boolean ready,
            String completedGamesHash,
            int completedGameCount,
            int eligibleGameCount,
            Long computedAtEpochMs,
            List<String> completedGameIds) {
        return new PlayerDashboardResponse.PlayerAggregates(
                ready,
                completedGamesHash,
                completedGameCount,
                eligibleGameCount,
                CURRENT_AGGREGATES_VERSION,
                computedAtEpochMs,
                completedGameIds,
                new PlayerDashboardResponse.TechStats(Map.of()),
                new PlayerDashboardResponse.FactionWinStats(Map.of()));
    }

    /**
     * Determines the canonical completed-game ID set used by hash and recompute logic.
     *
     * <p>Only ended games that pass {@link #isValidGameForAggregates(ManagedGame, Game)} are included.
     * IDs are deduplicated and sorted so the resulting hash is stable and independent of iteration order.
     */
    private static List<String> getTrackedCompletedGameIds(List<ManagedGame> playerGames) {
        if (playerGames == null || playerGames.isEmpty()) {
            return Collections.emptyList();
        }
        return playerGames.stream()
                .filter(ManagedGame::isHasEnded)
                .filter(managedGame -> {
                    Game game = managedGame.getGame();
                    return game != null && isValidGameForAggregates(managedGame, game);
                })
                .map(ManagedGame::getName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Returns {@code true} when cached aggregates must be recomputed.
     *
     * <p>Recompute is required if the row is missing, aggregate version changed, completed-game hash
     * changed, or completed-game count changed.
     */
    private static boolean shouldRecompute(
            PlayerAggregatesCache cached, String completedGamesHash, int completedGameCount) {
        if (cached == null) {
            return true;
        }
        return cached.getAggregatesVersion() != CURRENT_AGGREGATES_VERSION
                || !completedGamesHash.equals(cached.getCompletedGamesHash())
                || completedGameCount != cached.getCompletedGameCount();
    }

    /**
     * Queues one async recompute task per player (deduped by task name).
     */
    private void queueRecompute(String userId, List<String> completedGameIds, String completedGamesHash) {
        String taskName = "dashboard-player-aggregates:" + userId;
        ExecutorServiceManager.runAsyncIfNotRunning(
                taskName, () -> recomputeAndPersist(userId, completedGameIds, completedGamesHash));
    }

    /**
     * Recomputes aggregates and upserts the cache row.
     *
     * <p>If computation fails, stores a truncated error message and timestamp so the failure is visible
     * without preventing future retries.
     */
    private void recomputeAndPersist(String userId, List<String> completedGameIds, String completedGamesHash) {
        long startedAt = System.currentTimeMillis();
        try {
            ComputedAggregates computed = computeAggregates(userId, completedGameIds);
            StoredAggregates aggregates = new StoredAggregates(
                    CURRENT_AGGREGATES_VERSION,
                    userId,
                    completedGameIds,
                    computed.eligibleGameCount(),
                    computed.byTech(),
                    computed.factionWinsById());

            PlayerAggregatesCache row = repository.findById(userId).orElseGet(PlayerAggregatesCache::new);
            row.setUserId(userId);
            row.setCompletedGamesHash(completedGamesHash);
            row.setCompletedGameCount(completedGameIds.size());
            row.setAggregatesVersion(CURRENT_AGGREGATES_VERSION);
            row.setAggregatesJson(mapper.writeValueAsString(aggregates));
            row.setComputedAtEpochMs(System.currentTimeMillis());
            row.setLastError(null);
            row.setLastErrorAtEpochMs(null);
            repository.save(row);

            long elapsed = System.currentTimeMillis() - startedAt;
            BotLogger.info("Computed player aggregates for user " + userId + " from " + completedGameIds.size()
                    + " completed games in " + elapsed + "ms.");
        } catch (Exception e) {
            BotLogger.error("Failed recomputing player aggregates for user " + userId, e);
            PlayerAggregatesCache row = repository.findById(userId).orElseGet(PlayerAggregatesCache::new);
            row.setUserId(userId);
            row.setCompletedGamesHash(completedGamesHash);
            row.setCompletedGameCount(completedGameIds.size());
            row.setAggregatesVersion(FAILED_AGGREGATES_VERSION);
            if (row.getAggregatesJson() == null) {
                row.setAggregatesJson("{}");
            }
            if (row.getComputedAtEpochMs() <= 0) {
                row.setComputedAtEpochMs(System.currentTimeMillis());
            }
            row.setLastError(safeTrimError(e.getMessage()));
            row.setLastErrorAtEpochMs(System.currentTimeMillis());
            repository.save(row);
        }
    }

    private static String safeTrimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        int maxLength = 1500;
        if (errorMessage.length() <= maxLength) {
            return errorMessage;
        }
        return errorMessage.substring(0, maxLength);
    }

    /**
     * Computes tech aggregates from final game state.
     *
     * <p>For each completed game, technologies are normalized to canonical/original IDs via
     * {@code homebrewReplacesID} chain traversal. {@code gamesWithTech} counts completed games where
     * the player had that tech in final state.
     *
     * <p>{@code percentInEligibleGames} uses the denominator of games that are globally valid and not
     * Twilight's Fall.
     */
    private static ComputedAggregates computeAggregates(String userId, Collection<String> completedGameIds) {
        List<GameAggregateSnapshot> snapshots = loadPlayerSnapshotsPerGame(userId, completedGameIds);
        TechCountAccumulator counts = accumulateEligibleCounts(snapshots);
        Map<String, PlayerDashboardResponse.TechStat> byTech = toTechStatMap(counts);
        Map<String, Integer> factionWinsById = countFactionWins(snapshots);
        return new ComputedAggregates(counts.eligibleGameCount(), byTech, factionWinsById);
    }

    /**
     * Loads canonical player tech sets for each completed game.
     */
    private static List<GameAggregateSnapshot> loadPlayerSnapshotsPerGame(
            String userId, Collection<String> completedGameIds) {
        List<GameAggregateSnapshot> snapshots = new ArrayList<>();
        for (String gameId : completedGameIds) {
            if (gameId == null) {
                continue;
            }
            var managedGame = GameManager.getManagedGame(gameId);
            if (managedGame == null) {
                continue;
            }
            Game game = managedGame.getGame();
            if (game == null) {
                continue;
            }
            if (!isValidGameForAggregates(managedGame, game)) {
                continue;
            }
            Player player = game.getPlayer(userId);
            if (player == null) {
                continue;
            }

            Set<String> canonicalTechsForGame = canonicalTechsForPlayer(player);
            boolean includeInTechAggregates = !managedGame.isTwilightsFallMode();
            String canonicalFactionId = normalizeToCanonicalFactionId(player.getFaction());
            boolean playerWon = game.hasWinner()
                    && game.getWinners().stream().anyMatch(winner -> userId.equals(winner.getUserID()));
            snapshots.add(new GameAggregateSnapshot(
                    includeInTechAggregates,
                    includeInTechAggregates,
                    canonicalTechsForGame,
                    canonicalFactionId,
                    playerWon));
        }
        return snapshots;
    }

    /**
     * Global filter for dashboard aggregate tracking.
     *
     * <p>Whitelist policy:
     * - Exclude Franken.
     * - Exclude all homebrew unless it is extra-secret-only.
     * - Allow no-swap and VP/SO target configuration changes.
     */
    private static boolean isValidGameForAggregates(ManagedGame managedGame, Game game) {
        if (game.isFrankenGame()) {
            return false;
        }

        if (!game.hasHomebrew()) {
            return true;
        }

        // Homebrew whitelist: only extra-secret-only games are allowed.
        if (!game.isExtraSecretMode()) {
            return false;
        }

        // Deny all currently-known non-whitelisted homebrew reasons.
        if (game.isHomebrew()
                || game.isFowMode()
                || game.isFacilitiesMode()
                || game.isLightFogMode()
                || game.isRedTapeMode()
                || game.isDiscordantStarsMode()
                || game.isMiltyModMode()
                || game.isThundersEdgeDemo()
                || game.isAbsolMode()
                || game.isVotcMode()
                || game.isPromisesPromisesMode()
                || game.isFlagshippingMode()
                || (game.getSpinMode() != null && !"OFF".equalsIgnoreCase(game.getSpinMode()))
                || game.isHomebrewSCMode()
                || game.isCommunityMode()) {
            return false;
        }

        // Mirror structural/source checks from Game#hasHomebrew.
        if (game.getRealAndEliminatedPlayers().size() < 3
                || game.getRealAndEliminatedPlayers().size() > 8) {
            return false;
        }

        if (!allDecksOfficial(game)
                || !allTilesOfficial(game)
                || !allFactionsOfficial(game)
                || !allLeadersOfficial(game)) {
            return false;
        }

        return true;
    }

    private static boolean allDecksOfficial(Game game) {
        List<String> deckIds = List.of(
                game.getAcDeckID(),
                game.getSoDeckID(),
                game.getStage1PublicDeckID(),
                game.getStage2PublicDeckID(),
                game.getRelicDeckID(),
                game.getAgendaDeckID(),
                game.getExplorationDeckID(),
                game.getTechnologyDeckID(),
                game.getEventDeckID());

        boolean decksOfficial = deckIds.stream().allMatch(deckId -> {
            if (deckId == null || "null".equals(deckId)) {
                return true;
            }
            var deck = Mapper.getDeck(deckId);
            return deck == null || deck.getSource().isOfficial();
        });

        var scSet = Mapper.getStrategyCardSets().get(game.getScSetID());
        return decksOfficial && scSet != null && scSet.getSource().isOfficial();
    }

    private static boolean allTilesOfficial(Game game) {
        return game.getTileMap().values().stream().allMatch(tile -> {
            if (tile == null || tile.getTileModel() == null) {
                return true;
            }
            return tile.getTileModel().getSource().isOfficial();
        });
    }

    private static boolean allFactionsOfficial(Game game) {
        return game.getFactions().stream()
                .map(Mapper::getFaction)
                .filter(Objects::nonNull)
                .allMatch(faction -> faction.getSource().isOfficial());
    }

    private static boolean allLeadersOfficial(Game game) {
        return game.getRealAndEliminatedAndDummyPlayers().stream()
                .map(Player::getLeaderIDs)
                .flatMap(Collection::stream)
                .map(Mapper::getLeader)
                .filter(Objects::nonNull)
                .allMatch(leader -> leader.getSource().isOfficial());
    }

    /**
     * Creates the canonical tech set for a player's final game state.
     */
    private static Set<String> canonicalTechsForPlayer(Player player) {
        Set<String> canonicalTechsForGame = new HashSet<>();
        for (String techId : player.getTechs()) {
            String canonicalTechId = normalizeToCanonicalTechId(techId);
            if (canonicalTechId != null && !canonicalTechId.isBlank()) {
                canonicalTechsForGame.add(canonicalTechId);
            }
        }
        return canonicalTechsForGame;
    }

    /**
     * Accumulates completed and eligible tech counters from game snapshots.
     */
    private static TechCountAccumulator accumulateEligibleCounts(List<GameAggregateSnapshot> snapshots) {
        Map<String, Integer> gamesWithTech = new HashMap<>();
        Map<String, Integer> eligibleGamesWithTech = new HashMap<>();
        int eligibleGameCount = 0;

        for (GameAggregateSnapshot snapshot : snapshots) {
            if (!snapshot.includeInTechAggregates()) {
                continue;
            }
            if (snapshot.eligibleForPercent()) {
                eligibleGameCount++;
            }

            snapshot.canonicalTechs().forEach(tech -> gamesWithTech.merge(tech, 1, Integer::sum));
            if (snapshot.eligibleForPercent()) {
                snapshot.canonicalTechs().forEach(tech -> eligibleGamesWithTech.merge(tech, 1, Integer::sum));
            }
        }

        return new TechCountAccumulator(gamesWithTech, eligibleGamesWithTech, eligibleGameCount);
    }

    /**
     * Counts completed wins by canonical faction ID.
     */
    private static Map<String, Integer> countFactionWins(List<GameAggregateSnapshot> snapshots) {
        Map<String, Integer> winsByFaction = new HashMap<>();
        for (GameAggregateSnapshot snapshot : snapshots) {
            if (!snapshot.playerWon()) {
                continue;
            }
            String factionId = snapshot.canonicalFactionId();
            if (factionId == null || factionId.isBlank()) {
                continue;
            }
            winsByFaction.merge(factionId, 1, Integer::sum);
        }

        return winsByFaction.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /**
     * Projects counters into API-ready tech stats sorted by tech ID.
     */
    private static Map<String, PlayerDashboardResponse.TechStat> toTechStatMap(TechCountAccumulator counts) {
        int eligibleGameCountFinal = counts.eligibleGameCount();
        return counts.gamesWithTech().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            int eligibleCountForTech =
                                    counts.eligibleGamesWithTech().getOrDefault(entry.getKey(), 0);
                            double percent = eligibleGameCountFinal == 0
                                    ? 0.0
                                    : (eligibleCountForTech * 100.0) / eligibleGameCountFinal;
                            return new PlayerDashboardResponse.TechStat(entry.getValue(), percent);
                        },
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
    }

    /**
     * Maps a potentially homebrew tech ID to its canonical/original tech ID.
     *
     * <p>If a tech replaces another tech, this method follows replacement links until the base tech ID
     * is reached. Cycles are guarded by a visited set.
     */
    private static String normalizeToCanonicalTechId(String techId) {
        if (techId == null || techId.isBlank()) {
            return null;
        }

        String current = techId;
        Set<String> visited = new HashSet<>();
        while (visited.add(current)) {
            TechnologyModel model = Mapper.getTech(current);
            if (model == null) {
                return current;
            }
            String replacement = model.getHomebrewReplacesID().orElse(null);
            if (replacement == null || replacement.isBlank()) {
                String alias = model.getAlias();
                return alias == null || alias.isBlank() ? current : alias;
            }
            current = replacement;
        }
        return techId;
    }

    /**
     * Maps a potentially homebrew faction ID to its canonical/original faction ID.
     */
    private static String normalizeToCanonicalFactionId(String factionId) {
        if (factionId == null || factionId.isBlank() || "null".equalsIgnoreCase(factionId)) {
            return null;
        }

        String current = factionId;
        Set<String> visited = new HashSet<>();
        while (visited.add(current)) {
            var model = Mapper.getFaction(current);
            if (model == null) {
                return current;
            }
            String replacement = model.getHomebrewReplacesID().orElse(null);
            if (replacement == null || replacement.isBlank()) {
                String alias = model.getAlias();
                return alias == null || alias.isBlank() ? current : alias;
            }
            current = replacement;
        }
        return factionId;
    }

    /**
     * Parses persisted aggregate JSON into a typed record.
     */
    private static Optional<StoredAggregates> parseStoredAggregates(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, StoredAggregates.class));
        } catch (Exception e) {
            BotLogger.error("Failed to parse stored player aggregates JSON.", e);
            return Optional.empty();
        }
    }

    /**
     * Hashes completed game IDs for cache-busting.
     *
     * <p>Input must already be canonicalized (sorted/deduped). IDs are concatenated with a stable
     * delimiter and hashed using SHA-256. Any change to the completed-game set changes the hash and
     * triggers recompute.
     */
    static String hashCompletedGameIds(List<String> completedGameIds) {
        String joined = String.join(HASH_DELIMITER, new ArrayList<>(completedGameIds));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record CacheContext(
            String userId,
            List<String> completedGameIds,
            String completedGamesHash,
            PlayerAggregatesCache cached,
            boolean shouldRecompute) {}

    private record GameAggregateSnapshot(
            boolean includeInTechAggregates,
            boolean eligibleForPercent,
            Set<String> canonicalTechs,
            String canonicalFactionId,
            boolean playerWon) {}

    private record TechCountAccumulator(
            Map<String, Integer> gamesWithTech, Map<String, Integer> eligibleGamesWithTech, int eligibleGameCount) {}

    private record ComputedAggregates(
            int eligibleGameCount,
            Map<String, PlayerDashboardResponse.TechStat> byTech,
            Map<String, Integer> factionWinsById) {}

    private record StoredAggregates(
            int version,
            String userId,
            List<String> completedGameIds,
            Integer eligibleGameCount,
            Map<String, PlayerDashboardResponse.TechStat> techById,
            Map<String, Integer> factionWinsById) {}
}
