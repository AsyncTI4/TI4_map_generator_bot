package ti4.listeners.context;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;

@Getter
public class ButtonContext extends ListenerContext {

    private String messageID;

    @JsonIgnore
    public String getButtonID() {
        return componentID;
    }

    public ButtonInteractionEvent getEvent() {
        if (event instanceof ButtonInteractionEvent button)
            return button;
        return null;
    }

    public String getContextType() {
        return "button";
    }

    public ButtonContext(ButtonInteractionEvent event) {
        // Most of the generic checks happen inside `super` constructor
        // If something fails in super, it will set the valid flag to false, and we will quit immediately
        super(event, event.getButton().getId());
        if (!isValid()) {
            return;
        }

        // Proceed with additional button things
        this.messageID = event.getMessageId();

        if (componentID.contains("deleteThisButton")) {
            componentID = componentID.replace("deleteThisButton", "");
            ButtonHelper.deleteTheOneButton(event);
        }
        if (componentID.contains("deleteThisMessage")) {
            componentID = componentID.replace("deleteThisMessage", "");
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public void save() {
        boolean skip = componentID.contains("ultimateUndo") ||
            "showGameAgain".equalsIgnoreCase(componentID) ||
            "cardsInfo".equalsIgnoreCase(componentID) ||
            componentID.contains("showDeck") ||
            componentID.contains("FactionInfo") ||
            componentID.contains("searchMyGames") ||
            componentID.contains("decline_explore") ||
            componentID.contains("offerDeckButtons");
        if (skip) {
            return;
        }
        if (game != null) {
            ButtonHelper.saveButtons(getEvent(), game, player);
        }
        if (componentID.contains("jmf")) {
            ManagedGame managedGame = GameManager.getManagedGame(game.getName());
            Game game2 = managedGame.getGame();
            if (game2.getButtonPressCount() != game.getButtonPressCount()) {
                super.save();
            }
        } else {
            super.save();
        }
    }
}
