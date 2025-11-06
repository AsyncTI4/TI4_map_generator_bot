package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;

class Absol extends Subcommand {

    public Absol() {
        super("absol", "Show Helpful Absol stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "Absol.txt");
    }
}
