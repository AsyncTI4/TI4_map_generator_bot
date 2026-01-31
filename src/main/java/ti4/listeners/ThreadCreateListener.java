package ti4.listeners;

import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.ThreadArchiveHelper;
import ti4.message.logging.BotLogger;

public class ThreadCreateListener extends ListenerAdapter {

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!event.getChannelType().isThread()) return;

        try {
            ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
        } catch (Exception e) {
            BotLogger.error("Failed to check thread limit and archive on thread creation", e);
        }
    }
}
