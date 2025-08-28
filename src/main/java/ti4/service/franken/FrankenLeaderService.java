package ti4.service.franken;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.leader.HeroUnlockCheckService;

@UtilityClass
public class FrankenLeaderService {

    public static void addLeaders(GenericInteractionCreateEvent event, Player player, List<String> leaderIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added leaders:\n");
        Boolean fakeCommanders = false;
        if (event instanceof SlashCommandInteractionEvent slash) {
            fakeCommanders = slash.getOption(Constants.FAKE_COMMANDERS, OptionMapping::getAsBoolean);
        }
        for (String leaderID : leaderIDs) {
            sb.append(getAddLeaderText(player, leaderID));
            player.addLeader(leaderID);
            LeaderModel leaderModel = Mapper.getLeader(leaderID);
            if ("hero".equals(leaderModel.getType())) {
                HeroUnlockCheckService.checkIfHeroUnlocked(player.getGame(), player);
            }
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

    public static void removeLeaders(GenericInteractionCreateEvent event, Player player, List<String> leaderIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed leaders:\n");
        for (String leaderID : leaderIDs) {
            if (!player.hasLeader(leaderID)) {
                sb.append("> ").append(leaderID).append(" (player did not have this leader)");
            } else {
                Leader leader = new Leader(leaderID);
                sb.append("> ").append(Helper.getLeaderFullRepresentation(leader));
            }
            sb.append("\n");
            player.removeLeader(leaderID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
