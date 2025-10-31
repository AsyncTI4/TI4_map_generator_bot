package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;

public class HowToPlayTI4 extends Subcommand {

    public HowToPlayTI4() {
        super("how_to_play_ti4", "Display some helpful stuff for learning TI4");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HelpCommand.showHelpText(event, "HowToPlayTI4.txt");
    }
}
