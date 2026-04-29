package ti4.discord.interactions.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.info.CardsInfoService;

@UtilityClass
class CardsInfoButtonHandler {

    @ButtonHandler(value = "cardsInfo", save = false)
    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        if (player == null) {
            return;
        }
        if (!game.isFowMode()) {
            ThreadChannel channel = player.getCardsInfoThread();
            // archiving it to combat a common bug that is solved via archiving
            channel.getManager().setArchived(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        CardsInfoService.sendCardsInfo(game, player, event);
        EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
    }
}
