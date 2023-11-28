package ti4.selections;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

public interface Selection {
    String getSelectionID();

    // Check whether the selection menu will be executed based on the event
    default boolean accept(StringSelectInteractionEvent event) {
        return event.getComponentId().equals(getSelectionID());
    }

    // Command action execution method
    void execute(StringSelectInteractionEvent event);

    default void postExecute(StringSelectInteractionEvent event) {
        // DO NOTHING
    }

    default String getSelectionMenuDebugText(StringSelectInteractionEvent event) {
      String sb = event.getComponentId() + "\n" +
          "Values: " + event.getValues() + "\n";
        return sb;
    }
}
