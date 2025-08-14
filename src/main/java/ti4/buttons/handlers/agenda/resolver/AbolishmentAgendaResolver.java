package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class AbolishmentAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "abolishment";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "# Abolished the " + Mapper.getAgendaTitleNoCap(winner) + " law");
        game.removeLaw(winner);
    }
}
