package ti4.commands2.help;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.Subcommand;
import ti4.message.MessageHelper;

import static ti4.helpers.GameCreationHelper.getNewPlayerInfoText;

class NewPlayerInfo extends Subcommand {

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

}
