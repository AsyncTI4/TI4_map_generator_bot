package ti4.cron;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import ti4.executors.ExecutionLockManager;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.BotLogger;
import ti4.service.game.CreateGameService;

import static java.util.function.Predicate.not;

@UtilityClass
public class AddBothelperPermissionsCron {

    public static void register() {
        CronManager.scheduleOnce(AddBothelperPermissionsCron.class, AddBothelperPermissionsCron::handleActiveGames, 5, TimeUnit.MINUTES);
    }

    private static void handleActiveGames() {
        BotLogger.info("Running AddBothelperPermissionsCron");

        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .filter(not(ManagedGame::isFowMode))
            .map(ManagedGame::getName)
            .forEach(gameName ->
                    ExecutionLockManager
                        .wrapWithLockAndRelease(gameName, ExecutionLockManager.LockType.WRITE, () -> addPermissions(gameName))
                        .run());

        BotLogger.info("Finished AddBothelperPermissionsCron");
    }

    private static void addPermissions(String gameName) {
        Game game = GameManager.getManagedGame(gameName).getGame();
        BotLogger.info("Adding Bothelper permissions for " + game.getName());
        try {
            handleAddingPermissions(game);
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Adding Bothelper permissions failed for game: " + game.getName(), e);
        }
        BotLogger.info("Finished adding Bothelper permissions for " + game.getName());
    }

    private static void handleAddingPermissions(Game game) {
        Guild guild = game.getGuild();
        if (guild == null) {
            return;
        }

        Role bothelperRole = CreateGameService.getRole("Bothelper", guild);
        if (bothelperRole == null) {
            return;
        }

        List<Member> nonGameBothelpers = new ArrayList<>();
        for (Member botHelper : guild.getMembersWithRoles(bothelperRole)) {
            boolean inGame = false;
            for (Player member : game.getRealPlayers()) {
                if (member.getUserID().equalsIgnoreCase(botHelper.getId())) {
                    inGame = true;
                    break;
                }
            }
            if (!inGame) {
                nonGameBothelpers.add(botHelper);
            }
        }

        TextChannel actionsChannel = game.getMainGameChannel();
        if (actionsChannel == null) {
            return;
        }

        long threadPermission = Permission.MANAGE_THREADS.getRawValue();
        TextChannelManager manager = actionsChannel.getManager();
        for (Member botHelper : nonGameBothelpers) {
            manager = manager.putMemberPermissionOverride(botHelper.getIdLong(), threadPermission, 0);
        }
        manager.queue();
    }
}
