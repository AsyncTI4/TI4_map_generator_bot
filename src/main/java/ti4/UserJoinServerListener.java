package ti4;

import java.util.*;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;

public class UserJoinServerListener extends ListenerAdapter {
    
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        checkIfNewUserIsInExistingGameAndAutoAddRole(event.getGuild(), event.getUser());
    }

    private void checkIfNewUserIsInExistingGameAndAutoAddRole(Guild guild, User user) {
        List<Map> mapsJoined = new ArrayList<>();
        for (Map map : MapManager.getInstance().getMapList().values()) {
            if (map.getPlayers().containsKey(user.getId())) {
                mapsJoined.add(map);
                Helper.fixGameChannelPermissions(guild, map);
            }
        }
    }
}
