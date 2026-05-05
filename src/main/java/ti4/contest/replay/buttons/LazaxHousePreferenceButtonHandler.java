package ti4.contest.replay.buttons;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

@UtilityClass
public class LazaxHousePreferenceButtonHandler {

    @ButtonHandler(value = CombatReplayHouseService.TOGGLE_HOUSE_ROLE_BUTTON_ID, save = false)
    public static void toggleHouseRole(ButtonInteractionEvent event) {
        String message = SpringContext.getBean(CombatReplayHouseService.class)
                .toggleHouseRole(event.getGuild(), event.getMember(), event.getUser());
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }

    @ButtonHandler(value = CombatReplayHouseService.TOGGLE_HOUSE_OPT_IN_BUTTON_ID, save = false)
    public static void toggleHouseOptIn(ButtonInteractionEvent event) {
        String message = SpringContext.getBean(CombatReplayHouseService.class)
                .toggleHouseOptIn(event.getGuild(), event.getMember(), event.getUser());
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }
}
