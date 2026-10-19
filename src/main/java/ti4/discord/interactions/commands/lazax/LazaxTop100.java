package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.contest.replay.service.CombatReplayLeaderboardService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;

class LazaxTop100 extends Subcommand {

    LazaxTop100() {
        super(Constants.LAZAX_TOP_100, "Show the top 100 Lazax War Archives leaderboard.");
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.PUBLIC, "True to post in the channel. Default false sends only to you."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean publicMessage = event.getOption(Constants.PUBLIC, Boolean.FALSE, OptionMapping::getAsBoolean);
        String message =
                SpringContext.getBean(CombatReplayLeaderboardService.class).buildTop100LeaderboardMessage();
        if (publicMessage) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return;
        }
        LazaxReplyHelper.replyEphemeral(event, message);
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return !event.getOption(Constants.PUBLIC, Boolean.FALSE, OptionMapping::getAsBoolean);
    }
}
