package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SchematicsAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "schematics";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            for (Player player : game.getRealPlayers()) {
                if (player.getTechs().contains("ws")
                        || player.getTechs().contains("pws2")
                        || player.getTechs().contains("dsrohdws")) {
                    ActionCardHelper.discardRandomAC(event, game, player, player.getAc());
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Discarded the action cards of those that own the war sun technology.");
        }
    }
}
