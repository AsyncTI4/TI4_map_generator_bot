package ti4.buttons.handlers.commodities;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.commodities.CommodityConversionService;

@UtilityClass
public class ConvertCommoditiesButtonHandler {

  @ButtonHandler("convertAllComms")
  public static void convertAllComm(ButtonInteractionEvent event, Player player, Game game) {
    CommodityConversionService.convertAllComm(event, player, game);
  }
}
