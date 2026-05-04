package ti4.contest.replay.buttons;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.service.LazaxSeasonService;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

@UtilityClass
public class LazaxSeasonButtonHandler {

    @ButtonHandler(value = LazaxSeasonService.CLAIM_DELEGATION_BUTTON_ID, save = false)
    public static void claimDelegation(ButtonInteractionEvent event) {
        String message = SpringContext.getBean(LazaxSeasonService.class)
                .claimDelegation(event.getUser(), event.getMember(), event.getGuild());
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }
}
