package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

public class MutinyAgendaResolver implements AgendaResolver {
    private static final String BASE_OBJECTIVE_NAME = "Mutiny";

    @Override
    public String agendaId() {
        return "mutiny";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        boolean agendaWentFor = "for".equalsIgnoreCase(winner);
        List<Player> winningOrLosingPlayers = agendaWentFor
                ? AgendaHelper.getWinningVoters(winner, game)
                : AgendaHelper.getLosingVoters(winner, game);
        if (winningOrLosingPlayers.isEmpty()) {
            return;
        }

        String objectiveName = nextMutinyObjectiveName(game);
        Integer poIndex = game.addCustomPO(objectiveName, agendaWentFor ? 1 : -1);

        StringBuilder message = new StringBuilder();
        message.append("Custom objective _").append(objectiveName).append("_ has been added.\n");
        for (var winningOrLosingPlayer : winningOrLosingPlayers) {
            if (winningOrLosingPlayer.getTotalVictoryPoints() < 1 && !agendaWentFor) {
                continue;
            }
            game.scorePublicObjective(winningOrLosingPlayer.getUserID(), poIndex);
            if (!game.isFowMode()) {
                message.append(winningOrLosingPlayer.getRepresentation())
                        .append(" scored _")
                        .append(objectiveName)
                        .append("_.\n");
            }
            Helper.checkEndGame(game, winningOrLosingPlayer);
            if (winningOrLosingPlayer.getTotalVictoryPoints() >= game.getVp()) {
                break;
            }
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
    }

    private static String nextMutinyObjectiveName(Game game) {
        String name = BASE_OBJECTIVE_NAME;
        int occurrence = 1;
        while (game.getRevealedPublicObjectives().containsKey(name)) {
            occurrence++;
            name = BASE_OBJECTIVE_NAME + " " + occurrence;
        }
        return name;
    }
}
