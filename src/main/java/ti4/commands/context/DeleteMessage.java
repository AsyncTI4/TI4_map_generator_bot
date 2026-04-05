package ti4.commands.context;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.listeners.DeletionListener;
import ti4.message.logging.BotLogger;
import ti4.spring.service.messagecache.SavedBotMessagesService;

public class DeleteMessage extends MessageCommand {

    public DeleteMessage() {
        super("Delete Message", Permission.PIN_MESSAGES);
    }

    @Override
    public void execute(MessageContextInteractionEvent event) {
        // Preemptively remove this message from the saved message cache
        SavedBotMessagesService.getBean().remove(event.getTarget().getIdLong());
        event.getTarget().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @Override
    public void postExecute(MessageContextInteractionEvent event) {
        if (SavedBotMessagesService.isImportantMessage(event.getTarget())) {
            DeletionListener.handleContextMenuDelete(event);
        }
        event.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
