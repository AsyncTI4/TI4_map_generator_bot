package ti4.buttons.handlers.phases;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StatusHelper;
import ti4.listeners.annotations.ButtonHandler;

@UtilityClass
class ProceedToScoringButtonHandler {
    @ButtonHandler("proceed_to_scoring")
    public void proceedToScoring(ButtonInteractionEvent event, Game game) {
        StatusHelper.beginScoring(event, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }
}
