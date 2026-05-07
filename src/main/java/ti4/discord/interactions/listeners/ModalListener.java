package ti4.discord.interactions.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.JdaService;
import ti4.discord.interactions.listeners.context.ModalContext;
import ti4.discord.interactions.routing.AnnotationHandler;
import ti4.discord.interactions.routing.HandlerRegistry;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.logging.RollbarManager;
import ti4.service.game.GameNameService;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

public final class ModalListener extends ListenerAdapter {

    private static ModalListener instance;

    private final HandlerRegistry<ModalContext> registry;

    public static ModalListener getInstance() {
        if (instance == null) instance = new ModalListener();
        return instance;
    }

    public static void checkModalHandlersSetup() {
        if (getInstance().registry.handlers().isEmpty()) {
            throw new IllegalStateException("No modal handlers were registered");
        }
    }

    private ModalListener() {
        registry = AnnotationHandler.findKnownHandlers(ModalContext.class, ModalHandler.class);
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
        String rawModalID = event.getModalId();
        ExecutionLockType lockType = registry.isSave(rawModalID) ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutorServiceManager.runAsyncWithLock(
                "ModalListener task for  `" + gameName + "`",
                gameName,
                event.getMessageChannel(),
                () -> handleModal(event),
                lockType);
    }

    private void handleModal(ModalInteractionEvent event) {
        ModalContext context = new ModalContext(event);
        if (!context.isValid()) {
            BotLogger.warning(new LogOrigin(event), "Invalid modal context.");
            return;
        }
        try {
            RollbarManager.putInteractionMetadata("modal", event);
            RollbarManager.put("modal_id", event.getModalId());
            RollbarManager.put("game_name", GameNameService.getGameNameFromChannel(event));

            CombatReplayService combatReplayService = SpringContext.getBean(CombatReplayService.class);
            combatReplayService.setPreInteractionSnapshot(
                    combatReplayService.capturePreInteractionSnapshot(context.getGame()));
            try {
                resolveModalInteractionEvent(context);
                context.save();
            } finally {
                combatReplayService.clearPreInteractionSnapshot();
            }
        } catch (Exception e) {
            String message = "Modal issue in event: " + event.getModalId() + "\n> Channel: "
                    + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.error(new LogOrigin(event), message, e);
        } finally {
            RollbarManager.clear();
        }
    }

    private void resolveModalInteractionEvent(@Nonnull ModalContext context) {
        String modalID = context.getModalID();
        Game game = context.getGame();

        if (registry.handle(modalID, context)) return;

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
