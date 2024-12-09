package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.DateTimeHelper;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.listeners.context.ModalContext;
import ti4.map.Game;
import ti4.message.BotLogger;

public class ModalListener extends ListenerAdapter {

    public static ModalListener instance = null;

    private final Map<String, Consumer<ModalContext>> knownModals = new HashMap<>();

    public static ModalListener getInstance() {
        if (instance == null)
            instance = new ModalListener();
        return instance;
    }

    private ModalListener() {
        knownModals.putAll(AnnotationHandler.findKnownHandlers(ModalContext.class, ModalHandler.class));
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        AsyncTI4DiscordBot.runAsync("Modal listener task", () -> handleModal(event));
    }

    private void handleModal(@Nonnull ModalInteractionEvent event) {
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long startTime = System.currentTimeMillis();
        try {
            ModalContext context = new ModalContext(event);
            if (context.isValid()) {
                resolveModalInteractionEvent(context);
                context.save(event);
            }
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }

        long endTime = System.currentTimeMillis();
        final int milliThreshold = 3000;
        if (startTime - eventTime > milliThreshold || endTime - startTime > milliThreshold) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(startTime - eventTime);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(endTime - startTime);
            String errorMessage = "Modal took over " + milliThreshold + "ms to process:\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime) + " message was sent\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime) + " `" + responseTime + "` to receive\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime) + " `" + executionTime + "` to execute";
            BotLogger.log(errorMessage);
        }
    }

    private boolean handleKnownModals(ModalContext context) {
        String modalID = context.getModalID();
        // Check for exact match first
        if (knownModals.containsKey(modalID)) {
            knownModals.get(modalID).accept(context);
            return true;
        }

        // Then check for prefix match
        for (String key : knownModals.keySet()) {
            if (modalID.startsWith(key)) {
                knownModals.get(key).accept(context);
                return true;
            }
        }
        return false;
    }

    private void resolveModalInteractionEvent(@Nonnull ModalContext context) {
        String modalID = context.getModalID();
        Game game = context.getGame();

        if (handleKnownModals(context)) return;

        if (modalID.startsWith("jmfA_")) {
            game.initializeMiltySettings().parseInput(context);
        }
    }
}
