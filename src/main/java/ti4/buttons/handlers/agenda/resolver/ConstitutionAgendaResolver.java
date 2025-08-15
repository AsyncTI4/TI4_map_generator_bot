package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ConstitutionAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "constitution";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        java.util.List<String> laws =
                new java.util.ArrayList<>(game.getLaws().keySet());
        for (String law : laws) {
            game.removeLaw(law);
        }
        game.setStoredValue("agendaConstitution", "true");
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "# Removed all laws, will exhaust all home planets at the start of next Strategy Phase.");
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {}
}
