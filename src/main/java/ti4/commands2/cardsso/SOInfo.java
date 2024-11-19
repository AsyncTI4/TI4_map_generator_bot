package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class SOInfo extends GameStateSubcommand {

    public SOInfo() {
        super(Constants.INFO, "Sent scored and unscored Secret Objectives to your Cards Info thread", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SecretObjectiveInfoService.sendSecretObjectiveInfo(getGame(), getPlayer(), event);
        MessageHelper.sendMessageToEventChannel(event, "SO Info Sent");
    }
}
