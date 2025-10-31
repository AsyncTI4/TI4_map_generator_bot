package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;

class FoWPlusHelp extends Subcommand {

    public FoWPlusHelp() {
        super("fowplus", "Explain FoW+ Mode");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "FoWPlusHelp.txt");
    }
}
