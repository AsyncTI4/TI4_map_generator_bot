package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.GameManager;
import ti4.message.MessageHelper;
import ti4.selections.selectmenus.BigSelectDemo;

public class SelectionBoxDemo implements Command {

    @Override
    public String getActionID() {
        return "select_demo";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        BigSelectDemo.serveDemoSelectMenu(event);
    }
    @Override
    public void registerCommands(CommandListUpdateAction commands) {

        commands.addCommands(
                Commands.slash(getActionID(), "Show selection box demo"));
    }
}
