package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenLeaderService;

class LeaderAdd extends LeaderAddRemove {

    public LeaderAdd() {
        super(Constants.LEADER_ADD, "Add a leader to your faction");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FAKE_COMMANDERS, "Any of these added commanders do not apply to Alliance or Imperia", false));
    }

    @Override
    public void doAction(Player player, List<String> leaderIDs, SlashCommandInteractionEvent event) {
        FrankenLeaderService.addLeaders(event, player, leaderIDs);
    }
}
