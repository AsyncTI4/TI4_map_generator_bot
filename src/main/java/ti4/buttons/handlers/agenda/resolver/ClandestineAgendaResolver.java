package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ClandestineAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "cladenstine";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
            Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc", "Lose 1 Tactic Token");
            Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet Token");
            Button loseStrat =
                    Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc", "Lose 1 Strategy Token");
            Button done = Buttons.red(finsFactionCheckerPrefix + "deleteButtons", "Done Losing Command Tokens");
            List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, done);
            String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                    + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            String message = player.getRepresentation() + ", you've lost a command token from your fleet pool.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            player.setFleetCC(player.getFleetCC() - 1);
            ti4.helpers.ButtonHelper.checkFleetInEveryTile(player, game);
        }
    }
}
