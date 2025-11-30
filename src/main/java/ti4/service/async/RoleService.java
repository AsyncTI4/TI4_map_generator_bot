package ti4.service.async;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.GameLaunchThreadHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.spring.jda.JdaService;

@UtilityClass
public class RoleService {

    public void checkIfNewUserIsInExistingGamesAndAutoAddRole(Guild guild, User user) {
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

    private boolean checkIfNewUserIsInExistingGameAndAutoAddRole(ManagedGame managedGame, Guild guild, User user) {
        var gameGuild = managedGame.getGuild();
        if (gameGuild == null || !gameGuild.equals(guild) || !managedGame.hasPlayer(user.getId())) {
            return false;
        }
        Game game = managedGame.getGame();
        Helper.fixGameChannelPermissions(guild, game);
        ThreadChannel mapThread = game.getBotMapUpdatesThread();
        if (mapThread != null && !mapThread.isLocked()) {
            mapThread
                    .getManager()
                    .setArchived(false)
                    .queue(
                            success -> mapThread.addThreadMember(user).queueAfter(5, TimeUnit.SECONDS),
                            BotLogger::catchRestError);
        }

        // Ping into new player thread if applicable
        var player = game.getPlayer(user.getId());
        if (player == null
                || !ButtonHelper.isPlayerNew(player.getUserID())
                || game.getTableTalkChannel() == null
                || game.isFowMode()) {
            return true;
        }
        List<ThreadChannel> threadChannels = game.getTableTalkChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (Constants.NEW_PLAYER_THREAD_NAME.equalsIgnoreCase(threadChannel_.getName())) {
                threadChannel_.addThreadMember(user).queueAfter(1, TimeUnit.SECONDS, null, BotLogger::catchRestError);
                String msg = user.getAsMention() + " ping here";
                MessageHelper.sendMessageToChannel(threadChannel_, msg);
            }
        }
        return true;
    }

    public void checkIfNewUserIsInAnyGamesAndAddRole(User user) {
        ManagedPlayer player = GameManager.getManagedPlayer(user.getId());
        if (player != null && !player.getGames().isEmpty()) {
            for (Guild guild : JdaService.guilds) {
                Role role = getAsyncPlayerRole(guild);
                if (guild.getMember(user) != null) {
                    guild.addRoleToMember(user, role).queue();
                }
            }
        }
    }

    private Role getAsyncPlayerRole(Guild guild) {
        List<Role> roles = guild.getRolesByName("AsyncPlayer", false);
        roles.sort(Comparator.comparing(Role::getPosition));
        if (roles.isEmpty()) {
            return guild.createRole()
                    .setName("AsyncPlayer")
                    .setMentionable(false)
                    .setHoisted(false)
                    .setColor(Color.decode("#607d8b"))
                    .complete();
        } else {
            return roles.getFirst();
        }
    }
}
