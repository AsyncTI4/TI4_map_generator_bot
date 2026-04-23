package ti4.discord.interactions.slashcommands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.Subcommand;

class FoWHelp extends Subcommand {

    public FoWHelp() {
        super("fow", "Explain Fog of War");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "FoWHelp.txt");
    }
}
