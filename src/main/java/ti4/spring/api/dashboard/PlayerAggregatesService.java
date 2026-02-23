package ti4.spring.api.dashboard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.executors.ExecutorServiceManager;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.model.TechnologyModel;
import ti4.spring.service.roundstats.GameRoundPlayerStats;
import ti4.spring.service.roundstats.GameRoundPlayerStatsRepository;
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

    private static final int CURRENT_AGGREGATES_VERSION = 3;
    private static final int FAILED_AGGREGATES_VERSION = -1;
    private static final String HASH_DELIMITER = "\u001F";
    private static final JsonMapper mapper = ti4.json.JsonMapperManager.basic();

    private static final double AGGRESSION_WEIGHT_COMBATS = 0.45;
    private static final double AGGRESSION_WEIGHT_STOLEN = 0.35;
    private static final double AGGRESSION_WEIGHT_TACTICALS = 0.20;

    private final PlayerAggregatesCacheRepository repository;
    private final GameRoundPlayerStatsRepository roundStatsRepository;

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

        int completedCount = context.completedGameIds().size();
        int eligibleGameCount = Optional.ofNullable(stored.eligibleGameCount()).orElse(0);
        PlayerDashboardResponse.TechStats techStats = new PlayerDashboardResponse.TechStats(
                Optional.ofNullable(stored.techById()).orElse(Map.of()));
        PlayerDashboardResponse.FactionWinStats factionWinStats = new PlayerDashboardResponse.FactionWinStats(
                Optional.ofNullable(stored.factionWinsById()).orElse(Map.of()));

        return new PlayerDashboardResponse.PlayerAggregates(
                true,
                context.completedGamesHash(),
                completedCount,
                eligibleGameCount,
                CURRENT_AGGREGATES_VERSION,
                context.cached().getComputedAtEpochMs(),
                context.completedGameIds(),
                techStats,
                factionWinStats,
                Optional.ofNullable(stored.strategyCardStats()).orElse(emptyStrategyCardStats(completedCount, 0)),
                Optional.ofNullable(stored.combatProfile()).orElse(emptyCombatProfile(completedCount, 0)),
                Optional.ofNullable(stored.economyProfile())
                        .orElse(new PlayerDashboardResponse.EconomyProfile(0, 0, completedCount)),
                Optional.ofNullable(stored.factionTechSynergy())
                        .orElse(new PlayerDashboardResponse.FactionTechSynergy(Map.of())),
                Optional.ofNullable(stored.speakerImpact()).orElse(emptySpeakerImpact()),
                Optional.ofNullable(stored.aggressionProfile()).orElse(emptyAggressionProfile(completedCount, 0)));
    }

    private static boolean isStoredAggregateUsable(StoredAggregates stored) {
        return stored.version() == CURRENT_AGGREGATES_VERSION
                && stored.techById() != null
                && stored.eligibleGameCount() != null
                && stored.factionWinsById() != null
                && stored.strategyCardStats() != null
                && stored.combatProfile() != null
                && stored.economyProfile() != null
                && stored.factionTechSynergy() != null
                && stored.speakerImpact() != null
                && stored.aggressionProfile() != null;
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
                new PlayerDashboardResponse.FactionWinStats(Map.of()),
                emptyStrategyCardStats(completedGameCount, 0),
                emptyCombatProfile(completedGameCount, 0),
                new PlayerDashboardResponse.EconomyProfile(0, 0, completedGameCount),
                new PlayerDashboardResponse.FactionTechSynergy(Map.of()),
                emptySpeakerImpact(),
                emptyAggressionProfile(completedGameCount, 0));
    }

    private static PlayerDashboardResponse.StrategyCardStats emptyStrategyCardStats(
            int completedGamesConsidered, int gamesWithRoundStats) {
        return new PlayerDashboardResponse.StrategyCardStats(
                Map.of(),
                new PlayerDashboardResponse.StrategyCardStatsMeta(completedGamesConsidered, gamesWithRoundStats));
    }

    private static PlayerDashboardResponse.CombatProfile emptyCombatProfile(
            int completedGamesConsidered, int gamesWithRoundStats) {
        return new PlayerDashboardResponse.CombatProfile(
                new PlayerDashboardResponse.CombatTotals(0, 0, 0, 0, 0),
                new PlayerDashboardResponse.CombatAverages(0, 0, 0, 0, 0),
                new PlayerDashboardResponse.Coverage(completedGamesConsidered, gamesWithRoundStats));
    }

    private static PlayerDashboardResponse.SpeakerImpact emptySpeakerImpact() {
        return new PlayerDashboardResponse.SpeakerImpact(
                new PlayerDashboardResponse.SpeakerBucket(0, 0, 0),
                new PlayerDashboardResponse.SpeakerBucket(0, 0, 0),
                0);
    }

    private static PlayerDashboardResponse.AggressionProfile emptyAggressionProfile(
            int completedGamesConsidered, int gamesWithRoundStats) {
        return new PlayerDashboardResponse.AggressionProfile(
                new PlayerDashboardResponse.AggressionWeights(
                        AGGRESSION_WEIGHT_COMBATS, AGGRESSION_WEIGHT_STOLEN, AGGRESSION_WEIGHT_TACTICALS),
                Map.of(),
                new PlayerDashboardResponse.AggressionSummary(0, 0, 0, 0, null),
                new PlayerDashboardResponse.Coverage(completedGamesConsidered, gamesWithRoundStats));
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
                    computed.factionWinsById(),
                    computed.strategyCardStats(),
                    computed.combatProfile(),
                    computed.economyProfile(),
                    computed.factionTechSynergy(),
                    computed.speakerImpact(),
                    computed.aggressionProfile());

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
     * Computes all aggregate views from final game state and round stats.
     */
    private ComputedAggregates computeAggregates(String userId, Collection<String> completedGameIds) {
        List<GameAggregateSnapshot> snapshots = loadPlayerSnapshotsPerGame(userId, completedGameIds);
        Map<String, List<GameRoundPlayerStats>> roundStatsByGame = loadRoundStatsByGame(userId, snapshots);

        TechCountAccumulator techCounts = accumulateEligibleCounts(snapshots);
        Map<String, PlayerDashboardResponse.TechStat> byTech = toTechStatMap(techCounts);
        Map<String, Integer> factionWinsById = countFactionWins(snapshots);

        PlayerDashboardResponse.StrategyCardStats strategyCardStats =
                computeStrategyCardStats(snapshots, roundStatsByGame);
        PlayerDashboardResponse.CombatProfile combatProfile = computeCombatProfile(snapshots, roundStatsByGame);
        PlayerDashboardResponse.EconomyProfile economyProfile = computeEconomyProfile(snapshots);
        PlayerDashboardResponse.FactionTechSynergy factionTechSynergy = computeFactionTechSynergy(snapshots);
        PlayerDashboardResponse.SpeakerImpact speakerImpact = computeSpeakerImpact(snapshots);
        PlayerDashboardResponse.AggressionProfile aggressionProfile =
                computeAggressionProfile(snapshots, roundStatsByGame);

        return new ComputedAggregates(
                techCounts.eligibleGameCount(),
                byTech,
                factionWinsById,
                strategyCardStats,
                combatProfile,
                economyProfile,
                factionTechSynergy,
                speakerImpact,
                aggressionProfile);
    }

    private Map<String, List<GameRoundPlayerStats>> loadRoundStatsByGame(
            String userId, List<GameAggregateSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return Map.of();
        }
        List<String> gameIds =
                snapshots.stream().map(GameAggregateSnapshot::gameId).toList();
        List<GameRoundPlayerStats> rows = roundStatsRepository.findByUserDiscordIdAndGameIdIn(userId, gameIds);
        Map<String, List<GameRoundPlayerStats>> byGame = new HashMap<>();
        for (GameRoundPlayerStats row : rows) {
            if (row == null || row.getGameId() == null) {
                continue;
            }
            byGame.computeIfAbsent(row.getGameId(), ignored -> new ArrayList<>())
                    .add(row);
        }
        return byGame;
    }

    private static PlayerDashboardResponse.StrategyCardStats computeStrategyCardStats(
            List<GameAggregateSnapshot> snapshots, Map<String, List<GameRoundPlayerStats>> roundStatsByGame) {
        Map<Integer, Integer> totalPicksBySc = new HashMap<>();
        Map<Integer, Set<String>> gamesPickedBySc = new HashMap<>();
        Map<Integer, Integer> winsInGamesPickedBySc = new HashMap<>();

        int gamesWithRoundStats = 0;
        for (GameAggregateSnapshot snapshot : snapshots) {
            List<GameRoundPlayerStats> rows = roundStatsByGame.getOrDefault(snapshot.gameId(), List.of());
            if (!rows.isEmpty()) {
                gamesWithRoundStats++;
            }

            Set<Integer> scSeenInGame = new HashSet<>();
            for (GameRoundPlayerStats row : rows) {
                for (Integer sc : parseScPicks(row.getScPicks())) {
                    totalPicksBySc.merge(sc, 1, Integer::sum);
                    scSeenInGame.add(sc);
                }
            }

            for (Integer sc : scSeenInGame) {
                gamesPickedBySc.computeIfAbsent(sc, ignored -> new HashSet<>()).add(snapshot.gameId());
                if (snapshot.playerWon()) {
                    winsInGamesPickedBySc.merge(sc, 1, Integer::sum);
                }
            }
        }

        Set<Integer> scKeys = new HashSet<>();
        scKeys.addAll(totalPicksBySc.keySet());
        scKeys.addAll(gamesPickedBySc.keySet());

        Map<Integer, PlayerDashboardResponse.StrategyCardStat> bySc = scKeys.stream()
                .sorted()
                .collect(java.util.stream.Collectors.toMap(
                        sc -> sc,
                        sc -> {
                            int totalPicks = totalPicksBySc.getOrDefault(sc, 0);
                            int gamesPicked =
                                    gamesPickedBySc.getOrDefault(sc, Set.of()).size();
                            int winsInGamesPicked = winsInGamesPickedBySc.getOrDefault(sc, 0);
                            double winRate = gamesPicked == 0 ? 0.0 : (winsInGamesPicked * 100.0) / gamesPicked;
                            return new PlayerDashboardResponse.StrategyCardStat(
                                    totalPicks, gamesPicked, winsInGamesPicked, winRate);
                        },
                        (a, b) -> a,
                        LinkedHashMap::new));

        return new PlayerDashboardResponse.StrategyCardStats(
                bySc, new PlayerDashboardResponse.StrategyCardStatsMeta(snapshots.size(), gamesWithRoundStats));
    }

    private static List<Integer> parseScPicks(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        List<Integer> picks = new ArrayList<>();
        for (String token : csv.split(",")) {
            if (StringUtils.isBlank(token)) {
                continue;
            }
            try {
                picks.add(Integer.parseInt(token.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed SC values in historical rows.
            }
        }
        return picks;
    }

    private static PlayerDashboardResponse.CombatProfile computeCombatProfile(
            List<GameAggregateSnapshot> snapshots, Map<String, List<GameRoundPlayerStats>> roundStatsByGame) {
        long combatsInitiated = 0;
        long tacticalsWithCombat = 0;
        long planetsTaken = 0;
        long planetsStolen = 0;
        long diceRolled = 0;
        int gamesWithRoundStats = 0;

        for (GameAggregateSnapshot snapshot : snapshots) {
            List<GameRoundPlayerStats> rows = roundStatsByGame.getOrDefault(snapshot.gameId(), List.of());
            if (!rows.isEmpty()) {
                gamesWithRoundStats++;
            }
            for (GameRoundPlayerStats row : rows) {
                combatsInitiated += zeroIfNull(row.getCombatsInitiated());
                tacticalsWithCombat += zeroIfNull(row.getTacticalsWithCombat());
                planetsTaken += zeroIfNull(row.getPlanetsTaken());
                planetsStolen += zeroIfNull(row.getPlanetsStolen());
                diceRolled += zeroIfNull(row.getDiceRolled());
            }
        }

        int completedGamesConsidered = snapshots.size();
        double denom = completedGamesConsidered == 0 ? 1.0 : completedGamesConsidered;

        return new PlayerDashboardResponse.CombatProfile(
                new PlayerDashboardResponse.CombatTotals(
                        safeToInt(combatsInitiated),
                        safeToInt(tacticalsWithCombat),
                        safeToInt(planetsTaken),
                        safeToInt(planetsStolen),
                        safeToInt(diceRolled)),
                new PlayerDashboardResponse.CombatAverages(
                        combatsInitiated / denom,
                        tacticalsWithCombat / denom,
                        planetsTaken / denom,
                        planetsStolen / denom,
                        diceRolled / denom),
                new PlayerDashboardResponse.Coverage(completedGamesConsidered, gamesWithRoundStats));
    }

    private static PlayerDashboardResponse.EconomyProfile computeEconomyProfile(List<GameAggregateSnapshot> snapshots) {
        double totalExpensesSum = snapshots.stream()
                .mapToDouble(GameAggregateSnapshot::totalExpenses)
                .sum();
        int completedGamesConsidered = snapshots.size();
        double avgTotalExpenses = completedGamesConsidered == 0 ? 0 : totalExpensesSum / completedGamesConsidered;
        return new PlayerDashboardResponse.EconomyProfile(totalExpensesSum, avgTotalExpenses, completedGamesConsidered);
    }

    private static PlayerDashboardResponse.FactionTechSynergy computeFactionTechSynergy(
            List<GameAggregateSnapshot> snapshots) {
        Map<String, MutableFactionSynergy> accumulators = new HashMap<>();

        for (GameAggregateSnapshot snapshot : snapshots) {
            String factionId = snapshot.canonicalFactionId();
            if (StringUtils.isBlank(factionId)) {
                continue;
            }

            MutableFactionSynergy faction =
                    accumulators.computeIfAbsent(factionId, ignored -> new MutableFactionSynergy());
            faction.games++;
            if (snapshot.playerWon()) {
                faction.wins++;
            } else {
                faction.nonWins++;
            }

            for (String techId : snapshot.canonicalTechs()) {
                MutableFactionTech tech = faction.byTech.computeIfAbsent(techId, ignored -> new MutableFactionTech());
                tech.gamesWithTech++;
                if (snapshot.playerWon()) {
                    tech.winsWithTech++;
                } else {
                    tech.nonWinsWithTech++;
                }
            }
        }

        Map<String, PlayerDashboardResponse.FactionSynergyStat> byFaction = accumulators.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            MutableFactionSynergy faction = entry.getValue();
                            Map<String, PlayerDashboardResponse.FactionTechSynergyStat> byTech =
                                    faction.byTech.entrySet().stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .collect(java.util.stream.Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    techEntry -> {
                                                        MutableFactionTech tech = techEntry.getValue();
                                                        double winRate = tech.gamesWithTech == 0
                                                                ? 0.0
                                                                : (tech.winsWithTech * 100.0) / tech.gamesWithTech;
                                                        return new PlayerDashboardResponse.FactionTechSynergyStat(
                                                                tech.gamesWithTech,
                                                                tech.winsWithTech,
                                                                tech.nonWinsWithTech,
                                                                winRate);
                                                    },
                                                    (a, b) -> a,
                                                    LinkedHashMap::new));

                            return new PlayerDashboardResponse.FactionSynergyStat(
                                    faction.games, faction.wins, faction.nonWins, byTech);
                        },
                        (a, b) -> a,
                        LinkedHashMap::new));

        return new PlayerDashboardResponse.FactionTechSynergy(byFaction);
    }

    private static PlayerDashboardResponse.SpeakerImpact computeSpeakerImpact(List<GameAggregateSnapshot> snapshots) {
        int speakerGames = 0;
        int speakerWins = 0;
        int nonSpeakerGames = 0;
        int nonSpeakerWins = 0;

        for (GameAggregateSnapshot snapshot : snapshots) {
            if (snapshot.wasSpeaker()) {
                speakerGames++;
                if (snapshot.playerWon()) {
                    speakerWins++;
                }
            } else {
                nonSpeakerGames++;
                if (snapshot.playerWon()) {
                    nonSpeakerWins++;
                }
            }
        }

        double speakerWinRate = speakerGames == 0 ? 0.0 : (speakerWins * 100.0) / speakerGames;
        double nonSpeakerWinRate = nonSpeakerGames == 0 ? 0.0 : (nonSpeakerWins * 100.0) / nonSpeakerGames;

        return new PlayerDashboardResponse.SpeakerImpact(
                new PlayerDashboardResponse.SpeakerBucket(speakerGames, speakerWins, speakerWinRate),
                new PlayerDashboardResponse.SpeakerBucket(nonSpeakerGames, nonSpeakerWins, nonSpeakerWinRate),
                speakerWinRate - nonSpeakerWinRate);
    }

    private static PlayerDashboardResponse.AggressionProfile computeAggressionProfile(
            List<GameAggregateSnapshot> snapshots, Map<String, List<GameRoundPlayerStats>> roundStatsByGame) {
        Map<String, AggressionInput> byGameInput = new LinkedHashMap<>();
        int gamesWithRoundStats = 0;

        for (GameAggregateSnapshot snapshot : snapshots) {
            int combats = 0;
            int stolen = 0;
            int tacticals = 0;

            List<GameRoundPlayerStats> rows = roundStatsByGame.getOrDefault(snapshot.gameId(), List.of());
            if (!rows.isEmpty()) {
                gamesWithRoundStats++;
            }

            for (GameRoundPlayerStats row : rows) {
                combats += zeroIfNull(row.getCombatsInitiated());
                stolen += zeroIfNull(row.getPlanetsStolen());
                tacticals += zeroIfNull(row.getTacticalsWithCombat());
            }

            byGameInput.put(snapshot.gameId(), new AggressionInput(combats, stolen, tacticals));
        }

        Map<String, Double> byGameScore = new LinkedHashMap<>();
        if (gamesWithRoundStats < 2) {
            byGameInput.keySet().forEach(gameId -> byGameScore.put(gameId, 0.0));
            return new PlayerDashboardResponse.AggressionProfile(
                    new PlayerDashboardResponse.AggressionWeights(
                            AGGRESSION_WEIGHT_COMBATS, AGGRESSION_WEIGHT_STOLEN, AGGRESSION_WEIGHT_TACTICALS),
                    byGameScore,
                    new PlayerDashboardResponse.AggressionSummary(0, 0, 0, 0, null),
                    new PlayerDashboardResponse.Coverage(snapshots.size(), gamesWithRoundStats));
        }

        List<Double> combatsValues = byGameInput.values().stream()
                .map(i -> (double) i.combatsInitiated())
                .toList();
        List<Double> stolenValues = byGameInput.values().stream()
                .map(i -> (double) i.planetsStolen())
                .toList();
        List<Double> tacticalsValues = byGameInput.values().stream()
                .map(i -> (double) i.tacticalsWithCombat())
                .toList();

        double combatsMean = mean(combatsValues);
        double stolenMean = mean(stolenValues);
        double tacticalsMean = mean(tacticalsValues);

        double combatsStd = stdDev(combatsValues, combatsMean);
        double stolenStd = stdDev(stolenValues, stolenMean);
        double tacticalsStd = stdDev(tacticalsValues, tacticalsMean);

        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        String mostAggressiveGameId = null;

        for (Map.Entry<String, AggressionInput> entry : byGameInput.entrySet()) {
            AggressionInput input = entry.getValue();
            double combatsZ = zScore(input.combatsInitiated(), combatsMean, combatsStd);
            double stolenZ = zScore(input.planetsStolen(), stolenMean, stolenStd);
            double tacticalsZ = zScore(input.tacticalsWithCombat(), tacticalsMean, tacticalsStd);

            double score = (AGGRESSION_WEIGHT_COMBATS * combatsZ)
                    + (AGGRESSION_WEIGHT_STOLEN * stolenZ)
                    + (AGGRESSION_WEIGHT_TACTICALS * tacticalsZ);

            byGameScore.put(entry.getKey(), score);
            sum += score;
            if (score > max) {
                max = score;
                mostAggressiveGameId = entry.getKey();
            }
            if (score < min) {
                min = score;
            }
        }

        List<Double> scores = new ArrayList<>(byGameScore.values());
        double avg = scores.isEmpty() ? 0.0 : (sum / scores.size());
        double median = median(scores);

        return new PlayerDashboardResponse.AggressionProfile(
                new PlayerDashboardResponse.AggressionWeights(
                        AGGRESSION_WEIGHT_COMBATS, AGGRESSION_WEIGHT_STOLEN, AGGRESSION_WEIGHT_TACTICALS),
                byGameScore,
                new PlayerDashboardResponse.AggressionSummary(
                        avg,
                        median,
                        Double.isInfinite(max) ? 0 : max,
                        Double.isInfinite(min) ? 0 : min,
                        mostAggressiveGameId),
                new PlayerDashboardResponse.Coverage(snapshots.size(), gamesWithRoundStats));
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double stdDev(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        double variance = values.stream()
                .mapToDouble(v -> {
                    double delta = v - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static double zScore(double value, double mean, double stdDev) {
        if (stdDev == 0.0) {
            return 0.0;
        }
        return (value - mean) / stdDev;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }
        return sorted.get(middle);
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private static int safeToInt(long value) {
        if (value <= 0) {
            return 0;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
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
            boolean wasSpeaker = userId.equals(game.getSpeakerUserID());
            double totalExpenses = Math.max(player.getTotalExpenses(), 0);

            snapshots.add(new GameAggregateSnapshot(
                    gameId,
                    includeInTechAggregates,
                    includeInTechAggregates,
                    canonicalTechsForGame,
                    canonicalFactionId,
                    playerWon,
                    wasSpeaker,
                    totalExpenses));
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
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
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
                        LinkedHashMap::new));
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
            String gameId,
            boolean includeInTechAggregates,
            boolean eligibleForPercent,
            Set<String> canonicalTechs,
            String canonicalFactionId,
            boolean playerWon,
            boolean wasSpeaker,
            double totalExpenses) {}

    private record TechCountAccumulator(
            Map<String, Integer> gamesWithTech, Map<String, Integer> eligibleGamesWithTech, int eligibleGameCount) {}

    private record ComputedAggregates(
            int eligibleGameCount,
            Map<String, PlayerDashboardResponse.TechStat> byTech,
            Map<String, Integer> factionWinsById,
            PlayerDashboardResponse.StrategyCardStats strategyCardStats,
            PlayerDashboardResponse.CombatProfile combatProfile,
            PlayerDashboardResponse.EconomyProfile economyProfile,
            PlayerDashboardResponse.FactionTechSynergy factionTechSynergy,
            PlayerDashboardResponse.SpeakerImpact speakerImpact,
            PlayerDashboardResponse.AggressionProfile aggressionProfile) {}

    private record StoredAggregates(
            int version,
            String userId,
            List<String> completedGameIds,
            Integer eligibleGameCount,
            Map<String, PlayerDashboardResponse.TechStat> techById,
            Map<String, Integer> factionWinsById,
            PlayerDashboardResponse.StrategyCardStats strategyCardStats,
            PlayerDashboardResponse.CombatProfile combatProfile,
            PlayerDashboardResponse.EconomyProfile economyProfile,
            PlayerDashboardResponse.FactionTechSynergy factionTechSynergy,
            PlayerDashboardResponse.SpeakerImpact speakerImpact,
            PlayerDashboardResponse.AggressionProfile aggressionProfile) {}

    private static final class MutableFactionSynergy {
        private int games;
        private int wins;
        private int nonWins;
        private final Map<String, MutableFactionTech> byTech = new HashMap<>();
    }

    private static final class MutableFactionTech {
        private int gamesWithTech;
        private int winsWithTech;
        private int nonWinsWithTech;
    }

    private record AggressionInput(int combatsInitiated, int planetsStolen, int tacticalsWithCombat) {}
}
