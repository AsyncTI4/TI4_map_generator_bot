package ti4.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.DisasterWatchHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

public class EconomicEqualityAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "economic_equality";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        int finalTG = "for".equalsIgnoreCase(winner) ? 5 : 0;
        int maxLoss = 12;
        List<Player> comrades = new ArrayList<>();
        for (Player playerB : game.getRealPlayers()) {
            if (playerB.getTg() > maxLoss) {
                maxLoss = playerB.getTg();
                comrades = new ArrayList<>();
                comrades.add(playerB);
            } else if (playerB.getTg() == maxLoss) {
                comrades.add(playerB);
            }
            playerB.setTg(finalTG);
            if (finalTG > 0) {
                ButtonHelperAgents.resolveArtunoCheck(playerB, finalTG);
                ButtonHelperAbilities.pillageCheck(playerB, game);
            }
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                game.getPing() + ", all players' trade goods have been set to " + finalTG + ".");
        if (!comrades.isEmpty()) {
            for (Player playerB : comrades) {
                DisasterWatchHelper.sendMessageInDisasterWatch(
                        game,
                        "The Galactic Council of " + game.getName() + " have generously volunteered "
                                + playerB.getRepresentation() + " to donate " + maxLoss
                                + " trade goods to the less economically fortunate citizens of the galaxy.");
            }
            DisasterWatchHelper.sendMessageInDisasterWatch(game, MiscEmojis.tg(maxLoss));
        }
    }
}
