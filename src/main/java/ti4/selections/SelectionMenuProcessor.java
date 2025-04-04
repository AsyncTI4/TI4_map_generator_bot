package ti4.selections;

import java.util.Map;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.executors.ExecutorManager;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.SelectionHandler;
import ti4.listeners.context.SelectionMenuContext;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.service.game.GameNameService;

public class SelectionMenuProcessor {

    private static final Map<String, Consumer<SelectionMenuContext>> knownMenus = AnnotationHandler.findKnownHandlers(SelectionMenuContext.class, SelectionHandler.class);

    public static void queue(StringSelectInteractionEvent event) {
        String gameName = GameNameService.getGameNameFromChannel(event);
        ExecutorManager.runAsync("SelectionMenuProcessor task", gameName, event.getMessageChannel(), () -> process(event));
    }

    private static void process(StringSelectInteractionEvent event) {
        try {
            SelectionMenuContext context = new SelectionMenuContext(event);
            if (context.isValid()) {
                resolveSelectionMenu(context);
                context.save();
            }
        } catch (Exception e) {
            String message = "Selection Menu issue in event: " + event.getComponentId() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.error(new BotLogger.LogMessageOrigin(event), message, e);
        }
    }

    private static boolean handleKnownMenus(SelectionMenuContext context) {
        String menuID = context.getMenuID();
        // Check for exact match first
        if (knownMenus.containsKey(menuID)) {
            knownMenus.get(menuID).accept(context);
            return true;
        }

        // Then check for prefix match
        String longestPrefixMatch = null;
        for (String key : knownMenus.keySet()) {
            if (menuID.startsWith(key)) {
                if (longestPrefixMatch == null || key.length() > longestPrefixMatch.length()) {
                    longestPrefixMatch = key;
                }
            }
        }

        if (longestPrefixMatch != null) {
            knownMenus.get(longestPrefixMatch).accept(context);
            return true;
        }
        return false;
    }

    public static void resolveSelectionMenu(SelectionMenuContext context) {
        if (handleKnownMenus(context)) {
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
                    BotLogger.error(new BotLogger.LogMessageOrigin(event), messageText, e);
                }
                return;
            }
        }
    }

    @SelectionHandler("jmfA_")
    public static void handleJazzMiltyFrameworkAction(StringSelectInteractionEvent event, Game game) {
        game.initializeMiltySettings().parseSelectionInput(event);
        deleteMsg(event);
    }

    private static void deleteMsg(StringSelectInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static String getSelectionMenuDebugText(StringSelectInteractionEvent event) {
        return "You selected:\n```\n" +
            "MenuID: " + event.getComponentId() + "\n" +
            "Values: " + event.getValues() + "\n" +
            "\n```";
    }
}
