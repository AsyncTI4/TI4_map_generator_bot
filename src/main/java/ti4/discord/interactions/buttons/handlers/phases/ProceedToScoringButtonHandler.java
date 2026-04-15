package ti4.discord.interactions.buttons.handlers.phases;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StatusHelper;

@UtilityClass
class ProceedToScoringButtonHandler {
    @ButtonHandler("proceed_to_scoring")
    public void proceedToScoring(ButtonInteractionEvent event, Game game) {
        StatusHelper.beginScoring(event, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }
}
