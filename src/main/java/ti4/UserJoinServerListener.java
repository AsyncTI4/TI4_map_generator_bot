package ti4;

import java.util.*;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
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
                if (game.getBotMapUpdatesThread() != null) {
                    game.getBotMapUpdatesThread().addThreadMember(user).queueAfter(5, TimeUnit.SECONDS);
                }
                checkIfCanCloseGameLaunchThread(game);
            }
        }
        if (!mapsJoined.isEmpty()) {
            BotLogger.log("User: *" + user.getName() + "* joined server: **" + guild.getName() + "**. Maps joined: " + mapsJoined.stream().map(Game::getName).toList());
        }
    }

    private void checkIfCanCloseGameLaunchThread(Game game) {
        Guild guild = game.getGuild();
        if (guild == null) {
            return;
        }
        List<String> guildMemberIDs = guild.getMembers().stream().map(ISnowflake::getId).toList();
        for (String playerIDs : game.getPlayerIDs()) {
            if (!guildMemberIDs.contains(playerIDs)) {
                return;
            }
        }
        String threadID = game.getLaunchPostThreadID();
        if (threadID == null) {
            return;
        }
        ThreadChannel threadChannel = AsyncTI4DiscordBot.guildPrimary.getThreadChannelById(threadID);
        if (threadChannel == null) {
            return;
        }
        threadChannel.getManager().setArchived(true).queue();
        BotLogger.log("`UserJoinServerListener.checkIfCanCloseGameLaunchThread()` closed launch thread: `" + threadChannel.getName() + "` for game: `" + game.getName() + "`");
    }
}
