package ti4.message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.messages.MessageSearchResponse;
import net.dv8tion.jda.api.requests.restaction.MessageSearchAction;

@UtilityClass
public class MessageSearchService {

    private static final int MAX_INDEXING_RETRIES = 3;

    /**
     * Finds the most recent messages a user posted in a channel, newest first.
     * <p>
     * Discord serves these from a search index rather than from channel history, so messages posted in the last few
     * seconds may not be included yet, and the whole guild may be unindexed until the first search warms it up.
     */
    public static CompletableFuture<List<Message>> findMessagesByAuthor(
            GuildMessageChannel channel, UserSnowflake author, int limit) {
        MessageSearchAction search = channel.getGuild()
                .searchMessages()
                .channels(channel)
                .authors(author)
                .sortBy(MessageSearchAction.SortType.TIMESTAMP)
                .sortOrder(MessageSearchAction.SortOrder.DESC)
                .includeNsfw(true)
                .limit(MessageSearchAction.MAX_LIMIT);
        return collectPages(search, limit, new ArrayList<>(), MAX_INDEXING_RETRIES);
    }

    private static CompletableFuture<List<Message>> collectPages(
            MessageSearchAction search, int limit, List<Message> found, int indexingRetriesLeft) {
        return search.offset(found.size()).submit().thenCompose(response -> {
            if (response.isNotReady()) {
                if (indexingRetriesLeft <= 0) return CompletableFuture.completedFuture(found);
                return retryOnceIndexed(search, limit, found, indexingRetriesLeft, response.asNotReady());
            }

            List<Message> page = response.asResults().getMessages();
            page.stream().limit(limit - found.size()).forEach(found::add);
            boolean isLastPage = page.size() < MessageSearchAction.MAX_LIMIT;
            if (isLastPage || found.size() >= limit || found.size() > MessageSearchAction.MAX_OFFSET) {
                return CompletableFuture.completedFuture(found);
            }
            return collectPages(search, limit, found, indexingRetriesLeft);
        });
    }

    private static CompletableFuture<List<Message>> retryOnceIndexed(
            MessageSearchAction search,
            int limit,
            List<Message> found,
            int indexingRetriesLeft,
            MessageSearchResponse.NotReady notReady) {
        Executor afterIndexing =
                CompletableFuture.delayedExecutor(notReady.getRetryAfter().toMillis(), TimeUnit.MILLISECONDS);
        return CompletableFuture.runAsync(() -> {}, afterIndexing)
                .thenCompose(ignored -> collectPages(search, limit, found, indexingRetriesLeft - 1));
    }
}
