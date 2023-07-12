package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;

public class TechCommand implements Command {

    @Override
    public String getActionID() {
        return Constants.TECH;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'accept'");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'execute'");
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerCommands'");
    }
    
}
