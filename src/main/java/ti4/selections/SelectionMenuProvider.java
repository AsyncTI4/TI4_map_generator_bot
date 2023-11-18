package ti4.selections;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.message.BotLogger;

public class SelectionMenuProvider {
    public static void resolveSelectionMenu(StringSelectInteractionEvent event) {
        event.reply(getSelectionMenuDebugText(event)).setEphemeral(true).queue();

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
        event.getHook().deleteOriginal().queue();
    }

    private static String getSelectionMenuDebugText(StringSelectInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("You selected:\n```\n");
        sb.append("MenuID: ").append(event.getComponentId()).append("\n");
        sb.append("Values: ").append(event.getValues()).append("\n");
        sb.append("\n```");
        return sb.toString();
    }
}
