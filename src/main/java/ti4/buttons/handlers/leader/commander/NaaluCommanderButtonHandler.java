package ti4.buttons.handlers.leader.commander;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.leader.NaaluCommanderService;

@UtilityClass
class NaaluCommanderButtonHandler {

    @ButtonHandler("naaluCommander")
    public static void secondHalfOfNaaluCommander(GenericInteractionCreateEvent event, Game game, Player player) {
        NaaluCommanderService.secondHalfOfNaaluCommander(event, game, player);
    }
}
