package ti4.helpers;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.message.BotLogger;

@UtilityClass
public class ThreadGetter {

    public static void getThreadInChannel(@Nonnull TextChannel channel, @Nonnull String threadName, Consumer<ThreadChannel> consumer) {
        getThreadInChannel(channel, threadName, true, false, consumer);
    }

    public static void getThreadInChannel(@Nonnull TextChannel channel, @Nonnull String threadName, boolean createIfDoesntExist, boolean createAsPrivate,
                                            Consumer<ThreadChannel> consumer) {
        // ATTEMPT TO FIND BY NAME
        try {
            // SEARCH FOR EXISTING OPEN THREAD
            channel.getThreadChannels().stream()
                .filter(threadChannel -> threadChannel.getName().equals(threadName))
                .findFirst()
                .ifPresentOrElse(consumer, () -> searchForArchivedThreadOrCreateNew(channel, threadName, createIfDoesntExist, createAsPrivate, consumer));
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(channel), "Could not find existing #cards-info thread using name: " + threadName, e);
        }

    }

    private static void searchForArchivedThreadOrCreateNew(@Nonnull TextChannel channel, @Nonnull String threadName, boolean createIfDoesntExist, boolean createAsPrivate,
                                                            @Nonnull Consumer<ThreadChannel> consumer) {
        channel.retrieveArchivedPrivateThreadChannels().queue(threadChannels ->
            threadChannels.stream()
                .filter(threadChannel -> threadChannel.getName().equals(threadName))
                .findFirst()
                .ifPresentOrElse(consumer, () -> {
                    if (createIfDoesntExist) {
                        createNewThreadChannel(channel, threadName, createAsPrivate, consumer);
                    }
                }));
    }

    private static void createNewThreadChannel(@Nonnull TextChannel channel, @Nonnull String threadName, boolean createAsPrivate,
                                                @Nonnull Consumer<ThreadChannel> consumer) {
        ThreadChannelAction threadAction = channel.createThreadChannel(threadName, createAsPrivate);
        if (createAsPrivate) {
            threadAction = threadAction.setInvitable(false);
        }
        threadAction.queue(consumer);
    }
}
