package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.unit.UnitQueryService;

public class ArmsReductionAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String agendaId() {
        return "arms_reduction";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            List<Button> removeButtons = new ArrayList<>();
            String message = "";

            int excessCruisers = UnitQueryService.countUnits(game, player, "cruiser", false) - 4;
            if (excessCruisers > 0) {
                removeButtons.addAll(
                        ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "cruiser", true));
                message = player.toString() + ", please remove "
                        + StringHelper.pluralize(excessCruisers, "excess cruiser");
            }

            int excessDreadnoughts = UnitQueryService.countUnits(game, player, "dreadnought", false) - 2;
            if (excessDreadnoughts > 0) {
                removeButtons.addAll(
                        ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "dreadnought", true));
                if (message.isEmpty()) {
                    message = player.toString() + ", please remove "
                            + StringHelper.pluralize(excessDreadnoughts, "excess dreadnought");
                } else {
                    message += player.toString() + " and "
                            + StringHelper.pluralize(excessDreadnoughts, "excess dreadnought");
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

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        game.setStoredValue("agendaArmsReduction", "true");
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "# Will exhaust all planets with a technology specialty at the start of next Strategy Phase.");
    }
}
