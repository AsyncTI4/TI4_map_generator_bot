package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.house.mentak.CombatReplayMentakAbilityService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.spring.context.SpringContext;

class LazaxMentakFalseColors extends Subcommand {

    LazaxMentakFalseColors() {
        super("mentak_false_colors", "Repost open Mentak False Colors voting buttons.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!LazaxCommandAuthorization.isInHouseChannel(event, CombatReplayHouse.MENTAK)) {
            LazaxReplyHelper.replyEphemeral(event, "Use this command in `#lazax-mentak`.");
            return;
        }
        if (!LazaxCommandAuthorization.canUseHouseCommand(event, CombatReplayHouse.MENTAK)) {
            LazaxReplyHelper.replyEphemeral(event, "Only Mentak Delegation may repost False Colors controls.");
            return;
        }

        boolean posted =
                SpringContext.getBean(CombatReplayMentakAbilityService.class).repostOpenFalseColorsVotingButtons();
        LazaxReplyHelper.replyEphemeral(
                event,
                posted
                        ? "Reposted Mentak False Colors voting controls."
                        : "The Mentak False Colors voting window is not open.");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }
}
