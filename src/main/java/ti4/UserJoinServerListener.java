package ti4;

import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class UserJoinServerListener extends ListenerAdapter {

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            checkIfNewUserIsInExistingGamesAndAutoAddRole(event.getGuild(), event.getUser());
        } catch (Exception e) {
            BotLogger.log("Error in `UserJoinServerListener.onGuildMemberJoin`", e);
        }
    }

    @Override
    public void onGuildMemberRemove(@NonNull GuildMemberRemoveEvent event) {
        try {
            checkIfUserLeftActiveGames(event.getGuild(), event.getUser());
        } catch (Exception e) {
            BotLogger.log("Error in `UserJoinServerListener.onGuildMemberRemove`", e);
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
            for (Game g : mapsJoined) {
                String gameMessage = user.getAsMention() + " has joined the server!";
                MessageHelper.sendMessageToChannel(g.getTableTalkChannel(), gameMessage);
            }
            // Should be obsolete now
            // BotLogger.log("User: *" + user.getName() + "* joined server: **" + guild.getName() + "**. Maps joined: " + mapsJoined.stream().map(Game::getName).toList());
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
        MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), "All users have now joined the server! Let the games begin!");
        MessageHelper.sendMessageToChannel(threadChannel, "All users have joined the game, this thread will now be closed.");
        threadChannel.getManager().setArchived(true).queue();
    }

    private void checkIfUserLeftActiveGames(Guild guild, User user) {
        List<Game> gamesQuit = new ArrayList<>();
        for (Game game : GameManager.getInstance().getGameNameToGame().values()) {
            if (game.isHasEnded()) continue;
            if (game.getGuild() != null && game.getGuild().equals(guild) && game.getPlayers().containsKey(user.getId())) {
                gamesQuit.add(game);
            }
        }
        if (!gamesQuit.isEmpty()) {
            String msg = "User " + user.getName() + " has left the server " + guild.getName() + " with the following in-progress games:";
            for (Game g : gamesQuit) {
                String gameMessage = "Attention " + g.getPing() + ": " + user.getName() + " has left the server.";
                MessageHelper.sendMessageToChannel(g.getTableTalkChannel(), gameMessage);
                msg += "\n> " + g.getName() + " -> Link:" + g.getTableTalkChannel().getJumpUrl();
            }
            reportUserLeftServer(msg);
        }
    }

    private static void reportUserLeftServer(String message) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary
            .getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null)
            return;
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels.isEmpty())
            return;
        String threadName = "in-progress-games-left";
        // SEARCH FOR EXISTING OPEN THREAD
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                MessageHelper.sendMessageToChannel(threadChannel_, message);
                return;
            }
        }
    }
}
