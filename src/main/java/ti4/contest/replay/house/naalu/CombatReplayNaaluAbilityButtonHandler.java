package ti4.contest.replay.house.naalu;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatReplayNaaluAbilityButtonHandler {

    @ButtonHandler(CombatReplayNaaluAbilityService.NAALU_PEEK_PREFIX)
    public static void handleNaaluPeek(ButtonInteractionEvent event, String buttonId) {
        CombatReplayNaaluAbilityService service = SpringContext.getBean(CombatReplayNaaluAbilityService.class);
        if (!service.userHasHouse(event.getUser().getId())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only Naalu Delegation may use Gift of Foresight.");
            return;
        }

        Long contestId = contestId(buttonId);
        if (contestId == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not read the combat archive id.");
            return;
        }

        if (buttonId.startsWith(CombatReplayNaaluAbilityService.NAALU_ACTION_CARDS)) {
            sendVoteResult(
                    event,
                    service.voteActionCardPeek(
                            contestId, event.getUser().getId(), event.getUser().getName()));
            return;
        }
        if (buttonId.startsWith(CombatReplayNaaluAbilityService.NAALU_ROUND_ONE_ROLLS)) {
            sendVoteResult(
                    event,
                    service.voteRoundOneRollPeek(
                            contestId, event.getUser().getId(), event.getUser().getName()));
            return;
        }
        if (buttonId.startsWith(CombatReplayNaaluAbilityService.NAALU_DO_NOT_USE)) {
            sendVoteResult(
                    event,
                    service.voteDoNotUse(
                            contestId, event.getUser().getId(), event.getUser().getName()));
            return;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, "Unknown Gift of Foresight option.");
    }

    private static void sendVoteResult(
            ButtonInteractionEvent event, CombatReplayNaaluAbilityService.VoteResult result) {
        MessageHelper.sendEphemeralMessageToEventChannel(event, result.message());
    }

    private static Long contestId(String buttonId) {
        int index = buttonId.lastIndexOf('_');
        if (index < 0 || index == buttonId.length() - 1) return null;
        try {
            return Long.parseLong(buttonId.substring(index + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
