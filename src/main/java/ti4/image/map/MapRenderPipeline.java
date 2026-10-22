package ti4.image.map;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutionHistoryManager;
import ti4.executors.ExecutorUtility;
import ti4.executors.ShutdownResult;
import ti4.game.Game;
import ti4.game.persistence.GameSnapshots;
import ti4.helpers.DisplayType;
import ti4.helpers.TimedRunnable;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.settings.GlobalSettings;

@UtilityClass
public class MapRenderPipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 10;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(
            2, Thread.ofPlatform().name("ti4-map-renderer-", 0).factory());

    private static void render(RenderEvent renderEvent) {
        if (CircuitBreaker.isOpen()) {
            return;
        }

        Game liveGame = renderEvent.game;
        Game snapshot = snapshotOf(liveGame);
        if (renderEvent.uploadToDiscord) {
            liveGame.incrementMapImageGenerationCount();
        }
        liveGame.setTileDistances(new HashMap<>());
        var timedRunnable = new TimedRunnable(
                "Render event task for " + liveGame.getName(), EXECUTION_TIME_SECONDS_WARNING_THRESHOLD, () -> {
                    RenderTimings timings = RenderTimings.start();
                    try {
                        MapRenderResult result = MapRenderer.render(
                                new MapRenderRequest(
                                        snapshot, renderEvent.displayType, renderEvent.event, renderEvent.segment),
                                timings);
                        // TODO: the sent-warning markers land on the live game from this thread; they persist only if
                        // the handler saves later.
                        result.playerAreaWarnings().send(liveGame);
                        if (renderEvent.uploadToDiscord) {
                            uploadToDiscord(snapshot, result, timings, renderEvent.callback());
                        }
                        if (renderEvent.uploadToWebsite) {
                            MapUploads.toWebsite(snapshot, result, timings);
                        }
                    } catch (Exception e) {
                        BotLogger.error(new LogOrigin(snapshot), "Failed to render event.", e);
                    } finally {
                        timings.log(snapshot, renderEvent.event);
                    }
                });

        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    public static void queueImage(
            Game game, String label, Function<Game, FileUpload> image, Consumer<FileUpload> callback) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        Game snapshot = snapshotOf(game);
        var timedRunnable =
                new TimedRunnable(label + " for " + game.getName(), EXECUTION_TIME_SECONDS_WARNING_THRESHOLD, () -> {
                    try (FileUpload fileUpload = image.apply(snapshot)) {
                        callback.accept(fileUpload);
                    } catch (Exception e) {
                        BotLogger.error(new LogOrigin(game), "Failed to render " + label + ".", e);
                    }
                });
        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    private static Game snapshotOf(Game game) {
        try {
            return GameSnapshots.copy(game);
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(game), "Could not snapshot the game for rendering, rendering the live game.", e);
            return game;
        }
    }

    private static void uploadToDiscord(
            Game game, MapRenderResult result, RenderTimings timings, Consumer<FileUpload> callback) {
        try (var fileUpload = MapUploads.toDiscord(game, result, timings)) {
            if (fileUpload != null && callback != null) {
                callback.accept(fileUpload);
            }
        } catch (IOException e) {
            BotLogger.error("Could not render images for " + game.getName(), e);
        }
    }

    static void renderToWebsiteOnly(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(),
                Boolean.class,
                Boolean.FALSE)) {
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

    public static void queue(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            @Nullable String segment,
            @Nullable Consumer<FileUpload> callback) {
        queue(game, event, displayType, segment, callback, true, true);
    }

    public static void queueUnfoggedWithoutWebsiteUpload(
            Game game, @Nullable DisplayType displayType, Consumer<FileUpload> callback) {
        queue(game, null, displayType, null, callback, true, false);
    }

    private static void queue(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            @Nullable Consumer<FileUpload> callback,
            boolean uploadToDiscord,
            boolean uploadToWebsite) {
        queue(game, event, displayType, null, callback, uploadToDiscord, uploadToWebsite);
    }

    private static void queue(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            @Nullable String segment,
            @Nullable Consumer<FileUpload> callback,
            boolean uploadToDiscord,
            boolean uploadToWebsite) {
        if (game == null) {
            throw new IllegalArgumentException("game cannot be null in render pipeline");
        }
        render(new RenderEvent(game, event, displayType, segment, callback, uploadToDiscord, uploadToWebsite));
    }

    public static ShutdownResult shutdown() {
        return ExecutorUtility.shutdownAndAwaitTermination(
                EXECUTOR_SERVICE, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private record RenderEvent(
            Game game,
            GenericInteractionCreateEvent event,
            DisplayType displayType,
            String segment,
            Consumer<FileUpload> callback,
            boolean uploadToDiscord,
            boolean uploadToWebsite) {}
}
