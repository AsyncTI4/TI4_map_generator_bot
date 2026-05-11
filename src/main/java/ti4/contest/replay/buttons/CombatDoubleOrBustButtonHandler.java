package ti4.contest.replay.buttons;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.CombatDoubleOrBustService;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@UtilityClass
class CombatDoubleOrBustButtonHandler {

    @ButtonHandler(value = CombatDoubleOrBustButtonIds.PREFIX, save = false)
    public static void toggleDoubleOrBust(ButtonInteractionEvent event, String buttonId) {
        try {
            Long contestId = CombatDoubleOrBustButtonIds.parse(buttonId);
            CombatDoubleOrBustService.ToggleResult result =
                    SpringContext.getBean(CombatDoubleOrBustService.class).toggle(event, contestId);
            event.getHook().editOriginal(result.message()).queue(Consumers.nop(), BotLogger::catchRestError);
        } catch (IllegalArgumentException e) {
            event.getHook()
                    .editOriginal("This Double or Bust button is malformed.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.error(event, "Unexpected Double or Bust button failure", e);
            event.getHook()
                    .editOriginal("Double or Bust failed. Please try again or report this if it keeps happening.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }
}
