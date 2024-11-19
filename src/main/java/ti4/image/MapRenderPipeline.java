package ti4.image;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.Nullable;
import ti4.helpers.DisplayType;
import ti4.helpers.GlobalSettings;
import ti4.map.Game;
import ti4.message.BotLogger;

public class MapRenderPipeline {

    private static final MapRenderPipeline instance = new MapRenderPipeline();

    private final BlockingQueue<RenderEvent> gameRenderQueue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private boolean running = true;

    private MapRenderPipeline() {
        worker = new Thread(() -> {
            while (running || !gameRenderQueue.isEmpty()) {
                try {
                    RenderEvent renderEvent = gameRenderQueue.poll(2, TimeUnit.SECONDS);
                    if (renderEvent != null) {
                        render(renderEvent);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    BotLogger.log("MapRenderPipeline worker threw an exception.", e);
                }
            }
        });
    }

    public static void start() {
        instance.worker.start();
    }

    public static boolean shutdown() {
        instance.running = false;
        try {
            instance.worker.join(20000);
            return !instance.worker.isAlive();
        } catch (InterruptedException e) {
            BotLogger.log("MapRenderPipeline shutdown interrupted.");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void render(RenderEvent renderEvent) {
        try (var mapGenerator = new MapGenerator(renderEvent.game, renderEvent.displayType, renderEvent.event)) {
            mapGenerator.draw();
            if (renderEvent.uploadToDiscord) {
                uploadToDiscord(mapGenerator, renderEvent.callback());
            }
            if (renderEvent.uploadToWebsite) {
                mapGenerator.uploadToWebsite();
            }
        } catch (Exception e) {
            BotLogger.log("Render event threw an exception. Game '" + renderEvent.game.getName() + "'", e);
        }
    }

    private static void uploadToDiscord(MapGenerator mapGenerator, Consumer<FileUpload> callback) {
        try (var fileUpload = mapGenerator.createFileUpload()) {
            if (fileUpload != null && callback != null) {
                callback.accept(fileUpload);
            }
        } catch (IOException e) {
            BotLogger.log("Could not render images for " + mapGenerator.getGameName(), e);
        }
    }

    public static void renderToWebsiteOnly(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) {
            render(game, event, null, null, false, true);
        }
    }

    public static void render(Game game, @Nullable SlashCommandInteractionEvent event, @Nullable Consumer<FileUpload> callback) {
        render(game, event, null, callback, true, true);
    }

    public static void render(Game game, @Nullable GenericInteractionCreateEvent event, @Nullable DisplayType displayType,
                       @Nullable Consumer<FileUpload> callback) {
        render(game, event, displayType, callback, true, true);
    }

    public static void render(Game game, @Nullable GenericInteractionCreateEvent event,  @Nullable DisplayType displayType,
                       @Nullable Consumer<FileUpload> callback, boolean uploadToDiscord, boolean uploadToWebsite) {
        if (game == null) {
            throw new IllegalArgumentException("game cannot be null in render pipeline");
        }
        instance.gameRenderQueue.add(new RenderEvent(game, event, displayType, callback, uploadToDiscord, uploadToWebsite));
    }

    public record RenderEvent(Game game, GenericInteractionCreateEvent event, DisplayType displayType,
                              Consumer<FileUpload> callback, boolean uploadToDiscord, boolean uploadToWebsite) {}

}
