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
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.service.game.CreateGameService;

import static java.util.function.Predicate.not;

@UtilityClass
public class AddBothelperPermissionsCron {

    public static void register() {
        CronManager.schedulePeriodically(AddBothelperPermissionsCron.class, AddBothelperPermissionsCron::handleActiveGames, 5, 60, TimeUnit.MINUTES);
    }

    public static void scheduleForGame(Game game) {
        if (game == null) return;
        CronManager.scheduleOnce(AddBothelperPermissionsCron.class, () -> addPermissions(game), 5, TimeUnit.MINUTES);
    }

    private static void handleActiveGames() {
        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .map(ManagedGame::getGame)
            .forEach(AddBothelperPermissionsCron::addPermissions);
    }

    private static void addPermissions(Game game) {
        if (game == null) return;
        if (!game.getStoredValue("addedBothelpers").isEmpty() || game.isFowMode()) {
            return;
        }
        game.setStoredValue("addedBothelpers", "Yes");
        GameManager.save(game, "adding bothelper permissions");

        Guild guild = game.getGuild();
        if (guild == null) {
            return;
        }

        Role bothelperRole = CreateGameService.getRole("Bothelper", guild);
        if (bothelperRole == null) {
            return;
        }

        long threadPermission = Permission.MANAGE_THREADS.getRawValue();
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
        if (actionsChannel != null) {
            for (Member botHelper : nonGameBothelpers) {
                actionsChannel.getManager()
                    .putMemberPermissionOverride(botHelper.getIdLong(), threadPermission, 0)
                    .complete();
            }
        }
    }
}
