package ti4.commands2.help;

import java.nio.file.Files;
import java.nio.file.Paths;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands2.Subcommand;
import ti4.message.MessageHelper;

class Monuments extends Subcommand {

    public Monuments() {
        super("monuments", "Show Helpful Monuments stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showMonumentsHelp(event);
    }

    public static void showMonumentsHelp(GenericInteractionCreateEvent event) {
        String path = ResourceHelper.getInstance().getHelpFile("Monuments.txt");
        try {
            String message = new String(Files.readAllBytes(Paths.get(path)));
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "MONUMENTS HELP FILE IS BLANK");
        }
    }
}
