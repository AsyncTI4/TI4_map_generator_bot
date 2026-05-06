package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.helpers.StatusHelper;

@UtilityClass
public class EndPhaseService {
    public static void EndActionPhase(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        StatusHelper.announceStatusPhase(game);
        if (!game.isOmegaPhaseMode()) {
            StatusHelper.beginScoring(event, game, gameChannel);
        } else {
            StartPhaseService.startStatusHomework(event, game);
        }
        StatusHelper.handleStatusPhaseMiddle(event, game, gameChannel);
    }
}
