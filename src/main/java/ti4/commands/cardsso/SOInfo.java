package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class SOInfo extends GameStateSubcommand {

    public SOInfo() {
        super(Constants.INFO, "Sends scored and unscored secret objectives to your #cards-info thread", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SecretObjectiveInfoService.sendSecretObjectiveInfo(getGame(), getPlayer(), event);
        MessageHelper.sendMessageToEventChannel(event, "Secret objective info sent.");
    }
}
