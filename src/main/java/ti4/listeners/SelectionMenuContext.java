package ti4.listeners;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

public class SelectionMenuContext extends ListenerContext {
    public String menuID, messageID;
    public List<String> values;

    public StringSelectInteractionEvent getEvent() {
        if (event instanceof StringSelectInteractionEvent button)
            return button;
        return null;
    }

    public String getContextType() {
        return "menu";
    }

    public SelectionMenuContext(StringSelectInteractionEvent event) {
        super(event, event.getSelectMenu().getId());
        if (!isValid()) return; //super failed

        // Proceed with additional context
        this.menuID = this.componentID; // ID after checking faction
        this.messageID = event.getMessageId();
        this.values = new ArrayList<>(event.getValues());
    }
}
