package ti4.spring.service.statistics.matchmaking;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.ListUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.UserEntity;

@AllArgsConstructor
@Service
public class QueueForGameService {

    private static final String CSV_SEPARATOR = ";";
    private static final int DEFAULT_MAX_QUEUE_TIME_HOURS = 8;

    private final MatchmakingQueueEntryRepository matchmakingQueueEntryRepository;

    @Transactional
    public void queueUser(
            String userId,
            String username,
            List<String> expansions,
            List<String> playerCounts,
            List<String> victoryPoints,
            List<String> restrictions,
            String maxQueueTime) {
        if (DatabasePersistenceGate.isDisabled()) return;
        matchmakingQueueEntryRepository.deleteByUserId(userId);

        MatchmakingQueueEntryEntity entry = new MatchmakingQueueEntryEntity();
        entry.setUser(new UserEntity(userId, username));
        entry.setQueuedAtUtc(LocalDateTime.now(ZoneOffset.UTC));
        entry.setExpansionsCsv(toCsv(expansions));
        entry.setPlayerCountsCsv(toCsv(playerCounts));
        entry.setVictoryPointsCsv(toCsv(victoryPoints));
        entry.setRestrictionsCsv(toCsv(restrictions));
        entry.setMaxQueueTimeHours(parseHours(maxQueueTime));

        matchmakingQueueEntryRepository.save(entry);
    }

    public boolean isQueueingDisabled() {
        return DatabasePersistenceGate.isDisabled();
    }

    public boolean isUserQueued(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return matchmakingQueueEntryRepository.existsByUserId(userId);
    }

    @Transactional
    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return matchmakingQueueEntryRepository.deleteByUserId(userId);
    }

    private static int parseHours(String maxQueueTime) {
        if (maxQueueTime == null) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        StringBuilder hours = new StringBuilder();
        for (char c : maxQueueTime.trim().toCharArray()) {
            if (Character.isDigit(c)) hours.append(c);
            else break;
        }
        if (hours.isEmpty()) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        return Integer.parseInt(hours.toString());
    }

    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        List<MatchmakingQueueEntryEntity> entries = matchmakingQueueEntryRepository.findAllByOrderByQueuedAtUtcAsc();
        List<MatchmakingQueueEntryEntity> candidates =
                cleanAndRemoveExpiredEntries(entries, LocalDateTime.now(ZoneOffset.UTC));

        List<List<MatchmakingQueueEntryEntity>> gamesToCreate = new ArrayList<>();
        Set<MatchmakingQueueEntryEntity> playersAddedToGames = new HashSet<>();

        for (String playerCountOption : MatchmakingOptions.getPlayerCountOptionsDescending()) {
            for (String victoryPointGoalOption : MatchmakingOptions.getVictoryPointOptionsDescending()) {
                for (String expansionOption : MatchmakingOptions.getShuffledExpansionsWithBaseIncluded()) {
                    for (String pace : MatchmakingOptions.getPaceRestrictions()) {
                        for (Predicate<String> tiglPredicate : MatchmakingOptions.getTiglRestrictionPredicates()) {
                            // TODO: Add similar player skill predicate and active hours predicate
                            matchAndCollect(
                                    candidates,
                                    playersAddedToGames,
                                    gamesToCreate,
                                    playerCountOption,
                                    victoryPointGoalOption,
                                    expansionOption,
                                    pace,
                                    tiglPredicate);
                        }
                    }
                }
            }
        }

        if (!playersAddedToGames.isEmpty()) {
            matchmakingQueueEntryRepository.deleteAllInBatch(playersAddedToGames);
        }

        // TODO: create a post in the forum #making-new-games, using the normal CreateGameButton flow
        // Perhaps pull that logic into a new service to be used in both places, along with
        // what we can pull from `handleMakingNewGamesThreadCreation`
    }

    private void matchAndCollect(
            List<MatchmakingQueueEntryEntity> candidates,
            Set<MatchmakingQueueEntryEntity> playersAddedToGames,
            List<List<MatchmakingQueueEntryEntity>> gamesToCreate,
            String playerCountOption,
            String victoryPointGoalOption,
            String expansionOption,
            String pace,
            Predicate<String> tiglPredicate) {
        int playerCount = Integer.parseInt(playerCountOption);

        List<MatchmakingQueueEntryEntity> eligible = candidates.stream()
                .filter(c -> !playersAddedToGames.contains(c))
                .filter(c -> c.getPlayerCounts().contains(playerCountOption))
                .filter(c -> c.getVictoryPoints().contains(victoryPointGoalOption))
                .filter(c -> c.getExpansions().contains(expansionOption))
                .filter(c -> c.getRestrictions().contains(pace))
                .filter(c -> tiglPredicate.test(c.getRestrictionsCsv()))
                .sorted(Comparator.comparing(MatchmakingQueueEntryEntity::getMaxQueueTimeHours)
                        .reversed())
                .toList();

        // TODO: further handle similar player skill and active hours

        ListUtils.partition(eligible, playerCount).stream()
                .filter(game -> game.size() == playerCount)
                .forEach(game -> {
                    gamesToCreate.add(game);
                    playersAddedToGames.addAll(game);
                });
    }

    @NonNull
    private List<MatchmakingQueueEntryEntity> cleanAndRemoveExpiredEntries(
            List<MatchmakingQueueEntryEntity> entries, LocalDateTime now) {
        List<MatchmakingQueueEntryEntity> expired = entries.stream()
                .filter(entry -> entry.getQueuedAtUtc()
                        .plusMinutes(entry.getMaxQueueTimeHours())
                        .isBefore(now))
                .toList();
        if (!expired.isEmpty()) {
            matchmakingQueueEntryRepository.deleteAllInBatch(expired);
            String expiryMessage =
                    "The matchmaking service wasn't able to find you a game in the time frame you selected. "
                            + "Queue again when ready and consider being open to additional game types or longer wait "
                            + "times.";
            for (MatchmakingQueueEntryEntity entry : expired) {
                User user = JdaService.jda.getUserById(entry.getUser().getId());
                if (user == null) continue;
                MessageHelper.sendMessageToUser(expiryMessage, user);
            }
        }
        return entries.stream().filter(entry -> !expired.contains(entry)).toList();
    }

    private static String toCsv(List<String> values) {
        return String.join(CSV_SEPARATOR, values);
    }

    public static QueueForGameService get() {
        return SpringContext.getBean(QueueForGameService.class);
    }
}
