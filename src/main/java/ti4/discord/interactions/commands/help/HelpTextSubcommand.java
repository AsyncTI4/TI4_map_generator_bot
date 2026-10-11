package ti4.discord.interactions.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;

class HelpTextSubcommand extends Subcommand {

    private final String helpFileName;

    HelpTextSubcommand(String name, String description, String helpFileName) {
        super(name, description);
        this.helpFileName = helpFileName;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, helpFileName);
    }
}
