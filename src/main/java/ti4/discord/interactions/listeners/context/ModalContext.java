package ti4.discord.interactions.listeners.context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.game.persistence.GameManager;
import ti4.helpers.ButtonHelper;
import ti4.service.event.EventAuditService;

@Getter
public class ModalContext extends ListenerContext {

    private String modalID;
    private String messageID;
    private Map<String, String> values;
    private Map<String, List<String>> listValues;

    public ModalInteractionEvent getEvent() {
        if (event instanceof ModalInteractionEvent modal) return modal;
        return null;
    }

    public String getContextType() {
        return "modal";
    }

    public ModalContext(ModalInteractionEvent event) {
        super(event, event.getModalId());
        if (!isValid()) return; // super failed

        // Proceed with additional context
        modalID = componentID; // ID after checking faction
        messageID = event.getId();
        values = new HashMap<>();
        listValues = new HashMap<>();
        for (ModalMapping mapping : event.getValues()) {
            switch (mapping.getType()) {
                case TEXT_INPUT -> values.put(mapping.getCustomId(), mapping.getAsString());
                case RADIO_GROUP -> values.put(mapping.getCustomId(), mapping.getAsOptionalString());
                case CHECKBOX -> values.put(mapping.getCustomId(), Boolean.toString(mapping.getAsBoolean()));
                case STRING_SELECT, CHECKBOX_GROUP, USER_SELECT, ROLE_SELECT, MENTIONABLE_SELECT, CHANNEL_SELECT ->
                    listValues.put(mapping.getCustomId(), mapping.getAsStringList());
                default -> {}
            }
        }
    }

    public void save(ButtonInteractionEvent event) {
        boolean skippableButton = componentID.contains("ultimateUndo")
                || "showGameAgain".equalsIgnoreCase(componentID)
                || "cardsInfo".equalsIgnoreCase(componentID)
                || componentID.contains("showDeck")
                || componentID.contains("FactionInfo")
                || componentID.contains("searchMyGames")
                || componentID.contains("decline_explore")
                || componentID.contains("offerDeckButtons");
        if (game != null && !skippableButton) {
            ButtonHelper.saveButtons(event, game, player);
            GameManager.save(game, EventAuditService.getReason(event));
        }
    }
}
