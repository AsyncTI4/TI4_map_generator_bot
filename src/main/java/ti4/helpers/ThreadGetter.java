package ti4.helpers;

import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.message.logging.BotLogger;

@UtilityClass
public class ThreadGetter {

    public static void getThreadInChannel(
            @Nonnull TextChannel channel, @Nonnull String threadName, Consumer<ThreadChannel> consumer) {
        getThreadInChannel(channel, threadName, true, false, consumer);
    }

    public static void getThreadInChannel(
            @Nonnull TextChannel channel,
            @Nonnull String threadName,
            boolean createIfDoesntExist,
            boolean createAsPrivate,
            Consumer<ThreadChannel> consumer) {
        // ATTEMPT TO FIND BY NAME
        try {
            // SEARCH FOR EXISTING OPEN THREAD
            channel.getThreadChannels().stream()
                    .filter(threadChannel -> threadChannel.getName().equals(threadName))
                    .findFirst()
                    .ifPresentOrElse(
                            consumer,
                            () -> searchForArchivedThreadOrCreateNew(
                                    channel, threadName, createIfDoesntExist, createAsPrivate, consumer));
        } catch (Exception e) {
            BotLogger.error(
                    String.format(
                            "Could not find existing thread in channel %s using name: %s",
                            channel.getJumpUrl(), threadName),
                    e);
        }
    }

    private static void searchForArchivedThreadOrCreateNew(
            @Nonnull TextChannel channel,
            @Nonnull String threadName,
            boolean createIfDoesntExist,
            boolean createAsPrivate,
            @Nonnull Consumer<ThreadChannel> consumer) {
        // First: Check private archived threads
        channel.retrieveArchivedPrivateThreadChannels().queue(privateThreads -> {
            Optional<ThreadChannel> privateMatch = privateThreads.stream()
                    .filter(thread -> thread.getName().equals(threadName))
                    .findFirst();

            if (privateMatch.isPresent()) {
                reopenIfNeededOrPass(
                        privateMatch.get(), channel, threadName, createIfDoesntExist, createAsPrivate, consumer);
            } else {
                // Then: Check public archived threads
                channel.retrieveArchivedPublicThreadChannels().queue(publicThreads -> {
                    Optional<ThreadChannel> publicMatch = publicThreads.stream()
                            .filter(thread -> thread.getName().equals(threadName))
                            .findFirst();

                    if (publicMatch.isPresent()) {
                        reopenIfNeededOrPass(
                                publicMatch.get(), channel, threadName, createIfDoesntExist, createAsPrivate, consumer);
                    } else if (createIfDoesntExist) {
                        createNewThreadChannel(channel, threadName, createAsPrivate, consumer);
                    }
                });
            }
        });
    }

    private static void reopenIfNeededOrPass(
            @Nonnull ThreadChannel thread,
            @Nonnull TextChannel channel,
            String threadName,
            boolean createIfDoesntExist,
            boolean createAsPrivate,
            @Nonnull Consumer<ThreadChannel> consumer) {
        if (thread.isArchived()) {
            thread.getManager().setArchived(false).queue(success -> consumer.accept(thread), error -> {
                BotLogger.error(
                        String.format(
                                "Could not find existing thread in channel %s using name: %s",
                                channel.getJumpUrl(), threadName),
                        error);
                if (createIfDoesntExist) {
                    createNewThreadChannel(channel, threadName, createAsPrivate, consumer);
                }
            });
        } else {
            consumer.accept(thread);
        }
    }

    private static void createNewThreadChannel(
            @Nonnull TextChannel channel,
            @Nonnull String threadName,
            boolean createAsPrivate,
            @Nonnull Consumer<ThreadChannel> consumer) {
        ThreadChannelAction threadAction = channel.createThreadChannel(threadName, createAsPrivate);
        if (createAsPrivate) {
            threadAction = threadAction.setInvitable(false);
        }
        threadAction.queue(consumer);
    }
}
