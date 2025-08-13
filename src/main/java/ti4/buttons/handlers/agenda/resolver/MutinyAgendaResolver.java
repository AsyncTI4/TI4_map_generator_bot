package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class MutinyAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "mutiny";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        boolean agendaWentFor = "for".equalsIgnoreCase(winner);
        List<Player> winningOrLosingPlayers = agendaWentFor
                ? AgendaHelper.getWinningVoters(winner, game)
                : AgendaHelper.getLosingVoters(winner, game);
        if (winningOrLosingPlayers.isEmpty()) {
            return;
        }

        Integer poIndex = game.addCustomPO("Mutiny", agendaWentFor ? 1 : -1);

        StringBuilder message = new StringBuilder();
        message.append("Custom objective _Mutiny_ has been added.\n");
        for (var winningOrLosingPlayer : winningOrLosingPlayers) {
            if (winningOrLosingPlayer.getTotalVictoryPoints() < 1 && !agendaWentFor) {
                continue;
            }
            game.scorePublicObjective(winningOrLosingPlayer.getUserID(), poIndex);
            if (!game.isFowMode()) {
                message.append(winningOrLosingPlayer.getRepresentation()).append(" scored _Mutiny_.\n");
            }
            Helper.checkEndGame(game, winningOrLosingPlayer);
            if (winningOrLosingPlayer.getTotalVictoryPoints() >= game.getVp()) {
                break;
            }
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
    }
}
