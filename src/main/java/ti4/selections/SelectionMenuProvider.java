package ti4.selections;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

public class SelectionMenuProvider {
    public static void resolveSelectionMenu(StringSelectInteractionEvent event) {
        event.getChannel().sendMessage(getSelectionMenuDebugText(event)).queue();


        String selectionMenuID = event.getComponentId();
    }

    private static String getSelectionMenuDebugText(StringSelectInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.getComponentId()).append("\n");
        sb.append("Values: ").append(event.getValues()).append("\n");
        return sb.toString();
    }
}
