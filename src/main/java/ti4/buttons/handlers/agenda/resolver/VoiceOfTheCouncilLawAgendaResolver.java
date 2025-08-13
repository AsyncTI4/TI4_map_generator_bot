package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.omega_phase.VoiceOfTheCouncilHelper;
import ti4.map.Game;
import ti4.map.Player;

public class VoiceOfTheCouncilLawAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return Constants.VOICE_OF_THE_COUNCIL_ID;
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        VoiceOfTheCouncilHelper.ElectVoiceOfTheCouncil(game, player2);
    }
}
