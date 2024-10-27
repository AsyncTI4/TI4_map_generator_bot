package ti4.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class UserJoinServerListener extends ListenerAdapter {

    private static boolean validateEvent(GenericGuildEvent event) {
        String eventGuild = event.getGuild().getId();
        List<String> asyncGuilds = AsyncTI4DiscordBot.guilds.stream().map(Guild::getId).toList();
        if (!asyncGuilds.contains(eventGuild)) {
            // Do not process these events in guilds that we aren't initialized in
            return false;
        }
        return true;
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        if (!validateEvent(event)) return;
        try {
            checkIfNewUserIsInExistingGamesAndAutoAddRole(event.getGuild(), event.getUser());
        } catch (Exception e) {
            BotLogger.log("Error in `UserJoinServerListener.onGuildMemberJoin`", e);
        }
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        if (!validateEvent(event)) return;
        try {
            event.getGuild().retrieveAuditLogs().queueAfter(1, TimeUnit.SECONDS, (logs) -> {
                boolean voluntary = true;
                for (AuditLogEntry log : logs) {
                    if (log.getTargetIdLong() == event.getUser().getIdLong()) {
                        if (log.getType() == ActionType.BAN || log.getType() == ActionType.KICK) {
                            voluntary = false;
                            break;
                        }
                    }
                }

                checkIfUserLeftActiveGames(event.getGuild(), event.getUser(), voluntary);
            }, BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.log("Error in `UserJoinServerListener.onGuildMemberRemove`", e);
        }
    }

    private void checkIfNewUserIsInExistingGamesAndAutoAddRole(Guild guild, User user) {
        List<Game> mapsJoined = new ArrayList<>();
        for (Game game : GameManager.getInstance().getGameNameToGame().values()) {
            Guild gameGuild = game.getGuild();
            if (gameGuild != null && gameGuild.equals(guild) && game.getPlayers().containsKey(user.getId())) {
                mapsJoined.add(game);
                Helper.fixGameChannelPermissions(guild, game);
                ThreadChannel mapThread = game.getBotMapUpdatesThread();
                if (mapThread != null && !mapThread.isLocked()) {
                    mapThread.getManager().setArchived(false).queue(success -> {
                        mapThread.addThreadMember(user).queueAfter(5, TimeUnit.SECONDS);
                    }, BotLogger::catchRestError);
                }
                Player player = game.getPlayer(user.getId());
                if (player != null && ButtonHelper.isPlayerNew(game, player)) {
                    String msg = player.getRepresentation() + " ping here";
                    if (game.getTableTalkChannel() != null) {
                        List<ThreadChannel> threadChannels = game.getTableTalkChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equalsIgnoreCase("Info for new players")) {
                                MessageHelper.sendMessageToChannel(threadChannel_, msg);
                            }
                        }
                    }
                }
            }
        }
        if (!mapsJoined.isEmpty()) {
            for (Game g : mapsJoined) {
                String gameMessage = user.getAsMention() + " has joined the server!";
                MessageHelper.sendMessageToChannel(g.getTableTalkChannel(), gameMessage);
                checkIfCanCloseGameLaunchThread(g, true);
            }
        }
    }

    public static void checkIfCanCloseGameLaunchThread(Game game, boolean notify) {
        Guild guild = game.getGuild();
        if (guild == null) {
            return;
        }
        String threadID = game.getLaunchPostThreadID();
        if (threadID == null) {
            return;
        }
        ThreadChannel threadChannel = AsyncTI4DiscordBot.guildPrimary.getThreadChannelById(threadID);
        if (threadChannel == null) {
            return;
        }
        List<String> guildMemberIDs = guild.getMembers().stream().map(ISnowflake::getId).toList();
        for (String playerIDs : game.getPlayerIDs()) {
            if (!guildMemberIDs.contains(playerIDs)) {
                return;
            }
        }
        if (notify) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), game.getPing() + " all users have now joined the server! Let the games begin!");
            MessageHelper.sendMessageToChannel(threadChannel, "All users have joined the game, this thread will now be closed.");
        }
        threadChannel.getManager().setArchived(true).queue();
    }

    private void checkIfUserLeftActiveGames(Guild guild, User user, boolean voluntary) {
        List<Game> gamesQuit = new ArrayList<>();
        for (Game game : GameManager.getInstance().getGameNameToGame().values()) {
            boolean endVPReachedButNotEnded = game.getPlayers().values().stream().anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp());
            if (game.isHasEnded() || endVPReachedButNotEnded) continue;
            Guild gameGuild = game.getGuild();
            if (gameGuild != null && gameGuild.equals(guild) && game.getPlayers().containsKey(user.getId())) {
                gamesQuit.add(game);
            }
        }
        if (!gamesQuit.isEmpty()) {
            String msg = "User " + user.getName() + " has left the server " + guild.getName() + " with the following in-progress games:";
            for (Game g : gamesQuit) {
                String gameMessage = "Attention " + g.getPing() + ": " + user.getName();
                if (voluntary) gameMessage += " has left the server.\n> If this was not a mistake, you may make ";
                if (!voluntary) gameMessage += " was removed from the server.\n> Make ";
                gameMessage += "a post in https://discord.com/channels/943410040369479690/1176191865188536500 to get a replacement player";
                MessageHelper.sendMessageToChannel(g.getTableTalkChannel(), gameMessage);
                msg += "\n> " + g.getName() + " -> Link:" + g.getTableTalkChannel().getJumpUrl();
            }
            reportUserLeftServer(msg);

            String inviteBack = Helper.getGuildInviteURL(guild, 1);
            String primaryInvite = Helper.getGuildInviteURL(AsyncTI4DiscordBot.guildPrimary, 1, true);
            String usermsg = "It looks like you left a server while playing in `" + gamesQuit.size() + "` games.";
            usermsg += " Please consider making a post in https://discord.com/channels/943410040369479690/1176191865188536500 to get a replacement player.\n\n";
            usermsg += "If this was a mistake, here is an invite back to the server you just left: " + inviteBack + "\n";
            usermsg += "If you are just taking a break, here is an invite to the HUB server that will last until you're ready to come back: " + primaryInvite + "\n\n";
            usermsg += "Take care!\n> - Async TI4 Admin Team";
            if (voluntary) {
                MessageHelper.sendMessageToUser(usermsg, user);
            }
        }
    }

    private static void reportUserLeftServer(String message) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
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
