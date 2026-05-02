package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

public record MiscountMessageAgendaResolver(String agendaId) implements AgendaResolver {

    public MiscountMessageAgendaResolver() {
        this("miscount");
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        MessageHelper.sendMessageToChannel(
            game.getMainGameChannel(),
            "# Repealed the _" + Mapper.getAgendaTitleNoCap(winner)
                +
                "_ law.\nIt will will now be revealed for the purposes of revoting.\n-# It is technically still in effect during the revote, if relevant.");
    }
}
