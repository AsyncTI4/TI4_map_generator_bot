package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RefreshLeader extends LeaderAction {
    public RefreshLeader() {
        super(Constants.REFRESH_LEADER, "Ready leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leaderID);
        if (playerLeader != null){
            if (playerLeader.isLocked()){
                sendMessage("Leader is locked");
                return;
            }
            int tgCount = playerLeader.getTgCount();
            refreshLeader(player, playerLeader, activeMap);
            sendMessage(Helper.getFactionLeaderEmoji(playerLeader));
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                    .append(" readied ")
                    .append(Helper.getLeaderShortRepresentation(playerLeader));
            if (tgCount > 0) {
                message.append(" - ").append(String.valueOf(tgCount)).append(Emojis.tg).append(" transferred from leader to player");
            }
            sendMessage(message.toString());
        } else {
            sendMessage("Leader not found");
        }
    }

    public static void refreshLeader(Player player, Leader playerLeader, Map activeMap) {
        int tgCount = playerLeader.getTgCount();
        playerLeader.setExhausted(false);
        if (tgCount > 0) {
            int tg = player.getTg();
            tg += tgCount;
            player.setTg(tg);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), ButtonHelper.getTrueIdentity(player, activeMap) + " you gained "+tgCount + " tgs ("+(tg-tgCount)+"->"+tg+") from "+playerLeader.getId() + " being readied");
            ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
            playerLeader.setTgCount(0);
        }
    }
}
