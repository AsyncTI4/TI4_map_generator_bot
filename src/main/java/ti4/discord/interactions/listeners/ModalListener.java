package ti4.discord.interactions.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.listeners.context.ModalContext;
import ti4.discord.interactions.routing.AnnotationHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.logging.RollbarManager;
import ti4.service.game.GameNameService;
import ti4.spring.service.deploy.ActiveLeaseService;

public final class ModalListener extends ListenerAdapter {

    private static ModalListener instance;

    private final Map<String, Consumer<ModalContext>> knownModals = new HashMap<>();

    public static ModalListener getInstance() {
        if (instance == null) instance = new ModalListener();
        return instance;
    }

    public static void checkModalHandlersSetup() {
        if (getInstance().knownModals.isEmpty()) {
            throw new IllegalStateException("No modal handlers were registered");
        }
    }

    private ModalListener() {
        knownModals.putAll(AnnotationHandler.findKnownHandlers(ModalContext.class, ModalHandler.class));
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) {
            return;
        }
        if (!JdaService.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        event.deferEdit().queue(Consumers.nop(), BotLogger::catchRestError);

        String gameName = GameNameService.getGameNameFromChannel(event);
        var modalContext = new ModalContext(event);
        ExecutorServiceManager.runAsyncWithLock(
                "ModalListener task for  `" + gameName + "`",
                gameName,
                event.getMessageChannel(),
                () -> handleModal(modalContext, event),
                modalContext.isShouldSave() ? ExecutionLockManager.LockType.WRITE : ExecutionLockManager.LockType.READ);
    }

    private void handleModal(ModalContext context, ModalInteractionEvent event) {
        try {
            RollbarManager.putInteractionMetadata("modal", event);
            RollbarManager.put("modal_id", event.getModalId());
            RollbarManager.put("game_name", GameNameService.getGameNameFromChannel(event));
            if (context.isValid()) {
                resolveModalInteractionEvent(context);
                context.save();
            }
        } catch (Exception e) {
            String message = "Modal issue in event: " + event.getModalId() + "\n> Channel: "
                    + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.error(new LogOrigin(event), message, e);
        } finally {
            RollbarManager.clear();
        }
    }

    private boolean handleKnownModals(ModalContext context) {
        String modalID = context.getModalID();
        // Check for exact match first
        if (knownModals.containsKey(modalID)) {
            RollbarManager.put("modal_handler_id", modalID);
            knownModals.get(modalID).accept(context);
            return true;
        }

        // Then check for prefix match
        for (Map.Entry<String, Consumer<ModalContext>> entry : knownModals.entrySet()) {
            if (modalID.startsWith(entry.getKey())) {
                RollbarManager.put("modal_handler_id", entry.getKey());
                entry.getValue().accept(context);
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
            // Detect new settings menu navId() to route to the correct handler.
            String draftSystemNavPart = ".*_draft[._].*";
            if (modalID.matches(draftSystemNavPart)) {
                game.initializeDraftSystemSettings().parseInput(context);
                return;
            }
            game.initializeMiltySettings().parseInput(context);
        }
    }
}
