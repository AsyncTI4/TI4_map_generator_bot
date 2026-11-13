package ti4.service.tournament;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

@UtilityClass
public class TournamentMemberResolver {

    private static final Pattern MENTION = Pattern.compile("^<@!?(\\d+)>$");
    private static final Pattern RAW_ID = Pattern.compile("^\\d{15,21}$");

    public record Candidate(String userId, String username, Collection<String> displayNames) {}

    public record Resolution(List<String> userIds, List<String> notFound, List<String> ambiguous) {

        public String problems() {
            List<String> lines = new ArrayList<>();
            if (!notFound.isEmpty()) lines.add("Not found: " + String.join(", ", notFound));
            if (!ambiguous.isEmpty())
                lines.add("More than one match, use a mention or id: " + String.join(", ", ambiguous));
            return String.join("\n", lines);
        }
    }

    public static Resolution resolve(String text, Collection<Guild> guilds) {
        return resolveAmong(text, candidates(guilds));
    }

    public static Resolution resolveAmong(String text, Collection<Candidate> candidates) {
        Map<String, Candidate> byId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            byId.putIfAbsent(candidate.userId(), candidate);
        }
        Set<String> userIds = new LinkedHashSet<>();
        List<String> notFound = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (String token : tokens(text)) {
            String id = idFrom(token);
            if (id != null) {
                if (byId.containsKey(id)) userIds.add(id);
                else notFound.add(token);
                continue;
            }
            String name = token.startsWith("@") ? token.substring(1) : token;
            List<String> matches = byId.values().stream()
                    .filter(candidate -> name.equalsIgnoreCase(candidate.username()))
                    .map(Candidate::userId)
                    .toList();
            if (matches.isEmpty()) {
                matches = byId.values().stream()
                        .filter(candidate -> candidate.displayNames().stream().anyMatch(name::equalsIgnoreCase))
                        .map(Candidate::userId)
                        .toList();
            }
            if (matches.isEmpty()) notFound.add(token);
            else if (matches.size() > 1) ambiguous.add(token);
            else userIds.add(matches.getFirst());
        }
        return new Resolution(new ArrayList<>(userIds), notFound, ambiguous);
    }

    private static List<String> tokens(String text) {
        if (text == null) return List.of();
        List<String> tokens = new ArrayList<>();
        for (String line : text.split("[\\n,;]")) {
            String token = line.trim();
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    private static String idFrom(String token) {
        Matcher mention = MENTION.matcher(token);
        if (mention.matches()) return mention.group(1);
        return RAW_ID.matcher(token).matches() ? token : null;
    }

    private static List<Candidate> candidates(Collection<Guild> guilds) {
        Map<String, Candidate> byId = new LinkedHashMap<>();
        for (Guild guild : guilds) {
            if (guild == null) continue;
            for (Member member : guild.getMembers()) {
                Candidate candidate = byId.computeIfAbsent(
                        member.getId(), id -> new Candidate(id, member.getUser().getName(), new LinkedHashSet<>()));
                candidate.displayNames().add(member.getEffectiveName());
                if (member.getUser().getGlobalName() != null) {
                    candidate.displayNames().add(member.getUser().getGlobalName());
                }
            }
        }
        return new ArrayList<>(byId.values());
    }
}
