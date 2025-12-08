package ti4.listeners;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.Message;
import ti4.spring.context.SpringContext;
import ti4.spring.service.messagecache.BotMessageCacheService;

final class BotMessageCache {

    private BotMessageCache() {}

    static void cache(Message message) {
        SpringContext.getBean(BotMessageCacheService.class).cache(message);
    }

    @Nullable
    static String getContent(long messageId) {
        return SpringContext.getBean(BotMessageCacheService.class).getContent(messageId);
    }
}
