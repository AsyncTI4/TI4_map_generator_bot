package ti4.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.ExecutorManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.GameLaunchThreadHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class UserJoinServerListener extends ListenerAdapter {

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        if (!validateEvent(event)) return;
        ExecutorManager.runAsync("UserJoinServerListener task", () -> handleGuildMemberJoin(event));
    }

    private static boolean validateEvent(GenericGuildEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            return false;
        }
        String eventGuild = event.getGuild().getId();
        return AsyncTI4DiscordBot.isValidGuild(eventGuild);
    }

    private void handleGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            if (event.getGuild() == AsyncTI4DiscordBot.guildPrimary) {
                TextChannel welcomeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("welcome-and-waving", true).stream()
                    .findFirst().orElse(null);
                if (welcomeChannel != null) {
                    MessageHelper.sendMessageToChannel(welcomeChannel, "**Welcome** " + event.getUser().getAsMention() + "! We're glad you're here as lucky number #" + event.getGuild().getMemberCount() + "!\n"
                        + "To get started, check out the how to play documentation here: https://discord.com/channels/943410040369479690/947727176105623642/1349555940340404265. \n"
                        + "If you ever have any questions or difficulty, ping the Bothelper role. It's full of helpful people who should be able to assist you.");
                }
            }
            checkIfNewUserIsInExistingGamesAndAutoAddRole(event.getGuild(), event.getUser());
        } catch (Exception e) {
            BotLogger.error("Error in `UserJoinServerListener.onGuildMemberJoin`", e);
        }
    }

    private void checkIfNewUserIsInExistingGamesAndAutoAddRole(Guild guild, User user) {
        List<ManagedGame> mapsJoined = new ArrayList<>();

        for (ManagedGame game : GameManager.getManagedGames()) {
            boolean isInGame = checkIfNewUserIsInExistingGameAndAutoAddRole(game, guild, user);
            if (isInGame) mapsJoined.add(game);
        }

        if (mapsJoined.isEmpty()) {
            return;
        }
        for (ManagedGame managedGame : mapsJoined) {
            String gameMessage = user.getAsMention() + " has joined the server!";
            MessageHelper.sendMessageToChannel(managedGame.getTableTalkChannel(), gameMessage);
            Game game = managedGame.getGame();
            GameLaunchThreadHelper.checkIfCanCloseGameLaunchThread(game, true);
        }
    }

    private static boolean checkIfNewUserIsInExistingGameAndAutoAddRole(ManagedGame managedGame, Guild guild, User user) {
        var gameGuild = managedGame.getGuild();
        if (gameGuild == null || !gameGuild.equals(guild) || !managedGame.hasPlayer(user.getId())) {
            return false;
        }
        Game game = managedGame.getGame();
        Helper.fixGameChannelPermissions(guild, game);
        ThreadChannel mapThread = game.getBotMapUpdatesThread();
        if (mapThread != null && !mapThread.isLocked()) {
            mapThread.getManager().setArchived(false).queue(success -> mapThread.addThreadMember(user).queueAfter(5, TimeUnit.SECONDS), BotLogger::catchRestError);
        }
        var player = game.getPlayer(user.getId());
        if (player == null || !ButtonHelper.isPlayerNew(player.getUserID()) || game.getTableTalkChannel() == null || game.isFowMode()) {
            return true;
        }
        String msg = user.getAsMention() + " ping here";
        List<ThreadChannel> threadChannels = game.getTableTalkChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equalsIgnoreCase("Info for new players")) {
                MessageHelper.sendMessageToChannel(threadChannel_, msg);
            }
        }
        return true;
    }
}
