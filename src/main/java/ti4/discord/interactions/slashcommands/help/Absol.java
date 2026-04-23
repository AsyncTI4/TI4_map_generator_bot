package ti4.discord.interactions.slashcommands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.Subcommand;

class Absol extends Subcommand {

    public Absol() {
        super("absol", "Show Helpful Absol stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "Absol.txt");
    }
}
