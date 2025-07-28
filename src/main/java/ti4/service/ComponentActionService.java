package ti4.service;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ComponentActionHelper;
import ti4.map.Game;
import ti4.map.Player;

/**
 * Service for component action operations to enable dependency injection and testing
 */
public class ComponentActionService {

    public void serveNextComponentActionButtons(ButtonInteractionEvent event, Game game, Player player) {
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }
}