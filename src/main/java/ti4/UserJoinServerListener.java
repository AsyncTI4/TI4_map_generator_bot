package ti4;

import java.util.*;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.BotLogger;

public class UserJoinServerListener extends ListenerAdapter {
    
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            checkIfNewUserIsInExistingGamesAndAutoAddRole(event.getGuild(), event.getUser());
        } catch (Exception e) {
            BotLogger.log("Error in `UserJoinServerListener.onGuildMemberJoin`", e);
        }
    }

    private void checkIfNewUserIsInExistingGamesAndAutoAddRole(Guild guild, User user) {
        List<Game> mapsJoined = new ArrayList<>();
        for (Game game : GameManager.getInstance().getGameNameToGame().values()) {
            if (game.getGuild() != null && game.getGuild().equals(guild) && game.getPlayers().containsKey(user.getId())) {
                mapsJoined.add(game);
                Helper.fixGameChannelPermissions(guild, game);
                game.getBotMapUpdatesThread().addThreadMember(user).queueAfter(5, TimeUnit.SECONDS);
            }
        }
        if (!mapsJoined.isEmpty()) BotLogger.log("User: *" + user.getName() + "* joined server: **" + guild.getName() + "**. Maps joined:\n> " + mapsJoined.stream().map(Game::getName).toList());
    }
}
