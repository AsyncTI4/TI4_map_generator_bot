package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SeedEmpireAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "seed_empire";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        List<Player> winOrLose;
        Integer poIndex;
        poIndex = game.addCustomPO("Seed of an Empire", 1);
        if ("for".equalsIgnoreCase(winner)) {
            winOrLose = AgendaHelper.getPlayersWithMostPoints(game);
        } else {
            winOrLose = AgendaHelper.getPlayersWithLeastPoints(game);
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "Custom objective _Seed of an Empire_ has been added.");
        for (Player playerWL : winOrLose) {
            game.scorePublicObjective(playerWL.getUserID(), poIndex);
            MessageHelper.sendMessageToChannel(
                    playerWL.getCorrectChannel(), playerWL.getRepresentation() + " scored _Seed of an Empire_.");
            Helper.checkEndGame(game, playerWL);
            if (playerWL.getTotalVictoryPoints() >= game.getVp()) {
                break;
            }
        }
    }
}
