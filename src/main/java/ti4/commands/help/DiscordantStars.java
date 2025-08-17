package ti4.commands.help;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.ResourceHelper;
import ti4.message.MessageHelper;

class DiscordantStars extends Subcommand {

    public DiscordantStars() {
        super("discordant_stars", "Display DS Help Text");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showDSStuff(event);
    }

    private static void showDSStuff(GenericInteractionCreateEvent event) {
        String path = ResourceHelper.getInstance().getHelpFile("DS.txt");
        try {
            String message = Files.readString(Paths.get(path));
            MessageHelper.sendMessageToEventChannel(event, message);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "DS HELP FILE IS BLANK");
        }
    }
}
