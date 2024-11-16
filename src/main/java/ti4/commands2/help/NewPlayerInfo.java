package ti4.commands2.help;

import java.nio.file.Files;
import java.nio.file.Paths;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.commands2.Subcommand;
import ti4.message.MessageHelper;

public class NewPlayerInfo extends Subcommand {

    public NewPlayerInfo() {
        super("new_player_info", "Information for players new to AsyncTI4");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendNewPlayerInfoText(event);
    }

    public static void sendNewPlayerInfoText(GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToThread(event.getMessageChannel(), "Info for Players new to AsyncTI4", getNewPlayerInfoText());
    }

    public static String getNewPlayerInfoText() {
        String path = ResourceHelper.getInstance().getHelpFile("NewPlayerIntro.txt");
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (Exception e) {
            return "NewPlayerIntro HELP FILE IS BLANK";
        }
    }
}
