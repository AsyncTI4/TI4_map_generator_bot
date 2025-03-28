package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.ParentCommand;
import ti4.selections.selectmenus.BigSelectDemo;

public class SelectionBoxDemoCommand implements ParentCommand {

    @Override
    public String getName() {
        return "select_demo";
    }

    @Override
    public String getDescription() {
        return "Show selection box demo";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        BigSelectDemo.serveDemoSelectMenu(event);
    }
}
