package ti4.discord.interactions.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.selections.selectmenus.BigSelectDemo;
import ti4.discord.interactions.commands.ParentCommand;

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
