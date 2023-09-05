package ti4;

import java.util.*;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.BotLogger;

public class UserJoinServerListener extends ListenerAdapter {
    
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            checkIfNewUserIsInExistingGameAndAutoAddRole(event.getGuild(), event.getUser());
        } catch (Exception e) {
            BotLogger.log("Error in `UserJoinServerListener.onGuildMemberJoin`", e);
        }
    }

    private void checkIfNewUserIsInExistingGameAndAutoAddRole(Guild guild, User user) {
        List<Map> mapsJoined = new ArrayList<>();
        for (Map map : MapManager.getInstance().getMapList().values()) {
            if (map.getPlayers().containsKey(user.getId())) {
                mapsJoined.add(map);
                Helper.fixGameChannelPermissions(guild, map);
            }
        }
        BotLogger.log("User:" + user.getName() + " joined server: " + guild.getName() + ". Maps joined:\n> " + mapsJoined.stream().map(Map::getName).toList());
    }
}
