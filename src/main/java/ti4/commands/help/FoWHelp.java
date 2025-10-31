package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;

class FoWHelp extends Subcommand {

    public FoWHelp() {
        super("fow", "Explain Fog of War");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "FoWHelp.txt");
    }
}
