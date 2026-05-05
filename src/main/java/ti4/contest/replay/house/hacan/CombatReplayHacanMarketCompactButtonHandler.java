package ti4.contest.replay.house.hacan;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.buttons.CombatSideBetButtonIds.Parsed;
import ti4.contest.replay.service.CombatReplayInteractionResult;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatReplayHacanMarketCompactButtonHandler {

    @ButtonHandler(CombatReplayHacanMarketCompactService.BUTTON_PREFIX)
    public static void handleMarketCompact(ButtonInteractionEvent event, String buttonId) {
        try {
            Parsed parsed = CombatReplayHacanMarketCompactService.parseButtonId(buttonId);
            CombatReplayInteractionResult result = SpringContext.getBean(CombatReplayHacanMarketCompactService.class)
                    .recordMarketVote(event, parsed.contestId(), parsed.betType(), parsed.targetFaction());
            MessageHelper.sendEphemeralMessageToEventChannel(event, result.message());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not read that Hacan market vote.");
        }
    }
}
