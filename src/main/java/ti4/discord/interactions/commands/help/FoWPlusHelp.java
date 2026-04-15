package ti4.discord.interactions.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;

class FoWPlusHelp extends Subcommand {

    public FoWPlusHelp() {
        super("fowplus", "Explain FoW+ Mode");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!JdaService.fowServers.contains(event.getGuild())) {
            MessageHelper.replyToMessage(event, "Only relevant in FoW servers.");
            return;
        }

        HelpCommand.showHelpText(event, "FoWPlusHelp.txt");
    }
}
