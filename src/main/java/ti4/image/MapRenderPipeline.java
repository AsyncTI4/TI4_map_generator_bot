package ti4.image;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.Nullable;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutionHistoryManager;
import ti4.helpers.DisplayType;
import ti4.helpers.TimedRunnable;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.settings.GlobalSettings;

@UtilityClass
public class MapRenderPipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 10;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    static {
        // this seems recommended everywhere I look
        ImageIO.setUseCache(false);
    }

    private static void render(RenderEvent renderEvent) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(
                "Render event task for " + renderEvent.game.getName(), EXECUTION_TIME_SECONDS_WARNING_THRESHOLD, () -> {
                    try (var mapGenerator =
                            new MapGenerator(renderEvent.game, renderEvent.displayType, renderEvent.event)) {
                        mapGenerator.draw();
                        if (renderEvent.uploadToDiscord) {
                            uploadToDiscord(mapGenerator, renderEvent.callback());
                        }
                        if (renderEvent.uploadToWebsite) {
                            mapGenerator.uploadToWebsite();
                        }
                    } catch (Exception e) {
                        BotLogger.error(new LogOrigin(renderEvent.event, renderEvent.game), "Failed to render event.", e);
                    }
                });

        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    private static void uploadToDiscord(MapGenerator mapGenerator, Consumer<FileUpload> callback) {
        try (var fileUpload = mapGenerator.createFileUpload()) {
            if (fileUpload != null && callback != null) {
                callback.accept(fileUpload);
            }
        } catch (IOException e) {
            BotLogger.error("Could not render images for " + mapGenerator.getGameName(), e);
        }
    }

    public static void renderToWebsiteOnly(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) {
            queue(game, event, null, null, false, true);
        }
    }

    public static void queue(
            Game game, @Nullable SlashCommandInteractionEvent event, @Nullable Consumer<FileUpload> callback) {
        queue(game, event, null, callback, true, true);
    }

    public static void queue(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            @Nullable Consumer<FileUpload> callback) {
        queue(game, event, displayType, callback, true, true);
    }

    private static void queue(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            @Nullable Consumer<FileUpload> callback,
            boolean uploadToDiscord,
            boolean uploadToWebsite) {
        if (game == null) {
            throw new IllegalArgumentException("game cannot be null in render pipeline");
        }
        render(new RenderEvent(game, event, displayType, callback, uploadToDiscord, uploadToWebsite));
    }

    public static boolean shutdown() {
        EXECUTOR_SERVICE.shutdownNow();
        try {
            return EXECUTOR_SERVICE.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            BotLogger.error("MapRenderPipeline shutdown interrupted.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    record RenderEvent(
            Game game,
            GenericInteractionCreateEvent event,
            DisplayType displayType,
            Consumer<FileUpload> callback,
            boolean uploadToDiscord,
            boolean uploadToWebsite) {}
}
