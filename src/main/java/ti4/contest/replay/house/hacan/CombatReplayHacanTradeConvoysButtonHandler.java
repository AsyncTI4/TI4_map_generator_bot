package ti4.contest.replay.house.hacan;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService.ParsedTradeConvoysButton;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatReplayHacanTradeConvoysButtonHandler {

    @ButtonHandler(CombatReplayHacanTradeConvoysService.HACAN_TRADE_CONVOYS_PREFIX)
    public static void handleHacanTradeConvoys(ButtonInteractionEvent event, String buttonId) {
        try {
            ParsedTradeConvoysButton parsed = CombatReplayHacanTradeConvoysService.parseButtonId(buttonId);
            CombatReplayHacanTradeConvoysService.VoteResult result = SpringContext.getBean(
                            CombatReplayHacanTradeConvoysService.class)
                    .recordTradeConvoysVote(event, parsed);
            MessageHelper.sendEphemeralMessageToEventChannel(event, result.message());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not read that Hacan Trade Convoys vote.");
        }
    }
}
