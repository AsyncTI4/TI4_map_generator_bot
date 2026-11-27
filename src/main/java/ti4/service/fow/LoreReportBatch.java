package ti4.service.fow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

final class LoreReportBatch {

    private record Announcement(MessageChannel channel, String text, List<MessageEmbed> embeds) {}

    private record SystemPing(String position, List<String> lines) {}

    private record ChannelLines(MessageChannel channel, List<String> lines) {}

    private final Game game;
    private final Map<String, Announcement> announcements = new LinkedHashMap<>();
    private final Map<String, SystemPing> systemPings = new LinkedHashMap<>();
    private final Map<String, ChannelLines> channelLines = new LinkedHashMap<>();
    private final Set<String> pingedPlayers = new HashSet<>();
    private final List<String> activityLines = new ArrayList<>();
    private final List<String> firedTargets = new ArrayList<>();
    private boolean firedWithGmPing;
    private boolean mapChanged;

    LoreReportBatch(Game game) {
        this.game = game;
    }

    void announce(@Nullable MessageChannel channel, String text, MessageEmbed embed) {
        if (channel == null) return;
        announcements
                .computeIfAbsent(
                        channel.getId() + "|" + text, key -> new Announcement(channel, text, new ArrayList<>()))
                .embeds()
                .add(embed);
    }

    void pingSystem(String position, String line) {
        String key = position + "|" + String.join(",", colorsNamedIn(line));
        systemPings
                .computeIfAbsent(key, k -> new SystemPing(position, new ArrayList<>()))
                .lines()
                .add(line);
    }

    void tellPlayer(Player player, @Nullable MessageChannel channel, String line) {
        if (channel == null) return;
        String prefix = pingedPlayers.add(player.getUserID()) ? player.getRepresentationUnfogged() + " " : "";
        post(channel, prefix + line);
    }

    void post(@Nullable MessageChannel channel, String line) {
        if (channel == null) return;
        channelLines
                .computeIfAbsent(channel.getId(), key -> new ChannelLines(channel, new ArrayList<>()))
                .lines()
                .add(line);
    }

    void logForGm(String line) {
        activityLines.add(line);
    }

    void mapChanged() {
        mapChanged = true;
    }

    void loreFired(String target, boolean pingGm) {
        firedTargets.add(target);
        firedWithGmPing |= pingGm;
    }

    void send() {
        announcements
                .values()
                .forEach(announcement -> MessageHelper.sendMessageToChannelWithEmbeds(
                        announcement.channel(), announcement.text(), announcement.embeds()));
        systemPings
                .values()
                .forEach(ping -> FoWHelper.pingSystem(game, ping.position(), String.join("\n", ping.lines()), false));
        channelLines
                .values()
                .forEach(
                        lines -> MessageHelper.sendMessageToChannel(lines.channel(), String.join("\n", lines.lines())));
        if (!activityLines.isEmpty()) {
            GMService.postToActivityThread(game, String.join("\n", activityLines));
        }
        if (mapChanged) {
            GMService.refreshMapInActivityThread(game);
        }
        if (!firedTargets.isEmpty()) {
            GMService.logActivity(game, "Phase lore fired for " + String.join(", ", firedTargets), firedWithGmPing);
        }
    }

    private Set<String> colorsNamedIn(String line) {
        Set<String> colors = new TreeSet<>();
        for (Player player : game.getRealPlayers()) {
            if (FoWHelper.isNamedInPing(player, line)) {
                colors.add(player.getColor());
            }
        }
        return colors;
    }
}
