package ti4.message;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MentionGuard {

    private static final String ZERO_WIDTH_SPACE = "​";
    private static final Pattern MASS_MENTION = Pattern.compile("@(everyone|here)");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");

    public static String withoutRoleOrMassMentions(String text) {
        if (text == null) return null;
        String withoutRoles = ROLE_MENTION.matcher(text).replaceAll("@role");
        return MASS_MENTION.matcher(withoutRoles).replaceAll("@" + ZERO_WIDTH_SPACE + "$1");
    }

    public static String withoutAnyMentions(String text) {
        if (text == null) return null;
        String withoutUsers = USER_MENTION.matcher(text).replaceAll("@someone");
        return withoutRoleOrMassMentions(withoutUsers);
    }
}
