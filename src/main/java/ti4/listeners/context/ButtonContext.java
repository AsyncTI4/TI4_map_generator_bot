package ti4.listeners.context;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.map.GameSaveLoadManager;

public class ButtonContext extends ListenerContext {
    public String buttonID, messageID;
    //public ButtonInteractionEvent event;

    public ButtonInteractionEvent getEvent() {
        if (event instanceof ButtonInteractionEvent button)
            return button;
        return null;
    }

    public String getContextType() {
        return "button";
    }

    public ButtonContext(ButtonInteractionEvent event) {
        super(event, event.getButton().getId());
        if (!isValid()) return; // super failed

        // Proceed with additional context
        this.buttonID = this.componentID; // ID after checking faction
        this.messageID = event.getMessageId();

        //additional button things
        if (game != null && !buttonID.contains("ultimateUndo") && !"showGameAgain".equalsIgnoreCase(buttonID) && !"no_sabotage".equalsIgnoreCase(buttonID)) {
            ButtonHelper.saveButtons(event, game, player);
            GameSaveLoadManager.saveMap(game, event);
        }

        if (buttonID.contains("deleteThisButton")) {
            buttonID = buttonID.replace("deleteThisButton", "");
            componentID = componentID.replace("deleteThisButton", "");
            ButtonHelper.deleteTheOneButton(event);
        }
    }
}
