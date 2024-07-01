package ti4.selections;

import org.apache.commons.lang3.function.Consumers;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.listeners.context.SelectionMenuContext;
import ti4.message.BotLogger;

public class SelectionMenuProvider {

    public static void resolveSelectionMenu(SelectionMenuContext context) {
        StringSelectInteractionEvent event = context.getEvent();
        SelectionManager selectionManager = SelectionManager.getInstance();
        for (Selection selection : selectionManager.getSelectionMenuList()) {
            if (selection.accept(event)) {
                try {
                    selection.execute(event);
                    selection.postExecute(event);
                } catch (Exception e) {
                    String messageText = "Error trying to execute selection: " + event.getComponentId();
                    BotLogger.log(event, messageText, e);
                }
                return;
            }
        }

        resolveOtherSelectionMenu(context);
    }

    public static void resolveOtherSelectionMenu(SelectionMenuContext context) {
        if (context.menuID.startsWith("jmfN_") || context.menuID.startsWith("jmfA_")) {
            context.getGame().initializeMiltySettings().parseSelectionInput(context.getEvent());
            deleteMsg(context.getEvent());
        }
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
