package ti4.contest.replay.house.hacan;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatReplayHacanManageVoteButtonHandler {

    @ButtonHandler(CombatReplayHacanAbilityService.MANAGE_VOTE_BUTTON_PREFIX)
    public static void handleManageHacanVote(ButtonInteractionEvent event, String buttonId) {
        SpringContext.getBean(CombatReplayHacanAbilityService.class).sendEphemeralVoteControls(event, buttonId);
    }

    @ButtonHandler(CombatReplayHacanAbilityService.MANAGE_MARKET_COMPACT_BUTTON_PREFIX)
    public static void handleManageMarketCompact(ButtonInteractionEvent event, String buttonId) {
        SpringContext.getBean(CombatReplayHacanAbilityService.class)
                .sendEphemeralMarketCompactControls(event, buttonId);
    }

    @ButtonHandler(CombatReplayHacanAbilityService.MANAGE_TRADE_CONVOYS_BUTTON_PREFIX)
    public static void handleManageTradeConvoys(ButtonInteractionEvent event, String buttonId) {
        SpringContext.getBean(CombatReplayHacanAbilityService.class).sendEphemeralTradeConvoysControls(event, buttonId);
    }
}
