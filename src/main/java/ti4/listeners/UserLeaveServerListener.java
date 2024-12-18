package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.ExecutorManager;
import ti4.helpers.Helper;
import ti4.helpers.ThreadGetter;
import ti4.helpers.ToStringHelper;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.map.manage.ManagedPlayer;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.ManagedGameService;

public class UserLeaveServerListener extends ListenerAdapter {

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        if (!validateEvent(event)) return;
        ExecutorManager.runAsync("UserLeaveServerListener task", () -> handleGuildMemberRemove(event));
    }

    private void handleGuildMemberRemove(GuildMemberRemoveEvent event) {
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

    private static boolean validateEvent(GenericGuildEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            return false;
        }
        String eventGuild = event.getGuild().getId();
        return AsyncTI4DiscordBot.isValidGuild(eventGuild);
    }

    private void checkIfUserLeftActiveGames(Guild guild, User user, boolean voluntary) {
        List<ManagedGame> gamesQuit = new ArrayList<>();

        ManagedPlayer player = GameManager.getManagedPlayer(user.getId());
        for (ManagedGame game : player.getGames()) {
            if (game.isHasEnded() || game.isVpGoalReached()) continue;
            Guild gameGuild = game.getGuild();
            if (gameGuild != null && gameGuild.equals(guild)) {
                gamesQuit.add(game);
            }
        }

        if (!gamesQuit.isEmpty()) {
            StringBuilder msg = new StringBuilder("User " + user.getName() + " has left the server " + guild.getName() + " with the following in-progress games:");
            for (ManagedGame game : gamesQuit) {
                logManagedGame(game);//TODO: can be removed after debugging user leaving issues
                String gameMessage = "Attention " + ManagedGameService.getPingAllPlayers(game) + ": " + user.getName();
                if (voluntary) gameMessage += " has left the server.\n> If this was not a mistake, you may make ";
                if (!voluntary) gameMessage += " was removed from the server.\n> Make ";
                gameMessage += "a post in https://discord.com/channels/943410040369479690/1176191865188536500 to get a replacement player";
                MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), gameMessage);
                msg.append("\n> ").append(game.getName()).append(" -> Link:").append(game.getTableTalkChannel().getJumpUrl());
            }
            reportUserLeftServer(msg.toString());

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

    private void logManagedGame(ManagedGame managedGame) {
        BotLogger.log("ManagedGame of quit game: " +
            ToStringHelper.of(ManagedGame.class)
                .add("gameName", managedGame.getGame())
                .add("lastModifiedDate", managedGame.getLastModifiedDate())
                .add("guild", managedGame.getGuild().getName())
                .add("vpGoalReached", managedGame.isVpGoalReached())
                .add("hasEnded", managedGame.isHasEnded()));
    }

    private static void reportUserLeftServer(String message) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null) return;
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels.isEmpty()) return;
        String threadName = "in-progress-games-left";
        ThreadGetter.getThreadInChannel(bothelperLoungeChannel, threadName,
            threadChannel -> MessageHelper.sendMessageToChannel(threadChannel, message));
    }
}
