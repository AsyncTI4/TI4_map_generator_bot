package ti4.discord.interactions.selections;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.listeners.context.SelectionMenuContext;
import ti4.discord.interactions.routing.AnnotationHandler;
import ti4.discord.interactions.routing.HandlerRegistry;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.logging.RollbarManager;
import ti4.service.game.GameNameService;
import ti4.spring.context.SpringContext;

@UtilityClass
public final class SelectionMenuProcessor {

    private static final HandlerRegistry<SelectionMenuContext> registry =
            AnnotationHandler.findKnownHandlers(SelectionMenuContext.class, SelectionHandler.class);

    public static void checkSelectionMenuHandlersSetup() {
        if (registry.handlers().isEmpty()) {
            throw new IllegalStateException("No button handlers were registered");
        }
    }

    public static void queue(StringSelectInteractionEvent event) {
        String gameName = GameNameService.getGameNameFromChannel(event);
        String rawComponentID = event.getSelectMenu().getCustomId();
        ExecutionLockType lockType = registry.isSave(rawComponentID) ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutorServiceManager.runAsyncWithLock(
                "SelectionMenuProcessor task for `" + gameName + "`",
                gameName,
                event.getMessageChannel(),
                () -> process(event),
                lockType);
    }

    private static void process(StringSelectInteractionEvent event) {
        SelectionMenuContext context = new SelectionMenuContext(event);
        if (!context.isValid()) {
            BotLogger.warning(new LogOrigin(event), "Invalid selection menu context.");
            return;
        }
        try {
            RollbarManager.putInteractionMetadata("select_menu", event);
            RollbarManager.put("menu_id", event.getComponentId());
            RollbarManager.put("game_name", GameNameService.getGameNameFromChannel(event));

            CombatReplayService combatReplayService = SpringContext.getBean(CombatReplayService.class);
            combatReplayService.setPreInteractionSnapshot(
                    combatReplayService.capturePreInteractionSnapshot(context.getGame()));
            try {
                resolveSelectionMenu(context);
                context.save();
            } finally {
                combatReplayService.clearPreInteractionSnapshot();
            }
        } catch (Exception e) {
            String message = "Selection Menu issue in event: " + event.getComponentId() + "\n> Channel: "
                    + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.error(new LogOrigin(event), message, e);
        } finally {
            RollbarManager.clear();
        }
    }

    private static void resolveSelectionMenu(SelectionMenuContext context) {
        if (registry.handle(context.getMenuID(), context)) {
            return;
        }

        StringSelectInteractionEvent event = context.getEvent();
        SelectionManager selectionManager = SelectionManager.getInstance();
        for (Selection selection : selectionManager.getSelectionMenuList()) {
            if (selection.accept(event)) {
                try {
                    selection.execute(event, context);
                    selection.postExecute(event);
                } catch (Exception e) {
                    String messageText = "Error trying to execute selection: " + event.getComponentId();
                    BotLogger.error(new LogOrigin(event), messageText, e);
                }
                return;
            }
        }
    }

    @SelectionHandler("jmfA_")
    public static void handleJazzMiltyFrameworkAction(StringSelectInteractionEvent event, Game game) {
        // Detect new settings menu navId() to route to the correct handler.
        String draftSystemNavPart = ".*_draft[._].*";
        if (event.getCustomId().matches(draftSystemNavPart)) {
            game.initializeDraftSystemSettings().parseSelectionInput(event);
            deleteMsg(event);
            return;
        }

        game.initializeMiltySettings().parseSelectionInput(event);
        deleteMsg(event);
    }

    private static void deleteMsg(StringSelectInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static String getSelectionMenuDebugText(StringSelectInteractionEvent event) {
        return "You selected:\n```\n" + "MenuID: "
                + event.getComponentId() + "\n" + "Values: "
                + event.getValues() + "\n" + "\n```";
    }
}
