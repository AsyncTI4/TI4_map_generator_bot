package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.CardsInfoService;

@UtilityClass
class CardsInfoButtonHandler {

    @ButtonHandler(value = "cardsInfo", save = false)
    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        ThreadChannel channel = player.getCardsInfoThread();
        if (channel != null) {
            channel.getManager().setArchived(true).complete(); // archiving it to combat a common bug that is solved via archiving
        }
        CardsInfoService.sendCardsInfo(game, player, event);
    }
}
