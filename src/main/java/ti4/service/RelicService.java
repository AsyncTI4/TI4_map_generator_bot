package ti4.service;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;

/**
 * Service for relic operations to enable dependency injection and testing
 */
public class RelicService {

    public void drawRelicAndNotify(Player player, ButtonInteractionEvent event, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
    }
}