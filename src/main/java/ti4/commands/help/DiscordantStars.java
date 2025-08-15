package ti4.commands.help;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

class DiscordantStars extends Subcommand {

    public DiscordantStars() {
        super("discordant_stars", "Display DS Help Text");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showDSStuff(event);
    }

    public static void showDSStuff(GenericInteractionCreateEvent event) {
        String path = ResourceHelper.getInstance().getHelpFile("DS.txt");
        try {
            String message = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "DS HELP FILE IS BLANK");
        }
    }
}
