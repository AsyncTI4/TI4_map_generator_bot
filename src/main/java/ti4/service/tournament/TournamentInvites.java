package ti4.service.tournament;

import java.util.LinkedHashSet;
import java.util.List;
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
                .filter(userId -> TournamentDiscord.member(host, userId) == null)
                .toList();
    }

    public static String text(Tournament tournament) {
        return text(tournament, missingFromHost(tournament));
    }

    private static String text(Tournament tournament, List<String> missing) {
        Guild host = TournamentDiscord.hostGuild(tournament);
        if (host == null) return null;
        String link = Helper.getGuildInviteURL(host, UNLIMITED_USES);
        String where = "the **" + tournament.displayName() + "** games are on **" + host.getName() + "**. Join here: ";
        if (missing.isEmpty()) return StringUtils.capitalize(where) + link;
        return TournamentDiscord.mentions(missing) + " " + where + link;
    }

    public static String send(Tournament tournament, GuildMessageChannel channel) {
        List<String> missing = missingFromHost(tournament);
        String text = text(tournament, missing);
        if (text == null) return "Pick a host server first.";
        MessageHelper.sendMessageToChannel(channel, text);
        int pinged = missing.size();
        return "Posted the invite in " + channel.getAsMention() + " ("
                + channel.getGuild().getName() + ")"
                + (pinged > 0 ? ", pinging " + pinged + " player(s) who aren't on the host server yet." : ".");
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

    public static String defaultChannelLink(Tournament tournament) {
        GuildMessageChannel channel = TournamentSignupPosts.firstChannel(tournament);
        return channel == null ? null : channel.getJumpUrl();
    }
}
