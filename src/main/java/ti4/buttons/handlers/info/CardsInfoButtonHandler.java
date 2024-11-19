package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.CardsInfoService;

@UtilityClass
public class CardsInfoButtonHandler {

    @ButtonHandler("cardsInfo")
    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        CardsInfoService.sendCardsInfo(game, player, event);
    }
}
