package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.helpers.TIGLHelper;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;

@Service
@AllArgsConstructor
public class MatchmakingQueueSearchService {

    private final MatchmakingQueueSearchRepository repository;

    public static MatchmakingQueueSearchService get() {
        return SpringContext.getBean(MatchmakingQueueSearchService.class);
    }

    @Transactional
    public void register(String threadId, String messageId, PlayerSearchCriteria criteria) {
        if (DatabasePersistenceGate.isDisabled()) return;
        MatchmakingQueueSearch search = repository.findByThreadId(threadId).orElseGet(MatchmakingQueueSearch::new);
        search.setThreadId(threadId);
        search.setMessageId(messageId);
        search.setPlayerCounts(join(criteria.playerCounts()));
        search.setVictoryPointGoals(join(criteria.victoryPointGoals()));
        search.setExpansions(join(criteria.expansions()));
        search.setPaces(join(criteria.paces()));
        search.setRestrictions(join(criteria.restrictions()));
        search.setTigl(criteria.tigl());
        search.setTiglRanks(join(criteria.tiglRanks()));
        search.setCreatedAt(Instant.now());
        repository.save(search);
    }

    public boolean isRegistered(String threadId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return repository.findByThreadId(threadId).isPresent();
    }

    /**
     * @return whether a standing search existed for the thread and was removed
     */
    @Transactional
    public boolean remove(String threadId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        Optional<MatchmakingQueueSearch> found = repository.findByThreadId(threadId);
        if (found.isEmpty()) return false;
        repository.delete(found.get());
        return true;
    }

    public Optional<String> findJoinBlocker(String threadId, String joiningUserId, List<String> existingMemberIds) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.empty();
        return repository
                .findByThreadId(threadId)
                .flatMap(search ->
                        QueuedGameJoinValidator.findJoinBlocker(toCriteria(search), joiningUserId, existingMemberIds));
    }

    @Transactional
    public void updateForRoster(String threadId, List<String> memberIds) {
        if (DatabasePersistenceGate.isDisabled()) return;
        Optional<MatchmakingQueueSearch> found = repository.findByThreadId(threadId);
        if (found.isEmpty()) return;

        MatchmakingQueueSearch search = found.get();
        List<String> queuedCounts = split(search.getPlayerCounts());
        List<String> reachableCounts = queuedCounts.stream()
                .filter(count -> Integer.parseInt(count) >= memberIds.size())
                .toList();
        if (reachableCounts.isEmpty() && !queuedCounts.isEmpty()) {
            repository.delete(search);
            return;
        }
        search.setPlayerCounts(join(reachableCounts));
        if (search.isTigl()) {
            List<String> ranks = narrowTiglRanks(split(search.getTiglRanks()), memberIds);
            if (!ranks.isEmpty()) {
                search.setTiglRanks(join(ranks));
            }
        }
        repository.save(search);
    }

    private static List<String> narrowTiglRanks(List<String> ranks, List<String> memberIds) {
        Guild guild = JdaService.guildPrimary;
        if (guild == null) return ranks;
        List<User> users = memberIds.stream()
                .map(guild::getMemberById)
                .filter(Objects::nonNull)
                .map(Member::getUser)
                .toList();
        return TIGLHelper.filterStandardTiglRankOptionsAtOrBelow(users, ranks);
    }

    public void search() {
        if (DatabasePersistenceGate.isDisabled()) return;
        Guild guild = JdaService.guildPrimary;
        if (guild == null) return;

        for (MatchmakingQueueSearch search : repository.findAll()) {
            String threadId = search.getThreadId();
            ThreadChannel thread = guild.getThreadChannelById(threadId);
            if (thread == null || thread.isArchived() || thread.isLocked()) {
                // Thread is gone or closed (e.g. the game launched) - stop searching for it.
                repository.deleteByThreadId(threadId);
                continue;
            }
            PlayerSearchCriteria criteria = toCriteria(search);
            thread.retrieveMessageById(search.getMessageId())
                    .queue(
                            message -> CreateGameButtonHandler.addPlayersFromQueueSearch(guild, message, criteria),
                            // Only drop the record if the message is truly gone; ignore transient REST failures.
                            new ErrorHandler()
                                    .handle(
                                            ErrorResponse.UNKNOWN_MESSAGE,
                                            failure -> repository.deleteByThreadId(threadId)));
        }
    }

    private static PlayerSearchCriteria toCriteria(MatchmakingQueueSearch search) {
        return new PlayerSearchCriteria(
                split(search.getPlayerCounts()),
                split(search.getVictoryPointGoals()),
                split(search.getExpansions()),
                split(search.getPaces()),
                split(search.getRestrictions()),
                search.isTigl(),
                split(search.getTiglRanks()));
    }

    private static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }
}
