package ti4.helpers;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.service.JdaService;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class GameLaunchThreadHelper {

    public static void checkIfCanCloseGameLaunchThread(Game game, boolean notify) {
        Guild guild = game.getGuild();
        if (guild == null) {
            return;
        }
        String threadID = game.getLaunchPostThreadID();
        if (!ButtonHelper.isNumeric(threadID)) {
            return;
        }
        ThreadChannel threadChannel = JdaService.guildPrimary.getThreadChannelById(threadID);
        if (threadChannel == null) {
            return;
        }
        List<String> guildMemberIDs =
                guild.getMembers().stream().map(ISnowflake::getId).toList();
        for (String playerIds : game.getPlayerIDs()) {
            if (!guildMemberIDs.contains(playerIds)) {
                return;
            }
        }
        if (notify) {
            MessageHelper.sendMessageToChannel(
                    game.getTableTalkChannel(),
                    game.getPing() + " all users have now joined the server! Let the games begin!");
            MessageHelper.sendMessageToChannel(
                    threadChannel, "All users have joined the game, this thread will now be closed.");
        }
        threadChannel.getManager().setArchived(true).queueAfter(5, TimeUnit.SECONDS);
    }
}
