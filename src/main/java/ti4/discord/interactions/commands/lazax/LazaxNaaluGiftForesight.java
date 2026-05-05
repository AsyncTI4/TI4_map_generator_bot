package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.house.naalu.CombatReplayNaaluAbilityService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.spring.context.SpringContext;

class LazaxNaaluGiftForesight extends Subcommand {

    LazaxNaaluGiftForesight() {
        super("naalu_gift_foresight", "Repost open Naalu Gift of Foresight voting buttons.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!LazaxCommandAuthorization.isInHouseChannel(event, CombatReplayHouse.NAALU)) {
            LazaxReplyHelper.replyEphemeral(event, "Use this command in `#lazax-naalu`.");
            return;
        }
        if (!LazaxCommandAuthorization.canUseHouseCommand(event, CombatReplayHouse.NAALU)) {
            LazaxReplyHelper.replyEphemeral(event, "Only Naalu Delegation may repost Gift of Foresight controls.");
            return;
        }

        boolean posted =
                SpringContext.getBean(CombatReplayNaaluAbilityService.class).repostOpenGiftOfForesightButtons();
        LazaxReplyHelper.replyEphemeral(
                event,
                posted
                        ? "Reposted Naalu Gift of Foresight voting controls."
                        : "The Naalu Gift of Foresight voting window is not open.");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }
}
