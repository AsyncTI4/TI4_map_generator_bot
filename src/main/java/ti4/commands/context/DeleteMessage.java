package ti4.commands.context;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.listeners.DeletionListener;
import ti4.message.logging.BotLogger;

public class DeleteMessage extends MessageCommand {

    public DeleteMessage() {
        super("Delete Message", Permission.PIN_MESSAGES);
    }

    private boolean suspicious = false;

    public void execute(MessageContextInteractionEvent event) {
        event.getTarget().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        DeletionListener.handleContextMenuDelete(event);
    }

    public void postExecute(MessageContextInteractionEvent event) {
        if (suspicious) {
            event.reply("Deleting this is a bit suspicious... don't ya think?").queue();
        } else {
            event.getHook().deleteOriginal().queue();
        }
    }
}
