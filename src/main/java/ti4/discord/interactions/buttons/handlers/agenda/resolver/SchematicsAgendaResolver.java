package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.message.MessageHelper;

public class SchematicsAgendaResolver implements AgendaResolver {
    @Override
    public String agendaId() {
        return "schematics";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            for (Player player : game.getRealPlayers()) {
                if (player.getTechs().contains("ws")
                        || player.getTechs().contains("pws2")
                        || player.getTechs().contains("dsrohdws")) {
                    ActionCardHelper.discardRandomAC(event, game, player, player.getAcCount());
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Discarded the action cards of those that own the war sun technology.");
        }
    }
}
