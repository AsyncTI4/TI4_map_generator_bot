package ti4.listeners;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.Message;
import ti4.spring.context.SpringContext;

public class BotMessageCache {

    private BotMessageCache() {}

    public static void cache(Message message) {
        SpringContext.getBean(BotMessageCacheService.class).cache(message);
    }

    @Nullable
    public static String getContent(long messageId) {
        return SpringContext.getBean(BotMessageCacheService.class).getContent(messageId);
    }
}
