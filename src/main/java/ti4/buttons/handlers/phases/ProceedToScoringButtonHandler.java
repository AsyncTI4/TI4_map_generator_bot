package ti4.buttons.handlers.phases;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StatusHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;

@UtilityClass
class ProceedToScoringButtonHandler {
    @ButtonHandler("proceed_to_scoring")
    public void proceedToScoring(ButtonInteractionEvent event, Game game) {
        StatusHelper.BeginScoring(event, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }
}
