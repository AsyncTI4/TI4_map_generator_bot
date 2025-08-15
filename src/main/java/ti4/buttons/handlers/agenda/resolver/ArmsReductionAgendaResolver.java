package ti4.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ArmsReductionAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "arms_reduction";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            handleFor(game);
        } else {
            handleAgainst(game);
        }
    }

    private void handleFor(Game game) {
        for (Player player : game.getRealPlayers()) {
            List<Button> removeButtons = new ArrayList<>();
            String message = "";

            int excessCruisers = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) - 4;
            if (excessCruisers > 0) {
                removeButtons.addAll(ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "cruiser"));
                message = player.getRepresentation() + ", please remove " + excessCruisers + " excess cruiser"
                        + (excessCruisers == 1 ? "" : "s");
            }

            int excessDreadnoughts = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) - 2;
            if (excessDreadnoughts > 0) {
                removeButtons.addAll(ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "cruiser"));
                if (message.isEmpty()) {
                    message = player.getRepresentation() + ", please remove " + excessDreadnoughts
                            + " excess dreadnought" + (excessDreadnoughts == 1 ? "" : "s");
                } else {
                    message += player.getRepresentation() + " and " + excessDreadnoughts + " excess dreadnought"
                            + (excessDreadnoughts == 1 ? "" : "s");
                }
            }

            if (!removeButtons.isEmpty()) {
                message += ".";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, removeButtons);
            }
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "Sent buttons for each player to remove excess dreadnoughts and cruisers.");
    }

    private void handleAgainst(Game game) {
        game.setStoredValue("agendaArmsReduction", "true");
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "# Will exhaust all planets with a technology specialty at the start of next Strategy Phase.");
    }
}
