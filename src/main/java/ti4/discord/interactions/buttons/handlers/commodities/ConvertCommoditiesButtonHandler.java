package ti4.discord.interactions.buttons.handlers.commodities;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.commodities.CommodityConversionService;

@UtilityClass
public class ConvertCommoditiesButtonHandler {

    @ButtonHandler("convertAllComms")
    public static void convertAllComm(ButtonInteractionEvent event, Player player, Game game) {
        CommodityConversionService.convertAllComm(event, player, game);
    }
}
