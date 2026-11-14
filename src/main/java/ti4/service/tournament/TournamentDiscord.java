package ti4.service.tournament;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.spring.service.tournament.Tournament;

@UtilityClass
public class TournamentDiscord {

    public static void ephemeral(GenericInteractionCreateEvent event, String message) {
        if (event instanceof IReplyCallback) ephemeral(event, message, List.of());
        else MessageHelper.sendMessageToEventChannel(event, message);
    }

    public static void ephemeral(GenericInteractionCreateEvent event, String message, List<FileUpload> files) {
        if (!(event instanceof IReplyCallback callback)) return;
        String text = StringUtils.abbreviate(message, 2000);
        if (callback.isAcknowledged()) {
            callback.getHook()
                    .sendMessage(text)
                    .addFiles(files)
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            callback.reply(text).addFiles(files).setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void ephemeralWithComponents(
            GenericInteractionCreateEvent event, String message, List<ActionRow> rows) {
        if (!(event instanceof IReplyCallback callback)) return;
        String text = StringUtils.abbreviate(message, 2000);
        if (callback.isAcknowledged()) {
            callback.getHook()
                    .sendMessage(text)
                    .setComponents(rows)
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            callback.reply(text)
                    .setComponents(rows)
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static boolean isPublic(GuildMessageChannel channel) {
        if (channel == null) return false;
        return channel.getGuild()
                .getPublicRole()
                .hasPermission(channel.getPermissionContainer(), Permission.VIEW_CHANNEL);
    }

    public static boolean isPanelPublic(Tournament tournament) {
        return isPublic(channel(tournament.getPanelChannelId()));
    }

    public static Guild hostGuild(Tournament tournament) {
        return guild(tournament.getHostGuildId());
    }

    private static Guild guild(String guildId) {
        return StringUtils.isBlank(guildId) ? null : JdaService.jda.getGuildById(guildId);
    }

    public static GuildMessageChannel channel(String channelId) {
        if (StringUtils.isBlank(channelId)) return null;
        return JdaService.jda.getChannelById(GuildMessageChannel.class, channelId);
    }

    public static Category category(Guild guild, String name) {
        return guild.getCategoriesByName(name, true).stream()
                .findFirst()
                .orElseGet(() -> CreateGameService.createGameCategory(guild, name));
    }

    public static Member member(Guild guild, String userId) {
        return guild == null ? null : guild.getMemberById(userId);
    }

    public static Member anyMember(Guild preferred, String userId) {
        Member member = member(preferred, userId);
        if (member != null) return member;
        member = member(JdaService.guildPrimary, userId);
        if (member != null) return member;
        for (Guild guild : JdaService.jda.getGuilds()) {
            member = guild.getMemberById(userId);
            if (member != null) return member;
        }
        return null;
    }

    public static String userName(String userId) {
        User user = JdaService.jda.getUserById(userId);
        return user == null ? "Unknown user " + userId : user.getEffectiveName();
    }

    public static List<String> userNames(List<String> userIds) {
        return userIds.stream().map(TournamentDiscord::userName).toList();
    }

    public static String mentions(List<String> userIds) {
        return String.join(" ", userIds.stream().map(id -> "<@" + id + ">").toList());
    }

    public static List<Guild> lookupGuilds(Tournament tournament) {
        Guild host = hostGuild(tournament);
        if (host == null || host.equals(JdaService.guildPrimary)) return List.of(JdaService.guildPrimary);
        return List.of(host, JdaService.guildPrimary);
    }
}
