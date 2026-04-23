package ti4.discord.interactions.slashcommands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.Subcommand;

class DiscordantStars extends Subcommand {

    public DiscordantStars() {
        super("discordant_stars", "Display DS Help Text");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "DS.txt");
    }
}
