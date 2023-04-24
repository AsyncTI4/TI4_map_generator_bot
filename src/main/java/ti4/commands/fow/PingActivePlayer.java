package ti4.commands.fow;

import java.util.Date;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.map.MapSaveLoadManager;

public class PingActivePlayer extends FOWSubcommandData {
    
    
    public PingActivePlayer() {
        super(Constants.PING_ACTIVE_PLAYER, "Ping the active player in this game");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        String playerID = activeMap.getActivePlayer();
        if (playerID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There is no active player right now.");
            return;
        }
        Player player = activeMap.getPlayer(playerID);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There is no active player right now.");
            return;
        }
        Player playerOrig = activeMap.getPlayer(getUser().getId());
        playerOrig = Helper.getGamePlayer(activeMap, player, event, null);
        long milliSinceLastPing = new Date().getTime() - activeMap.getLastActivePlayerPing().getTime();
        boolean samePlayer = false;
        if(playerOrig != null)
        {
            samePlayer = playerOrig.getUserID().equalsIgnoreCase(player.getUserID());
        }

        if (milliSinceLastPing < (1000 * 60 * 60 * 8) && !samePlayer) { //eight hours
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Active player was pinged recently. Try again later.");
        } else {
            String ping = Helper.getPlayerRepresentation(event, player, true) + " this is a gentle reminder that it is your turn.";
            if(activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Active player has been pinged.");
                MessageHelper.sendPrivateMessageToPlayer(player, activeMap, ping);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ping);
            }
            activeMap.setLastActivePlayerPing(new Date());
            MapSaveLoadManager.saveMap(activeMap);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        return;
    }
}