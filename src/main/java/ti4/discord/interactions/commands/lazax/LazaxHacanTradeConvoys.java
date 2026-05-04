package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.spring.context.SpringContext;

class LazaxHacanTradeConvoys extends Subcommand {

    LazaxHacanTradeConvoys() {
        super("hacan_trade_convoys", "Repost open Hacan Trade Convoys voting buttons.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!LazaxCommandAuthorization.isInHouseChannel(event, CombatReplayHouse.HACAN)) {
            LazaxReplyHelper.replyEphemeral(event, "Use this command in `#lazax-hacan`.");
            return;
        }
        if (!LazaxCommandAuthorization.canUseHouseCommand(event, CombatReplayHouse.HACAN)) {
            LazaxReplyHelper.replyEphemeral(event, "Only Hacan Delegation may repost Trade Convoys controls.");
            return;
        }

        boolean posted = SpringContext.getBean(CombatReplayHacanTradeConvoysService.class)
                .repostOpenTradeConvoysVotingButtons();
        LazaxReplyHelper.replyEphemeral(
                event,
                posted
                        ? "Reposted Hacan Trade Convoys voting controls."
                        : "The Hacan Trade Convoys voting window is not open.");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }
}
