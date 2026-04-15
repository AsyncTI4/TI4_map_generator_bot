package ti4.discord.interactions.buttons.handlers.tech.specific;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class PsychoarchaeologyButtonHandler {

    @ButtonHandler("getPsychoButtons")
    public static void offerPsychoButtons(Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use buttons to gain 1 trade good per planet exhausted.",
                ButtonHelper.getPsychoTechPlanets(game, player));
    }
}
