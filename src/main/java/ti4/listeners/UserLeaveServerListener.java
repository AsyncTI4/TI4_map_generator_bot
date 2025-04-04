package ti4.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.ExecutorManager;
import ti4.helpers.Helper;
import ti4.helpers.ThreadGetter;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.map.manage.ManagedPlayer;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

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
            BotLogger.error("Error in `UserJoinServerListener.onGuildMemberRemove`", e);
        }
    }

    private static boolean validateEvent(GenericGuildEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            return false;
        }
        String eventGuild = event.getGuild().getId();
        return AsyncTI4DiscordBot.isValidGuild(eventGuild);
    }

    private static int userTotalGames(ManagedPlayer user) {
        return user.getGames().stream()
            .filter(mg -> !mg.isHasEnded() && !mg.isHasWinner() && !mg.isVpGoalReached())
            .toList().size();
    }

    private static Game gameWasReallyLeft(Guild guild, ManagedPlayer mPlayer, ManagedGame mGame) {
        Guild gameGuild = mGame.getGuild();
        // trivial checks first
        if (mGame.isHasEnded() || mGame.isVpGoalReached()) return null;
        if (gameGuild == null || !gameGuild.getId().equals(guild.getId())) return null;

        // and then checks that require loading the game
        Game game = mGame.getGame();
        Player player = game.getPlayer(mPlayer.getId());
        if (player.isEliminated() || player.isDummy()) return null;
        if (game.isHasHadAStatusPhase() && !player.isRealPlayer()) return null;
        return game;
    }

    private void checkIfUserLeftActiveGames(Guild guild, User user, boolean voluntary) {
        List<Game> gamesQuit = new ArrayList<>();

        ManagedPlayer player = GameManager.getManagedPlayer(user.getId());
        for (ManagedGame game : player.getGames()) {
            Game realGame = gameWasReallyLeft(guild, player, game);
            if (realGame != null) gamesQuit.add(realGame);
        }

        if (!gamesQuit.isEmpty()) {
            for (Game game : gamesQuit) {
                String gameMessage = "Attention " + game.getPing() + ": " + player.getName();
                if (voluntary) gameMessage += " has left the server.\n> If this was not a mistake, you may make ";
                if (!voluntary) gameMessage += " was removed from the server.\n> Make ";
                gameMessage += "a post in https://discord.com/channels/943410040369479690/1176191865188536500 to get a replacement player";
                MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), gameMessage);
            }
            reportUserLeftServer(guild, player, gamesQuit);

            if (voluntary) {
                String inviteBack = Helper.getGuildInviteURL(guild, 1);
                String primaryInvite = Helper.getGuildInviteURL(AsyncTI4DiscordBot.guildPrimary, 1, true);
                String usermsg = "It looks like you left a server while playing in `" + gamesQuit.size() + "` games.";
                usermsg += " Please consider making a post in https://discord.com/channels/943410040369479690/1176191865188536500 to get a replacement player.\n\n";
                usermsg += "If this was a mistake, here is an invite back to the server you just left: " + inviteBack + "\n";
                usermsg += "If you are just taking a break, here is an invite to the HUB server that will last until you're ready to come back: " + primaryInvite + "\n\n";
                usermsg += "Take care!\n> - Async TI4 Admin Team";
                MessageHelper.sendMessageToUser(usermsg, user);
            }
        }
    }

    private static String generateSingleGameReport(ManagedPlayer user, Game game) {
        Player player = game.getPlayer(user.getId());

        // HEADER
        String websiteLink = String.format("[__[%s](https://ti4.westaddisonheavyindustries.com/game/%s)__]", game.getName(), game.getName());
        String faction = player.getFactionEmoji();
        String tabletalkLink = String.format("[__[Tabletalk](%s)__]", game.getTableTalkChannel().getJumpUrl());
        String actionsLink = String.format("[__[Actions](%s)__]", game.getActionsChannel().getJumpUrl());
        String round = "(Round " + game.getRound() + ")";
        String header = String.format("> %s %s %s %s %s", websiteLink, faction, tabletalkLink, actionsLink, round);

        // Last player turn start
        game.getLastActivePlayerChange().getTime();
        String lastTurnStart = "> -- Last turn started <t:" + (game.getLastActivePlayerChange().getTime() / 1000) + ":R>";

        // Some other player stats to show...
        float value = player.getTotalResourceValueOfUnits("space") + player.getTotalResourceValueOfUnits("ground");
        String valueString = "> -- `" + value + "`" + MiscEmojis.resources + " of units, `" + player.getPlanets().size() + "` planets.";

        int mostVPs = game.getRealPlayers().stream().map(Player::getTotalVictoryPoints).max(Integer::compare).orElse(0);
        String vpsStr = "> -- `" + player.getTotalVictoryPoints() + "` VPs -- leader has `" + mostVPs + "`";

        return String.join("\n", header, lastTurnStart, valueString, vpsStr);
    }

    private static String generateBothelperReport(Guild guild, ManagedPlayer player, List<Game> games) {
        StringBuilder msg = new StringBuilder("### __" + player.getName() + "__ left " + guild.getName() + " with in-progress games:");
        String separator = "\n-# > --------------------------------------------------";
        msg.append(separator);
        boolean foundOne = false;
        for (Game g : games) {
            Player p = g.getPlayer(player.getId());
            if(p != null && g.getLastActivePlayerChange().toString() != null){
                float value = p.getTotalResourceValueOfUnits("space") + p.getTotalResourceValueOfUnits("ground");
                if(!foundOne && value > 0 && Helper.getDateDifference(Helper.getDateRepresentation(g.getLastActivePlayerChange().getTime()), Helper.getDateRepresentation(System.currentTimeMillis())) < 15 ){
                    foundOne = true;
                }
            }
            msg.append("\n").append(generateSingleGameReport(player, g));
            msg.append(separator);
        }
        msg.append("\nUser has **__").append(userTotalGames(player)).append("__** in-progress games and **__").append(player.getGames().size()).append("__** lifetime games across all servers.");
        if(!foundOne){
            return "dud";
        }
        return msg.toString();
    }

    private static void reportUserLeftServer(Guild guild, ManagedPlayer player, List<Game> games) {
        TextChannel staffLounge = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (staffLounge == null) return;

        String threadName = "in-progress-games-left";
        try {
            String msg = generateBothelperReport(guild, player, games);
            StringBuilder gs = new StringBuilder();
            if(!msg.equalsIgnoreCase("dud")){
                ThreadGetter.getThreadInChannel(staffLounge, threadName, tc -> MessageHelper.sendMessageToChannel(tc, msg));
            }else{
                for(Game game : games){
                    gs.append(game.getActionsChannel().getJumpUrl()).append("\n");
                }
                final String gss = gs.toString();
                ThreadGetter.getThreadInChannel(staffLounge, threadName, tc -> MessageHelper.sendMessageToChannel(tc, player.getName()+" left some games, but the games were ruled to be duds. Games were as follows: "+gss));
            }
        } catch (Exception e){
            ThreadGetter.getThreadInChannel(staffLounge, threadName, tc -> MessageHelper.sendMessageToChannel(tc, "reportUserLeftServer method hit the following error: "+e.getMessage()));
        }
    }
}
