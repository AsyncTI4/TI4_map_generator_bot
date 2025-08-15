package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

public class AbsolMeasuresAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "absol_measures";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            for (Player playerWL : AgendaHelper.getWinningVoters(winner, game)) {
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
                                playerWL.getRepresentationUnfogged() + ", please discard an action card.",
                                ActionCardHelper.getDiscardActionCardButtons(playerWL, false));
                    } else {
                        ActionCardHelper.sendActionCardInfo(game, playerWL, event);
                    }
                }
                CommanderUnlockCheckService.checkPlayer(playerWL, "yssaril");
                ButtonHelper.checkACLimit(game, playerWL);
            }
            for (Player p2 : AgendaHelper.getLosingVoters(winner, game)) {
                p2.setStrategicCC(p2.getStrategicCC());
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "Drew 2 action cards for each of the players who voted \"for\" and placed 1 command token in the strategy pool of each player that voted \"against\".");
        }
    }
}
