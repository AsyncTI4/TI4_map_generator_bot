package ti4.buttons.handlers.omega_phase;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.omega_phase.VoiceOfTheCouncilHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;

public class ElectVoiceOfTheCouncilButtonHandler {
    @ButtonHandler("elect_voice_of_the_council")
    public static void electVoiceOfTheCouncil(ButtonInteractionEvent event, Game game) {
        VoiceOfTheCouncilHelper.RevealVoiceOfTheCouncil(game, event);
        ButtonHelper.deleteMessage(event);
    }
}
