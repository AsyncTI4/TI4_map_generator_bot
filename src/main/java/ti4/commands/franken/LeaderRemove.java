package ti4.commands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenLeaderService;

class LeaderRemove extends LeaderAddRemove {

    public LeaderRemove() {
        super(Constants.LEADER_REMOVE, "Remove a leader from your faction");
    }

    @Override
    public void doAction(Player player, List<String> leaderIDs, SlashCommandInteractionEvent event) {
        FrankenLeaderService.removeLeaders(event, player, leaderIDs);
    }
}
