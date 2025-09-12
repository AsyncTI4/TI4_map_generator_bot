package ti4.helpers.thundersedge;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.breakthrough.PsychosporeService;

public class TeHelperBreakthroughs {
    public static boolean handleBreakthroughExhaust(
            GenericInteractionCreateEvent event, Game game, Player player, String breakthroughID) {
        switch (breakthroughID) {
            case "arborecbt" -> PsychosporeService.postInitialButtons(event, game, player);
            default -> {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "This breakthrough either does not have an exhaust effect, or is not automated. Please resolve manually.");
                return false;
            }
        }
        return true;
    }
}
