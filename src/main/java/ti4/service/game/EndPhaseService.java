package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.StatusHelper;
import ti4.map.Game;

@UtilityClass
public class EndPhaseService {
    public static void EndActionPhase(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        StatusHelper.AnnounceStatusPhase(game);
        if (!game.isOmegaPhaseMode()) {
            StatusHelper.BeginScoring(event, game, gameChannel);
        } else {
            StartPhaseService.startStatusHomework(event, game);
        }
        StatusHelper.HandleStatusPhaseMiddle(event, game, gameChannel);
    }
}
