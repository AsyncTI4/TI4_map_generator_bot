package ti4.service;

import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;

/**
 * Service for message operations to enable dependency injection and testing
 */
@Service
public class MessageService {

    public void deleteMessage(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }
}