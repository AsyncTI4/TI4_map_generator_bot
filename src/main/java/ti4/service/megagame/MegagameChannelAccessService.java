package ti4.service.megagame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;

@UtilityClass
public class MegagameChannelAccessService {

    public static void channelReport(Game game, Player player, Member gm, Consumer<String> reply) {
        Guild guild = game.getGuild();
        if (guild == null) {
            reply.accept("Could not find the server for this game.");
            return;
        }
        Member member = player.getMember();
        if (member == null) {
            reply.accept(player.getRepresentationUnfoggedNoPing() + " is not on this server (or not cached).");
            return;
        }
        ThreadChannel ownCardsInfo = player.getExistingCardsInfoThread();
        List<CompletableFuture<ThreadChannel>> privateThreadChecks = guild.getThreadChannels().stream()
                .filter(thread -> !thread.isPublic())
                .filter(thread -> member.hasAccess(thread.getParentChannel()))
                .filter(thread -> !isSomeoneElsesCardsInfo(thread, ownCardsInfo))
                .map(thread -> joinedOrNull(thread, member))
                .toList();
        CompletableFuture.allOf(privateThreadChecks.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    List<ThreadChannel> threads = new ArrayList<>(guild.getThreadChannels().stream()
                            .filter(thread -> thread.isPublic() && member.hasAccess(thread))
                            .toList());
                    privateThreadChecks.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .forEach(threads::add);
                    reply.accept(format(guild, player, member, threads, gmAccessHolder(guild, gm)));
                })
                .exceptionally(error -> {
                    BotLogger.error("Could not list megagame channels for " + player.getUserName(), error);
                    reply.accept("Could not list the channels, see the bot log.");
                    return null;
                });
    }

    private static boolean isSomeoneElsesCardsInfo(ThreadChannel thread, ThreadChannel ownCardsInfo) {
        boolean cardsInfo = thread.getName().startsWith(Constants.CARDS_INFO_THREAD_PREFIX)
                || thread.getName().contains("-cards-info-");
        return cardsInfo && (ownCardsInfo == null || ownCardsInfo.getIdLong() != thread.getIdLong());
    }

    private static CompletableFuture<ThreadChannel> joinedOrNull(ThreadChannel thread, Member member) {
        if (thread.getThreadMember(member) != null) {
            return CompletableFuture.completedFuture(thread);
        }
        return thread.retrieveThreadMember(member)
                .submit()
                .handle((threadMember, error) -> error == null ? thread : null);
    }

    private static String format(
            Guild guild, Player player, Member member, List<ThreadChannel> threads, IPermissionHolder gmAccess) {
        List<GuildChannel> channels = guild.getChannels().stream()
                .filter(channel -> !(channel instanceof Category))
                .filter(member::hasAccess)
                .toList();
        StringBuilder sb = new StringBuilder("## Channels ")
                .append(player.getRepresentationUnfoggedNoPing())
                .append(" can see (")
                .append(channels.size())
                .append(" channels, ")
                .append(threads.size())
                .append(" active threads)\n");
        sb.append("**Private channels**\n");
        appendChannels(
                sb,
                channels.stream().filter(channel -> isPrivate(guild, channel)).toList(),
                threads,
                gmAccess);
        sb.append("**Public channels**\n");
        appendChannels(
                sb,
                channels.stream().filter(channel -> !isPrivate(guild, channel)).toList(),
                threads,
                gmAccess);
        sb.append("-# Archived threads are not listed.");
        return sb.toString();
    }

    private static boolean isPrivate(Guild guild, GuildChannel channel) {
        return !guild.getPublicRole().hasAccess(channel);
    }

    private static void appendChannels(
            StringBuilder sb, List<GuildChannel> channels, List<ThreadChannel> threads, IPermissionHolder gmAccess) {
        if (channels.isEmpty()) {
            sb.append("> None\n");
            return;
        }
        for (GuildChannel channel : channels) {
            sb.append("> ").append(link(channel, gmAccess)).append('\n');
            threads.stream()
                    .filter(thread -> thread.getParentChannel().getIdLong() == channel.getIdLong())
                    .sorted(Comparator.comparing(ThreadChannel::getName))
                    .forEach(thread -> sb.append(">   ↳ ")
                            .append(link(thread, gmAccess))
                            .append(!thread.isPublic() ? " (private thread)" : "")
                            .append('\n'));
        }
    }

    private static IPermissionHolder gmAccessHolder(Guild guild, Member gm) {
        return guild.getRolesByName(MegagameGmAccess.GM_ROLE_NAME, true).stream()
                .<IPermissionHolder>map(role -> role)
                .findFirst()
                .orElse(gm);
    }

    private static String link(GuildChannel channel, IPermissionHolder gmAccess) {
        if (gmAccess != null && gmCanOpen(channel, gmAccess)) {
            return channel.getAsMention();
        }
        return "`#" + channel.getName() + "` (GM role has no access)";
    }

    private static boolean gmCanOpen(GuildChannel channel, IPermissionHolder gmAccess) {
        if (channel instanceof ThreadChannel thread && !thread.isPublic()) {
            GuildChannel parent = thread.getParentChannel();
            return gmAccess.hasAccess(parent) && gmAccess.hasPermission(parent, Permission.MANAGE_THREADS);
        }
        return gmAccess.hasAccess(channel);
    }
}
