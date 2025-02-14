package ti4.listeners.context;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

@Getter
public class SelectionMenuContext extends ListenerContext {

    private String menuID;
    private String messageID;
    private List<String> values;

    public StringSelectInteractionEvent getEvent() {
        if (event instanceof StringSelectInteractionEvent button) return button;
        return null;
    }

    public String getContextType() {
        return "menu";
    }

    public SelectionMenuContext(StringSelectInteractionEvent event) {
        super(event, event.getSelectMenu().getId());
        if (!isValid()) return; // super failed

        // Proceed with additional context
        this.menuID = this.componentID; // ID after checking faction
        this.messageID = event.getMessageId();
        this.values = new ArrayList<>(event.getValues());
    }
}
