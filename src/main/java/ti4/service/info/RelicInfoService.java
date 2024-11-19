package ti4.service.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands2.CommandHelper;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class RelicInfoService {

    public static void sendRelicInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        RelicInfoService.sendRelicInfo(game, player, event);
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        RelicHelper.sendRelicInfo(player);
    }
}
