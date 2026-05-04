package ti4.contest.replay.buttons;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.buttons.CombatSideBetButtonIds.Parsed;
import ti4.contest.replay.service.CombatReplaySideBetService;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@UtilityClass
class CombatSideBetButtonHandler {

    @ButtonHandler(value = CombatSideBetButtonIds.PREFIX, save = false)
    public static void placeSideBet(ButtonInteractionEvent event, String buttonId) {
        try {
            Parsed parsed = CombatSideBetButtonIds.parse(buttonId);
            CombatReplaySideBetService.PlacementResult result = SpringContext.getBean(CombatReplaySideBetService.class)
                    .placeSideBet(event, parsed.contestId(), parsed.betType(), parsed.targetFaction());
            if (!result.accepted()) {
                event.getHook().editOriginal(result.message()).queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }

            event.getHook().editOriginal(result.message()).queue(Consumers.nop(), BotLogger::catchRestError);
        } catch (IllegalArgumentException e) {
            event.getHook()
                    .editOriginal("This side bet button is malformed.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.error(event, "Unexpected side bet button failure", e);
            event.getHook()
                    .editOriginal("Side bet failed. Please try again or report this if it keeps happening.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }
}
