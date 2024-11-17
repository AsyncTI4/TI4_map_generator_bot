package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SOInfo extends GameStateSubcommand {

    public SOInfo() {
        super(Constants.INFO, "Sent scored and unscored Secret Objectives to your Cards Info thread", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SecretObjectiveHelper.sendSecretObjectiveInfo(getGame(), getPlayer(), event);
        MessageHelper.sendMessageToEventChannel(event, "SO Info Sent");
    }

    @ButtonHandler("refreshSOInfo")
    public static void sendSecretObjectiveInfo(Game game, Player player, ButtonInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        SecretObjectiveHelper.sendSecretObjectiveInfo(game, player);
    }
}
