package ti4.ai.discord;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.ai.fallback.AiConfusionService;
import ti4.ai.fallback.DelegatedPress;
import ti4.ai.fallback.DelegationChoice;
import ti4.ai.perception.AiPerception;
import ti4.ai.runtime.AiRuntime;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class AiSeatPickButtonHandler {

    @ButtonHandler(value = AiPerception.DELEGATION_PREFIX, save = false)
    public static void pick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<DelegationChoice> choice = DelegationChoice.parse(buttonID);
        if (game == null || player == null || choice.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This choice is no longer valid.");
            return;
        }
        if (!AiConfusionService.claim(event.getMessageId())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Another player already chose for the AI.");
            return;
        }
        String chooser = player.getRepresentationNoPing();
        String label = event.getButton().getLabel();
        event.getMessage()
                .editMessage(event.getMessage().getContentRaw() + "\n⏳ " + chooser + " chose **" + label + "**…")
                .setComponents(List.of())
                .queue(null, BotLogger::catchRestError);
        AiRuntime.enqueueDelegatedPress(new DelegatedPress(
                game.getName(), choice.get(), chooser, label, event.getChannel().getId(), event.getMessageId()));
    }
}
