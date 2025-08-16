package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

class NewPlayerInfo extends Subcommand {

    public NewPlayerInfo() {
        super("new_player_info", "Information for players new to AsyncTI4");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendNewPlayerInfoText(event);
    }

    private static void sendNewPlayerInfoText(GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToThread(
                event.getMessageChannel(),
                "Info for Players new to AsyncTI4",
                CreateGameService.getNewPlayerInfoText());
    }
}
