package ti4.discord.interactions.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.strategycard.TradeStrategyCardService;

@UtilityClass
class TradeStrategyCardButtonHandler {

    @ButtonHandler("trade_primary")
    public static void tradePrimary(Game game, GenericInteractionCreateEvent event, Player player) {
        TradeStrategyCardService.doPrimary(game, event, player);
    }
}
