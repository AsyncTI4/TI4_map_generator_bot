package ti4.contest.replay.house.mentak;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

@UtilityClass
public class CombatReplayMentakAbilityButtonHandler {

    @ButtonHandler(CombatReplayMentakAbilityService.MENTAK_MANAGE_VOTE_PREFIX)
    public static void handleManageMentakVote(ButtonInteractionEvent event, String buttonId) {
        CombatReplayMentakAbilityService service = SpringContext.getBean(CombatReplayMentakAbilityService.class);
        if (!service.userHasHouse(event.getUser().getId())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only Mentak Delegation may fly false colors.");
            return;
        }
        service.sendEphemeralVoteControls(event, buttonId);
    }

    @ButtonHandler(CombatReplayMentakAbilityService.MENTAK_DECOY_PREFIX)
    public static void handleMentakDecoy(ButtonInteractionEvent event, String buttonId) {
        CombatReplayMentakAbilityService service = SpringContext.getBean(CombatReplayMentakAbilityService.class);
        if (!service.userHasHouse(event.getUser().getId())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only Mentak Delegation may fly false colors.");
            return;
        }

        if (buttonId.startsWith(CombatReplayMentakAbilityService.MENTAK_DO_NOT_USE)) {
            Long candidateId = doNotUseCandidateId(buttonId);
            if (candidateId == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "Could not read the Mentak false-colors request.");
                return;
            }
            CombatReplayMentakAbilityService.VoteResult result = service.voteDoNotUse(
                    candidateId, event.getUser().getId(), event.getUser().getName());
            MessageHelper.sendEphemeralMessageToEventChannel(event, result.message());
            return;
        }

        DecoyRequest request = parse(buttonId);
        if (request == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not read the Mentak false-colors request.");
            return;
        }

        CombatReplayMentakAbilityService.VoteResult result = service.voteDecoy(
                request.candidateId(),
                request.targetFaction(),
                request.unitType(),
                event.getUser().getId(),
                event.getUser().getName());
        MessageHelper.sendEphemeralMessageToEventChannel(event, result.message());
    }

    private static DecoyRequest parse(String buttonId) {
        String request = buttonId.replace(CombatReplayMentakAbilityService.MENTAK_DECOY_PREFIX, "");
        String[] parts = request.split("_", 3);
        if (parts.length != 3) return null;
        try {
            UnitType unitType = Units.findUnitType(parts[2]);
            if (unitType == null) return null;
            return new DecoyRequest(Long.parseLong(parts[0]), parts[1], unitType);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long doNotUseCandidateId(String buttonId) {
        String candidateId = buttonId.replace(CombatReplayMentakAbilityService.MENTAK_DO_NOT_USE, "");
        try {
            return Long.parseLong(candidateId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record DecoyRequest(long candidateId, String targetFaction, UnitType unitType) {}
}
