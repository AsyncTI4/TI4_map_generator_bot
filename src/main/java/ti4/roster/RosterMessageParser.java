package ti4.roster;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.apache.commons.lang3.StringUtils;

public class RosterMessageParser {

    public static String parseGameFunName(String content) {
        String gameSillyName = StringUtils.substringBetween(content, "Game Fun Name: ", "\n");
        if (gameSillyName == null) return "TBD";
        return gameSillyName.trim();
    }

    public static List<String> parsePlayerIds(String content) {
        List<String> ids = new ArrayList<>();
        if (!content.contains("Players:")) return ids;
        String after = StringUtils.substringAfter(content, "Players:\n");
        if (after == null) return ids;
        String[] lines = after.split("\\n");
        for (String line : lines) {
            if (StringUtils.isBlank(line)) continue;
            // Expect lines like 1:123456789.(DisplayName)
            if (line.contains(":")) {
                String part = StringUtils.substringAfter(line, ":");
                String id = StringUtils.substringBefore(part, ".");
                if (StringUtils.isNumeric(id)) ids.add(id.trim());
            }
        }
        return ids;
    }

    public static List<Member> resolveMembers(Guild guild, List<String> ids) {
        List<Member> members = new ArrayList<>();
        for (String id : ids) {
            try {
                Member m = guild.getMemberById(id);
                if (m != null) members.add(m);
            } catch (Exception e) {
                // skip invalid id
            }
        }
        return members;
    }
}
