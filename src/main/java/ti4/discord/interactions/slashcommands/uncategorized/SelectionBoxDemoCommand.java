package ti4.discord.interactions.slashcommands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.selections.selectmenus.BigSelectDemo;

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
