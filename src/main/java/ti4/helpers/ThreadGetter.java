package ti4.helpers;

import javax.annotation.Nonnull;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.message.BotLogger;

@UtilityClass
public class ThreadGetter {

    /**
     * @return {@link ThreadChannel} with the given name in the given channel. WARNING: Uses two RestAction.complete() to check archived threads and another to create the thread
     */
    @Nonnull
    public static ThreadChannel getThreadInChannel(@Nonnull TextChannel channel, @Nonnull String threadName) {
        return getThreadInChannel(channel, threadName, true, false);
    }

    /**
     * @return {@link ThreadChannel} with the given name in the given channel. NULL if thead doesn't exist. WARNING: Uses one RestAction.complete() to check archived threads, and another if createIfDoesntExist is true to create the thread
     */
    public static ThreadChannel getThreadInChannel(@Nonnull TextChannel channel, @Nonnull String threadName, boolean createIfDoesntExist, boolean createAsPrivate) {
        // ATTEMPT TO FIND BY NAME
        try {
            // SEARCH FOR EXISTING OPEN THREAD
            for (ThreadChannel threadChannel_ : channel.getThreadChannels()) {
                if (threadChannel_.getName().equals(threadName)) {
                    return threadChannel_;
                }
            }

            // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
            for (ThreadChannel threadChannel_ : channel.retrieveArchivedPrivateThreadChannels().complete()) {
                if (threadChannel_.getName().equals(threadName)) {
                    return threadChannel_;
                }
            }
        } catch (Exception e) {
            BotLogger.log("Could not find existing Cards Info thread using name: " + threadName, e);
        }
        if (createIfDoesntExist) {
            return createNewThreadChannel(channel, threadName, createAsPrivate);
        }
        return null;
    }

    @Nonnull
    private static ThreadChannel createNewThreadChannel(@Nonnull TextChannel channel, @Nonnull String threadName, boolean createAsPrivate) {
        ThreadChannelAction threadAction = channel.createThreadChannel(threadName, createAsPrivate);
        if (createAsPrivate) {
            threadAction.setInvitable(false);
        }
        return threadAction.complete();
    }
}
