package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.selections.selectmenus.BigSelectDemo;

public class SelectionBoxDemo implements Command {

    @Override
    public String getName() {
        return "select_demo";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        BigSelectDemo.serveDemoSelectMenu(event);
    }

    @Override
    public void register(CommandListUpdateAction commands) {

        commands.addCommands(
            Commands.slash(getName(), "Show selection box demo"));
    }
}
