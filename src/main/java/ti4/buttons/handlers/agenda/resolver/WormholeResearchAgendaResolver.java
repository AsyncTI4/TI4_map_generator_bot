package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class WormholeResearchAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "wormhole_research";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            AgendaHelper.doResearch(event, game);
        } else {
            for (Player player : AgendaHelper.getWinningVoters(winner, game)) {
                String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
                Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc", "Lose 1 Tactic Token");
                Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet Token");
                Button loseStrat =
                        Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc", "Lose 1 Strategy Token");
                Button done = Buttons.red(finsFactionCheckerPrefix + "deleteButtons", "Done Losing Command Tokens");
                var buttons = java.util.List.of(loseTactic, loseFleet, loseStrat, done);
                String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                        + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
        }
    }
}
