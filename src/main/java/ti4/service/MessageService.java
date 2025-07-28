package ti4.service;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;

/**
 * Service for message operations to enable dependency injection and testing
 */
public class MessageService {

    public void deleteMessage(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }
}