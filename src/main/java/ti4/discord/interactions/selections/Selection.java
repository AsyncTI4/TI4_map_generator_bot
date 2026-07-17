package ti4.discord.interactions.selections;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.discord.interactions.listeners.context.SelectionMenuContext;

public interface Selection {

    String getSelectionID();

    default boolean accept(StringSelectInteractionEvent event) {
        return event.getComponentId().equals(getSelectionID());
    }

    void execute(StringSelectInteractionEvent event, SelectionMenuContext context);

    default void postExecute(StringSelectInteractionEvent event) {}
}
