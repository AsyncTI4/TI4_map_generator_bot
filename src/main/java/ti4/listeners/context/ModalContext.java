package ti4.listeners.context;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.helpers.ButtonHelper;
import ti4.map.persistence.GameManager;
import ti4.service.event.EventAuditService;

@Getter
public class ModalContext extends ListenerContext {

    private String modalID;
    private String messageID;
    private Map<String, String> values;

    public ModalInteractionEvent getEvent() {
        if (event instanceof ModalInteractionEvent modal)
            return modal;
        return null;
    }

    public String getContextType() {
        return "modal";
    }

    public ModalContext(ModalInteractionEvent event) {
        super(event, event.getModalId());
        if (!isValid()) return; //super failed

        // Proceed with additional context
        this.modalID = this.componentID; // ID after checking faction
        this.messageID = event.getId();
        this.values = new HashMap<>();
        for (ModalMapping mapping : event.getValues()) {
            values.put(mapping.getId(), mapping.getAsString());
        }
    }

    public void save(ButtonInteractionEvent event) {
        boolean skippableButton = componentID.contains("ultimateUndo") ||
            "showGameAgain".equalsIgnoreCase(componentID) ||
            "cardsInfo".equalsIgnoreCase(componentID) ||
            componentID.contains("showDeck") ||
            componentID.contains("FactionInfo") ||
            componentID.contains("searchMyGames") ||
            componentID.contains("decline_explore") ||
            componentID.contains("offerDeckButtons");
        if (game != null && !skippableButton) {
            ButtonHelper.saveButtons(event, game, player);
            GameManager.save(game, EventAuditService.getReason(event, game.isFowMode()));
        }
    }
}
