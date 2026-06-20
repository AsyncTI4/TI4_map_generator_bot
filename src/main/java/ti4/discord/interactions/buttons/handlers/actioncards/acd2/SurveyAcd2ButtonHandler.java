package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class SurveyAcd2ButtonHandler {

    @ButtonHandler("resolveSurvey")
    public static void resolveSurvey(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you do not control a planet you can explore for _Survey_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose 1 planet you control to explore for _Survey_."
                        + " When you would resolve the exploration card, you may instead resolve any card from that"
                        + " deck's discard pile (resolve that card manually).",
                buttons);
    }
}
