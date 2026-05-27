package ti4.spring.service.statistics.matchmaking;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;

@Service
public class QueueForGameService {

    private static final String CSV_SEPARATOR = ";";
    private static final Map<String, Integer> MAX_QUEUE_TIME_TO_MINUTES = Map.of(
            "1 hour", 60,
            "4 hours", 240,
            "8 hours", 480,
            "24 hours", 1440,
            "48 hours", 2880,
            "1 week", 10_080);

    private final MatchmakingQueueEntryRepository repository;

    public QueueForGameService(MatchmakingQueueEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void queueUser(
            String userId,
            String username,
            List<String> expansions,
            List<String> playerCounts,
            List<String> victoryPoints,
            List<String> restrictions,
            String maxQueueTime) {
        if (DatabasePersistenceGate.isDisabled()) {
            return;
        }
        repository.deleteByUserId(userId);

        MatchmakingQueueEntryEntity entry = new MatchmakingQueueEntryEntity();
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setQueuedAtUtc(LocalDateTime.now(ZoneOffset.UTC));
        entry.setExpansionsCsv(toCsv(expansions));
        entry.setPlayerCountsCsv(toCsv(playerCounts));
        entry.setVictoryPointsCsv(toCsv(victoryPoints));
        entry.setRestrictionsCsv(toCsv(restrictions));
        entry.setMaxQueueTimeMinutes(MAX_QUEUE_TIME_TO_MINUTES.getOrDefault(maxQueueTime, 480));

        repository.save(entry);
    }

    @Transactional
    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) {
            return;
        }
        List<MatchmakingQueueEntryEntity> entries = repository.findAllByOrderByQueuedAtUtcAsc();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<MatchmakingQueueEntryEntity> expired = entries.stream()
                .filter(entry -> entry.getQueuedAtUtc()
                        .plusMinutes(entry.getMaxQueueTimeMinutes())
                        .isBefore(now))
                .toList();
        if (!expired.isEmpty()) {
            repository.deleteAllInBatch(expired);
        }

        List<MatchmakingQueueEntryEntity> candidates =
                entries.stream().filter(entry -> !expired.contains(entry)).toList();
        Set<Long> matchedIds = new LinkedHashSet<>();

        for (MatchmakingQueueEntryEntity seed : candidates) {
            if (matchedIds.contains(seed.getId())) continue;

            for (int targetCount : parsePlayerCounts(seed)) {
                List<MatchmakingQueueEntryEntity> compatible = candidates.stream()
                        .filter(candidate -> !matchedIds.contains(candidate.getId()))
                        .filter(candidate -> supportsPlayerCount(candidate, targetCount))
                        .filter(candidate -> areCompatible(seed, candidate))
                        .toList();

                List<MatchmakingQueueEntryEntity> match = buildCompatibleGroup(compatible, targetCount);
                if (match.size() == targetCount) {
                    matchedIds.addAll(match.stream()
                            .map(MatchmakingQueueEntryEntity::getId)
                            .toList());
                    // TODO handle this later.
                    BotLogger.logCron("QueueForGameService matched " + targetCount + " players for a game.");
                    break;
                }
            }
        }

        if (!matchedIds.isEmpty()) {
            repository.deleteAllByIdInBatch(matchedIds);
        }
    }

    private static List<MatchmakingQueueEntryEntity> buildCompatibleGroup(
            List<MatchmakingQueueEntryEntity> candidates, int targetCount) {
        List<MatchmakingQueueEntryEntity> selected = new ArrayList<>();
        for (MatchmakingQueueEntryEntity candidate : candidates) {
            boolean fitsAll = selected.stream().allMatch(existing -> areCompatible(existing, candidate));
            if (!fitsAll) continue;
            selected.add(candidate);
            if (selected.size() == targetCount) {
                return selected;
            }
        }
        return List.of();
    }

    private static boolean supportsPlayerCount(MatchmakingQueueEntryEntity entry, int targetCount) {
        return parsePlayerCounts(entry).contains(targetCount);
    }

    private static List<Integer> parsePlayerCounts(MatchmakingQueueEntryEntity entry) {
        return parseCsv(entry.getPlayerCountsCsv()).stream()
                .map(QueueForGameService::tryParseInt)
                .filter(value -> value > 0)
                .toList();
    }

    private static int tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean areCompatible(MatchmakingQueueEntryEntity a, MatchmakingQueueEntryEntity b) {
        return overlaps(parseCsv(a.getExpansionsCsv()), parseCsv(b.getExpansionsCsv()))
                && overlaps(parseCsv(a.getVictoryPointsCsv()), parseCsv(b.getVictoryPointsCsv()))
                && overlaps(parseCsv(a.getRestrictionsCsv()), parseCsv(b.getRestrictionsCsv()));
    }

    private static boolean overlaps(List<String> a, List<String> b) {
        Set<String> aSet = new LinkedHashSet<>(a);
        for (String value : b) {
            if (aSet.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(CSV_SEPARATOR)).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String toCsv(List<String> values) {
        return values.stream().collect(Collectors.joining(CSV_SEPARATOR));
    }
}
