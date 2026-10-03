package ti4.discord.interactions.listeners.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;

@Getter
public class ButtonContext extends ListenerContext {

    private String messageID;

    @JsonIgnore
    public String getButtonID() {
        return getComponentID();
    }

    public ButtonInteractionEvent getEvent() {
        if (event instanceof ButtonInteractionEvent button) return button;
        return null;
    }

    public String getContextType() {
        return "button";
    }

    public ButtonContext(ButtonInteractionEvent event) {
        super(event, event.getButton().getCustomId());
        if (!isValid()) {
            return;
        }

        // Proceed with additional button things
        messageID = event.getMessageId();

        if (envelope.deleteButton()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }

        if (envelope.deleteMessage()) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public void save() {
        if (!shouldSave) {
            return;
        }
        if (game != null) {
            ButtonHelper.saveButtons(getEvent(), game, player);
        }
        super.save();
    }
}
