package ti4.commands.help;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

public class HowToPlayTI4 extends Subcommand {

    public HowToPlayTI4() {
        super("how_to_play_ti4", "Display some helpful stuff for learning TI4");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        howToPlayTI4(event);
    }

    public static void howToPlayTI4(GenericInteractionCreateEvent event) {
        String path = ResourceHelper.getInstance().getHelpFile("HowToPlayTI4.txt");
        try {
            String message = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "TI4 HELP FILE IS BLANK");
        }
    }
}
