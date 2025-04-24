package ti4.buttons.handlers.steps_and_phases;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StatusHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class ProceedToScoringButtonHandler {
    @ButtonHandler("proceed_to_scoring")
    public void proceedToScoring(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        StatusHelper.BeginScoring(event, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }
}
