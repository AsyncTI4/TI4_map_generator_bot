package ti4.discord.interactions.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;

class Monuments extends Subcommand {

    Monuments() {
        super("monuments", "Show Helpful Monuments stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "Monuments.txt");
    }
}
