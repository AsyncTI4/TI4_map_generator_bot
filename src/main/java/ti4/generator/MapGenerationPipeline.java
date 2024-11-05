package ti4.generator;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.Nullable;
import ti4.helpers.DisplayType;
import ti4.helpers.GlobalSettings;
import ti4.map.Game;
import ti4.message.BotLogger;

public class MapGenerationPipeline {

    private static final MapGenerationPipeline instance = new MapGenerationPipeline();

    private final BlockingQueue<RenderEvent> gameRenderQueue = new LinkedBlockingQueue<>();
    private final Thread worker;

    private MapGenerationPipeline() {
        worker = new Thread(() -> {
            while (true) {
                try {
                    RenderEvent renderEvent = gameRenderQueue.take();
                    render(renderEvent);
                    System.gc();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    BotLogger.log("Render event threw an exception.", e);
                }
            }
        });
    }

    public static void start() {
        instance.worker.start();
    }

    public static void stop() {
        instance.worker.interrupt();
    }

    private static void render(RenderEvent renderEvent) {
        try (var fileUpload = new MapGenerator(renderEvent.game, renderEvent.displayType)
                .saveImage(renderEvent.event, renderEvent.uploadToDiscord, renderEvent.uploadToWebsite)) {
            if (fileUpload != null && renderEvent.callback != null) {
                renderEvent.callback.accept(fileUpload);
            }
        } catch (IOException e) {
            BotLogger.log("Could not render images for " + renderEvent.game.getName(), e);
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
        instance.gameRenderQueue.add(new RenderEvent(game, event, displayType, callback, uploadToDiscord, uploadToWebsite));
    }

    public record RenderEvent(Game game, GenericInteractionCreateEvent event, DisplayType displayType,
                              Consumer<FileUpload> callback, boolean uploadToDiscord, boolean uploadToWebsite) {}

}
