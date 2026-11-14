package ti4.service.tournament;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentInvites {

    private static final Pattern CHANNEL_LINK = Pattern.compile("discord(?:app)?\\.com/channels/\\d+/(\\d+)");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("^<#(\\d+)>$");
    private static final Pattern RAW_ID = Pattern.compile("^\\d{15,21}$");
    private static final int UNLIMITED_USES = 0;

    private static List<String> missingFromHost(Tournament tournament) {
        Guild host = TournamentDiscord.hostGuild(tournament);
        if (host == null) return List.of();
        TournamentService service = TournamentService.getBean();
        Set<String> candidates = new LinkedHashSet<>();
        service.activeEntries(tournament.getId()).stream()
                .map(TournamentEntry::getUserId)
                .forEach(candidates::add);
        if (tournament.isFogMode()) candidates.addAll(service.gameMasterIds(tournament.getId()));
        return candidates.stream()
                .filter(userId -> TournamentDiscord.isMissingFrom(host, userId))
                .toList();
    }

    public static String text(Tournament tournament) {
        return text(tournament, missingFromHost(tournament));
    }

    private static String text(Tournament tournament, List<String> missing) {
        Guild host = TournamentDiscord.hostGuild(tournament);
        if (host == null) return null;
        return text(tournament, host, Helper.getGuildInviteURL(host, UNLIMITED_USES), missing);
    }

    private static String text(Tournament tournament, Guild host, String link, List<String> pinged) {
        String where = "the **" + tournament.displayName() + "** games are on **" + host.getName() + "**. Join here: ";
        if (pinged.isEmpty()) return StringUtils.capitalize(where) + link;
        return TournamentDiscord.mentions(pinged) + " " + where + link;
    }

    public static String send(Tournament tournament, GuildMessageChannel channel) {
        return send(tournament, List.of(channel));
    }

    public static String sendToSignupPosts(Tournament tournament) {
        List<GuildMessageChannel> channels = signupChannelsOffHost(tournament);
        if (channels.isEmpty()) return null;
        return send(tournament, channels);
    }

    private static List<GuildMessageChannel> signupChannelsOffHost(Tournament tournament) {
        Guild host = TournamentDiscord.hostGuild(tournament);
        Map<Guild, GuildMessageChannel> firstPerServer = new LinkedHashMap<>();
        TournamentService.getBean().signupPosts(tournament.getId()).stream()
                .map(post -> TournamentDiscord.channel(post.getChannelId()))
                .filter(Objects::nonNull)
                .filter(channel -> !channel.getGuild().equals(host))
                .forEach(channel -> firstPerServer.putIfAbsent(channel.getGuild(), channel));
        return List.copyOf(firstPerServer.values());
    }

    private static String send(Tournament tournament, List<GuildMessageChannel> channels) {
        Guild host = TournamentDiscord.hostGuild(tournament);
        if (host == null) return "Pick a host server first.";
        String link = Helper.getGuildInviteURL(host, UNLIMITED_USES);
        Set<String> notPinged = new LinkedHashSet<>(missingFromHost(tournament));
        List<String> posted = new ArrayList<>();
        for (GuildMessageChannel channel : channels) {
            List<String> pinged = takeMembersOf(channel.getGuild(), notPinged);
            MessageHelper.sendMessageToChannel(channel, text(tournament, host, link, pinged));
            posted.add(
                    channel.getAsMention() + " (" + channel.getGuild().getName() + ", " + pinged.size() + " pinged)");
        }
        String notice = "Posted the invite in " + String.join(", ", posted) + ".";
        if (notPinged.isEmpty()) return notice;
        return notice + "\nNot on " + host.getName() + " or on the server of any of those channels, so nobody"
                + " could ping them: " + String.join(", ", TournamentDiscord.userNames(List.copyOf(notPinged)))
                + ". DM them the link from **Copy invite**.";
    }

    static List<String> takeMembersOf(Guild server, Set<String> notPinged) {
        List<String> members = notPinged.stream()
                .filter(userId -> TournamentDiscord.member(server, userId) != null)
                .toList();
        members.forEach(notPinged::remove);
        return members;
    }

    public static GuildMessageChannel resolveChannel(String input) {
        String channelId = channelId(input);
        return channelId == null ? null : TournamentDiscord.channel(channelId);
    }

    static String channelId(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        Matcher link = CHANNEL_LINK.matcher(trimmed);
        if (link.find()) return link.group(1);
        Matcher mention = CHANNEL_MENTION.matcher(trimmed);
        if (mention.matches()) return mention.group(1);
        return RAW_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
