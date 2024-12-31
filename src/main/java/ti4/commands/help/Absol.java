package ti4.commands.help;

import java.nio.file.Files;
import java.nio.file.Paths;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

class Absol extends Subcommand {

    public Absol() {
        super("absol", "Show Helpful Absol stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showAbsolStuff(event);
    }

    public static void showAbsolStuff(GenericInteractionCreateEvent event) {
        String path = ResourceHelper.getInstance().getHelpFile("Absol.txt");
        try {
            String message = new String(Files.readAllBytes(Paths.get(path)));
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "ABSOL HELP FILE IS BLANK");
        }
    }
}
