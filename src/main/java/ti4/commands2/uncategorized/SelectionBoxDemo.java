package ti4.commands2.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.ParentCommand;
import ti4.selections.selectmenus.BigSelectDemo;

public class SelectionBoxDemo implements ParentCommand {

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
