package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StatusHelper;
import ti4.service.fow.LoreService;

@UtilityClass
public class EndPhaseService {
    public static void EndActionPhase(GenericInteractionCreateEvent event, Game game, MessageChannel gameChannel) {
        LoreService.showPhaseLore(game, "status"); // before StatusHelper mutates phaseOfGame: END lore reads "action"
        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("mandate_of_presence")) {
                game.removeStoredValue("mandateUsedThisActionPhase_" + player.getFaction());
            }
        }
        StatusHelper.announceStatusPhase(game);
        if (!game.isOmegaPhaseMode()) {
            StatusHelper.beginScoring(event, game, gameChannel);
        } else {
            StartPhaseService.startStatusHomework(event, game);
        }
        StatusHelper.handleStatusPhaseMiddle(game);
    }
}
