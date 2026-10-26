package ti4.service.game;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;

@UtilityClass
public class PrivateThreadVisibilityService {

    public static final long THREAD_MANAGEMENT = Permission.MANAGE_THREADS.getRawValue();

    public static void hideOtherPlayersPrivateThreads(Guild guild, Game game) {
        for (IPermissionContainer channel : gameChannels(game)) {
            for (Player player : game.getPlayers().values()) {
                Member member = guild.getMemberById(player.getUserID());
                if (member == null || !needsThreadManagementDenied(member, channel)) continue;
                if (deniesThreadManagement(channel.getPermissionOverride(member))) continue;
                channel.upsertPermissionOverride(member)
                        .deny(Permission.MANAGE_THREADS)
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
    }

    public static void restoreThreadManagement(Game game, @Nullable Member formerPlayer) {
        if (formerPlayer == null) return;
        for (IPermissionContainer channel : gameChannels(game)) {
            if (!deniesThreadManagement(channel.getPermissionOverride(formerPlayer))) continue;
            channel.upsertPermissionOverride(formerPlayer)
                    .clear(Permission.MANAGE_THREADS)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static boolean needsThreadManagementDenied(Member member, IPermissionContainer channel) {
        if (member.hasPermission(Permission.ADMINISTRATOR)) return false;
        return member.hasPermission(channel, Permission.MANAGE_THREADS)
                || deniesThreadManagement(channel.getPermissionOverride(member));
    }

    private static boolean deniesThreadManagement(@Nullable PermissionOverride override) {
        return override != null && override.getDenied().contains(Permission.MANAGE_THREADS);
    }

    private static Set<IPermissionContainer> gameChannels(Game game) {
        Set<IPermissionContainer> channels = new LinkedHashSet<>();
        Stream.of(game.getTableTalkChannel(), game.getMainGameChannel())
                .filter(Objects::nonNull)
                .forEach(channels::add);
        return channels;
    }
}
