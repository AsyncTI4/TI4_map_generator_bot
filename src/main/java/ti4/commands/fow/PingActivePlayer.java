package ti4.commands.fow;

import java.util.Date;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PingActivePlayer extends FOWSubcommandData {
    
    
    public PingActivePlayer() {
        super(Constants.PING_ACTIVE_PLAYER, "Ping the active player in this game");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        String playerID = activeMap.getActivePlayer();
        if (playerID == null) {
            MessageHelper.replyToSlashCommand(event, "There is no active player right now.");
            return;
        }
        Player player = activeMap.getPlayer(playerID);
        if (player == null) {
            MessageHelper.replyToSlashCommand(event, "There is no active player right now.");
            return;
        }

        long milliSinceLastPing = new Date().getTime() - activeMap.getLastActivePlayerPing().getTime();
        if (milliSinceLastPing < (1000 * 60 * 60 * 8)) { //eight hours
            MessageHelper.replyToSlashCommand(event, "Active player was pinged recently. Try again later.");
        } else {
            String ping = Helper.getPlayerRepresentation(event, player, true) + " IS STILL UP FOR AN ACTION";
            if(activeMap.isFoWMode()) {
                MessageHelper.replyToSlashCommand(event, "Active player has been pinged.");
                MessageHelper.sendPrivateMessageToPlayer(player, activeMap, ping);
            } else {
                MessageHelper.replyToSlashCommand(event, ping);
            }
            activeMap.setLastActivePlayerPing(new Date());
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        return;
    }
}