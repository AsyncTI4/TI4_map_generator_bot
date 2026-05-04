package ti4.spring.api.image;

import static software.amazon.awssdk.utils.StringUtils.isBlank;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.logging.BotLogger;

@Service
@RequiredArgsConstructor
class GameAttachmentUrlRefreshService {

    private static final String CHRONICLES_CHANNEL_NAME = "the-pbd-chronicles";
    private static final String REFRESHED_MAP_IMAGES_THREAD_NAME = "refreshed-map-images";
    private static final long REFRESH_TIMEOUT_SECONDS = 120;

    private final GameImageService gameImageService;

    public Optional<String> refreshAttachmentUrl(String gameName) {
        if (isBlank(gameName) || !GameManager.isValid(gameName)) {
            return Optional.empty();
        }

        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null) {
            return Optional.empty();
        }

        Game game = managedGame.getGame();
        if (game == null || game.isFowMode()) {
            return Optional.empty();
        }

        TextChannel chroniclesChannel = getChroniclesChannel();
        if (chroniclesChannel == null) {
            return Optional.empty();
        }

        ThreadChannel threadChannel = getOrCreateRefreshThread(chroniclesChannel);
        if (threadChannel == null) {
            return Optional.empty();
        }

        CompletableFuture<String> attachmentUrlFuture = new CompletableFuture<>();
        // This render is started from an API request instead of a Discord interaction.
        MapRenderPipeline.queue(game, null, DisplayType.all, fileUpload -> {
            try {
                Message message = threadChannel.sendFiles(fileUpload).complete();
                if (message == null || message.getAttachments().isEmpty()) {
                    attachmentUrlFuture.completeExceptionally(
                            new IllegalStateException("Map refresh upload completed without an attachment"));
                    return;
                }

                gameImageService.saveDiscordMessage(gameName, message);
                attachmentUrlFuture.complete(message.getAttachments().getFirst().getUrl());
            } catch (Exception e) {
                attachmentUrlFuture.completeExceptionally(e);
            }
        });

        try {
            return Optional.ofNullable(attachmentUrlFuture.get(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            BotLogger.error("Timed out refreshing attachment URL for game " + game.getName(), e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BotLogger.error("Interrupted while refreshing attachment URL for game " + game.getName(), e);
            return Optional.empty();
        } catch (ExecutionException e) {
            BotLogger.error("Failed to refresh attachment URL for game " + game.getName(), e);
            return Optional.empty();
        }
    }

    private TextChannel getChroniclesChannel() {
        if (JdaService.guildPrimary == null) {
            BotLogger.error("Cannot refresh attachment URL because the primary guild is unavailable");
            return null;
        }
        return JdaService.guildPrimary.getTextChannelsByName(CHRONICLES_CHANNEL_NAME, true).stream()
                .findFirst()
                .orElse(null);
    }

    private ThreadChannel getOrCreateRefreshThread(TextChannel chroniclesChannel) {
        try {
            ThreadChannel existingThread = findThreadByName(chroniclesChannel.getThreadChannels());
            if (existingThread != null) {
                return existingThread;
            }

            existingThread = findThreadByName(
                    chroniclesChannel.retrieveArchivedPublicThreadChannels().complete());
            if (existingThread != null) {
                return reopenThread(existingThread);
            }

            existingThread = findThreadByName(
                    chroniclesChannel.retrieveArchivedPrivateThreadChannels().complete());
            if (existingThread != null) {
                return reopenThread(existingThread);
            }

            return chroniclesChannel
                    .createThreadChannel(REFRESHED_MAP_IMAGES_THREAD_NAME)
                    .complete();
        } catch (Exception e) {
            BotLogger.error("Failed to get or create refresh thread in " + chroniclesChannel.getJumpUrl(), e);
            return null;
        }
    }

    private ThreadChannel findThreadByName(List<ThreadChannel> threads) {
        return threads.stream()
                .filter(thread -> REFRESHED_MAP_IMAGES_THREAD_NAME.equals(thread.getName()))
                .findFirst()
                .orElse(null);
    }

    private ThreadChannel reopenThread(ThreadChannel threadChannel) {
        if (!threadChannel.isArchived()) {
            return threadChannel;
        }
        threadChannel.getManager().setArchived(false).complete();
        return threadChannel;
    }
}
