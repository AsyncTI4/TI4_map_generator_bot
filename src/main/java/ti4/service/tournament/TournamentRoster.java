package ti4.service.tournament;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import ti4.logging.BotLogger;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentRoster {

    private static final int INLINE_LIMIT = 1900;

    public static String text(Tournament tournament, List<TournamentEntry> entries) {
        Map<Integer, List<TournamentEntry>> groups = TournamentService.groups(entries);
        Map<Integer, String> gameMasters = TournamentService.gameMastersByGroup(tournament);
        List<String> lines = new ArrayList<>();
        lines.add(tournament.getSeriesName() + " · " + tournament.getRoundLabel());
        groups.forEach((group, members) -> lines.add(groupLine(tournament, group, members, gameMasters.get(group))));
        List<TournamentEntry> waitlist =
                entries.stream().filter(TournamentEntry::isWaitlisted).toList();
        if (!waitlist.isEmpty()) {
            lines.add("Waitlist: " + String.join(", ", names(waitlist)));
        }
        return String.join("\n", lines);
    }

    private static String groupLine(Tournament tournament, int group, List<TournamentEntry> members, String gm) {
        String game = members.getFirst().getGameName();
        String line = "Group " + String.format("%02d", group) + (game == null ? "" : " [" + game + "]");
        if (tournament.isFogMode()) {
            line += " (GM: " + (gm == null ? "none" : TournamentDiscord.userName(gm)) + ")";
        }
        return line + ": " + String.join(", ", names(members));
    }

    public static String csv(Tournament tournament, List<TournamentEntry> entries) {
        Map<Integer, String> gameMasters = TournamentService.gameMastersByGroup(tournament);
        StringBuilder csv = new StringBuilder("group,game,role,user_id,name\n");
        TournamentService.groups(entries).forEach((group, members) -> {
            String game = members.getFirst().getGameName();
            String gm = gameMasters.get(group);
            if (tournament.isFogMode() && gm != null) {
                csv.append(row(group, game, "gm", gm));
            }
            for (TournamentEntry member : members) {
                csv.append(row(group, game, "player", member.getUserId()));
            }
        });
        for (TournamentEntry entry : entries) {
            if (entry.isWaitlisted()) csv.append(row(null, null, "waitlist", entry.getUserId()));
        }
        return csv.toString();
    }

    private static String row(Integer group, String game, String role, String userId) {
        String name = TournamentDiscord.userName(userId).replace("\"", "\"\"");
        return (group == null ? "" : group) + "," + (game == null ? "" : game) + "," + role + "," + userId + ",\""
                + name + "\"\n";
    }

    public static void post(Tournament tournament, List<TournamentEntry> entries, MessageChannel channel) {
        String text = text(tournament, entries);
        String baseName = (tournament.getSeriesName() + "-" + tournament.getRoundLabel())
                .replaceAll("[^A-Za-z0-9-]+", "-")
                .toLowerCase();
        List<FileUpload> files = new ArrayList<>();
        files.add(FileUpload.fromData(csv(tournament, entries).getBytes(StandardCharsets.UTF_8), baseName + ".csv"));
        String content;
        if (text.length() <= INLINE_LIMIT) {
            content = "```\n" + text + "\n```";
        } else {
            content = "Roster for **" + tournament.displayName() + "** is attached.";
            files.add(FileUpload.fromData(text.getBytes(StandardCharsets.UTF_8), baseName + ".txt"));
        }
        channel.sendMessage(content).addFiles(files).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static List<String> names(List<TournamentEntry> entries) {
        return entries.stream()
                .map(entry -> TournamentDiscord.userName(entry.getUserId()))
                .toList();
    }
}
