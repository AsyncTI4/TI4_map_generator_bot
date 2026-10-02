package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class InnovationAcd2ButtonHandler {

    @ButtonHandler("innovation")
    public static void resolveInnovation(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmoji() + " readied every planet with a technology specialty.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
