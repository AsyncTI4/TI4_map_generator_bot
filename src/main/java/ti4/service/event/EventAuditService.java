package ti4.service.event;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

@UtilityClass
public class EventAuditService {

    public static String getReason(GenericInteractionCreateEvent event, boolean isFow) {
        if (event == null) {
            return null;
        }
        String username = event.getUser().getName();
        return switch (event) {
            case SlashCommandInteractionEvent slash -> username + " used: " + slash.getCommandString();
            case ButtonInteractionEvent button -> {
                boolean thread = button.getMessageChannel() instanceof ThreadChannel;
                boolean cardThread =
                        thread && button.getMessageChannel().getName().contains("Cards Info-");
                boolean draftThread =
                        thread && button.getMessageChannel().getName().contains("Draft Bag-");
                if (cardThread
                        || draftThread
                        || isFow
                        || button.getButton().getId().contains("anonDeclare")
                        || button.getButton().getId().contains("requestAllFollow")) {
                    yield "someone pressed button: [CLASSIFIED]";
                }
                yield username + " pressed button: " + button.getButton().getId() + " -- "
                        + button.getButton().getLabel();
            }
            case StringSelectInteractionEvent selectMenu ->
                username + " used string selection: " + selectMenu.getComponentId();
            case ModalInteractionEvent modal -> username + " used modal: " + modal.getModalId();
            default -> "Last Command Unknown";
        };
    }
}
