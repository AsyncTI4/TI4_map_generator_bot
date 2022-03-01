package ti4.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface Command {
    //If command can be executed for given command text
    boolean accept(MessageReceivedEvent event);

    //Command action execution method
    void execute(MessageReceivedEvent event);
}
