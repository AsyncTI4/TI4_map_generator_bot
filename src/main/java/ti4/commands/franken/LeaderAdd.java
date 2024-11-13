package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LeaderAdd extends LeaderAddRemove {

    public LeaderAdd() {
        super(Constants.LEADER_ADD, "Add a leader to your faction");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FAKE_COMMANDERS, "Any of these added commanders do not apply to Alliance or Imperia", false));
    }

    @Override
    public void doAction(Player player, List<String> leaderIDs, SlashCommandInteractionEvent event) {
        addLeaders(event, player, leaderIDs);
    }

    public static void addLeaders(GenericInteractionCreateEvent event, Player player, List<String> leaderIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added leaders:\n");
        Boolean fakeCommanders = false;
        if (event instanceof SlashCommandInteractionEvent slash) {
            fakeCommanders = slash.getOption(Constants.FAKE_COMMANDERS, OptionMapping::getAsBoolean);
        }
        for (String leaderID : leaderIDs) {
            sb.append(getAddLeaderText(player, leaderID));
            player.addLeader(leaderID);
            if (fakeCommanders != null && fakeCommanders) {
                player.getGame().addFakeCommander(leaderID);
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    public static String getAddLeaderText(Player player, String leaderID) {
        StringBuilder sb = new StringBuilder();
        if (player.hasLeader(leaderID)) {
            sb.append("> ").append(leaderID).append(" (player had this leader)");
        } else {
            Leader leader = new Leader(leaderID);
            sb.append("> ").append(Helper.getLeaderFullRepresentation(leader));
        }
        sb.append("\n");
        return sb.toString();
    }
}
