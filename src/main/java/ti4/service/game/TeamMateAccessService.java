package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.utility.DiscordChannelUtility;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;

@UtilityClass
public class TeamMateAccessService {

    private static final long CHANNEL_PERMISSIONS =
            Permission.PIN_MESSAGES.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();

    public static boolean isOnGameServer(Game game, String userId) {
        Guild guild = game.getGuild();
        return guild != null && guild.getMemberById(userId) != null;
    }

    public static void grantAccess(Game game, Player seat, String userId) {
        Member member = teammateMember(game, seat, userId);
        if (member == null) {
            return;
        }
        List<RestAction<?>> accessActions;
        try {
            accessActions = accessActions(game, seat, member);
        } catch (RuntimeException e) {
            BotLogger.error(new LogOrigin(game), "Could not give teammate " + userId + " access to the game", e);
            return;
        }

        ThreadChannel cardsInfo = findCardsInfoThread(game, seat);
        if (cardsInfo == null) {
            accessActions.forEach(TeamMateAccessService::queue);
            return;
        }
        if (accessActions.isEmpty()) {
            queueThreadAction(game, cardsInfo, thread -> thread.addThreadMember(member));
            return;
        }
        queue(RestAction.allOf(accessActions)
                .flatMap(_ -> onUnarchived(cardsInfo, thread -> thread.addThreadMember(member))));
    }

    public static void revokeAccess(Game game, Player seat, String userId) {
        Member member = teammateMember(game, seat, userId);
        if (member == null) {
            return;
        }
        try {
            removeAccess(game, seat, member);
        } catch (RuntimeException e) {
            BotLogger.error(new LogOrigin(game), "Could not remove game access from teammate " + userId, e);
        }

        ThreadChannel cardsInfo = findCardsInfoThread(game, seat);
        if (cardsInfo != null) {
            queueThreadAction(game, cardsInfo, thread -> thread.removeThreadMemberById(userId));
        }
    }

    static boolean keepsGameAccess(Game game, Player seat, String userId) {
        return game.getPlayer(userId) != null
                || game.getPlayers().values().stream()
                        .filter(player -> player != seat)
                        .anyMatch(player -> player.getTeamMateIDs().contains(userId));
    }

    private static Member teammateMember(Game game, Player seat, String userId) {
        if (game.isHasEnded() || userId.equals(seat.getUserID())) {
            return null;
        }
        Guild guild = game.getGuild();
        return guild == null ? null : guild.getMemberById(userId);
    }

    private static List<RestAction<?>> accessActions(Game game, Player seat, Member member) {
        List<RestAction<?>> actions = new ArrayList<>();
        Role gameRole = findGameRole(member.getGuild(), game);
        if (gameRole != null) {
            actions.add(member.getGuild().addRoleToMember(member, gameRole));
        } else {
            gameChannels(game).forEach(channel -> actions.add(allowMember(channel, member)));
        }
        TextChannel privateChannel = seat.getPrivateChannel();
        if (privateChannel != null) {
            actions.add(allowMember(privateChannel, member));
        }
        return actions;
    }

    private static void removeAccess(Game game, Player seat, Member member) {
        removeMemberOverride(seat.getPrivateChannel(), member);
        if (keepsGameAccess(game, seat, member.getId())) {
            return;
        }
        Role gameRole = findGameRole(member.getGuild(), game);
        if (gameRole != null) {
            queue(member.getGuild().removeRoleFromMember(member, gameRole));
        }
        gameChannels(game).forEach(channel -> removeMemberOverride(channel, member));
    }

    private static ThreadChannel findCardsInfoThread(Game game, Player seat) {
        if (game.isFowMode() && seat.getPrivateChannel() == null) {
            return null;
        }
        try {
            return seat.getExistingCardsInfoThread();
        } catch (RuntimeException e) {
            BotLogger.warning(new LogOrigin(game), "Could not look up the cards info thread for a teammate", e);
            return null;
        }
    }

    private static void queueThreadAction(
            Game game, ThreadChannel thread, Function<ThreadChannel, RestAction<Void>> threadAction) {
        try {
            queue(onUnarchived(thread, threadAction));
        } catch (RuntimeException e) {
            BotLogger.warning(new LogOrigin(game), "Could not update teammate membership of " + thread.getName(), e);
        }
    }

    private static RestAction<Void> onUnarchived(
            ThreadChannel thread, Function<ThreadChannel, RestAction<Void>> threadAction) {
        if (!thread.isArchived()) {
            return threadAction.apply(thread);
        }
        return thread.getManager()
                .setArchived(false)
                .flatMap(_ -> DiscordChannelUtility.retrieveThreadChannelById(thread.getGuild(), thread.getId())
                        .useCache(false))
                .flatMap(threadAction);
    }

    private static Role findGameRole(Guild guild, Game game) {
        List<Role> roles = guild.getRolesByName(game.getName(), true);
        return roles.size() == 1 ? roles.getFirst() : null;
    }

    private static Stream<TextChannel> gameChannels(Game game) {
        return Stream.of(game.getActionsChannel(), game.getTableTalkChannel())
                .filter(Objects::nonNull)
                .distinct();
    }

    private static RestAction<?> allowMember(TextChannel channel, Member member) {
        return channel.getManager().putMemberPermissionOverride(member.getIdLong(), CHANNEL_PERMISSIONS, 0);
    }

    private static void removeMemberOverride(TextChannel channel, Member member) {
        PermissionOverride override = channel == null ? null : channel.getPermissionOverride(member);
        if (override != null) {
            queue(override.delete());
        }
    }

    private static void queue(RestAction<?> action) {
        action.queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
