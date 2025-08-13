package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class UnconventionalAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "unconventional";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        List<Player> winOrLose;
        if (!"for".equalsIgnoreCase(winner)) {
            winOrLose = AgendaHelper.getLosingVoters(winner, game);
            for (Player playerWL : winOrLose) {
                ActionCardHelper.discardRandomAC(event, game, playerWL, playerWL.getAc());
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Discarded the action cards of those who voted \"for\".");
        } else {
            winOrLose = AgendaHelper.getWinningVoters(winner, game);
            for (Player playerWL : winOrLose) {
                if (playerWL.hasAbility("autonetic_memory")) {
                    ButtonHelperAbilities.autoneticMemoryStep1(game, playerWL, 2);
                } else {
                    game.drawActionCard(playerWL.getUserID());
                    game.drawActionCard(playerWL.getUserID());
                    if (playerWL.hasAbility("scheming")) {
                        game.drawActionCard(playerWL.getUserID());
                        ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                        MessageHelper.sendMessageToChannelWithButtons(
                                playerWL.getCardsInfoThread(),
                                playerWL.getRepresentationUnfogged() + " use buttons to discard",
                                ActionCardHelper.getDiscardActionCardButtons(playerWL, false));
                    } else {
                        ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                    }
                }
                ti4.service.leader.CommanderUnlockCheckService.checkPlayer(playerWL, "yssaril");
                ti4.helpers.ButtonHelper.checkACLimit(game, playerWL);
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Drew 2 action cards for each of the players who voted \"for\".");
        }
    }
}
