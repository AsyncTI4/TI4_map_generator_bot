package ti4.discord.interactions.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.service.leader.UnlockLeaderService;

class UnlockLeader extends LeaderSubcommand {

    public UnlockLeader() {
        super(Constants.UNLOCK_LEADER, "Unlock leader");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        UnlockLeaderService.unlockLeader(leaderID, getGame(), getPlayer());
    }
}
