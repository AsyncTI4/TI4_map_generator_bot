package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ArmsReductionAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "arms_reduction";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            for (Player player : game.getRealPlayers()) {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) > 4) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation() + " remove excess cruisers",
                            ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "cruiser"));
                }
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) > 2) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation() + " remove excess dreadnoughts",
                            ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "dreadnought"));
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "Sent buttons for each player to remove excess dreadnoughts and cruisers.");
        } else {
            game.setStoredValue("agendaArmsReduction", "true");
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "# Will exhaust all planets with a technology specialty  at the start of next Strategy Phase.");
        }
    }
}
