package ti4.service;

import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;

/**
 * Service for relic operations to enable dependency injection and testing
 */
@Service
public class RelicService {

    public void drawRelicAndNotify(Player player, ButtonInteractionEvent event, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
    }
}