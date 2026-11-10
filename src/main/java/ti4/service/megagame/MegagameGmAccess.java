package ti4.service.megagame;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

@UtilityClass
public class MegagameGmAccess {

    public static final String GM_ROLE_NAME = "pbd100three GM";
    private static final List<String> GM_ROLE_NAMES = List.of(GM_ROLE_NAME, "bothelper");

    public static boolean hasGmRole(Member member) {
        return member != null
                && member.getRoles().stream()
                        .anyMatch(role -> GM_ROLE_NAMES.stream().anyMatch(role.getName()::equalsIgnoreCase));
    }

    public static String gmMention(Guild guild) {
        if (guild == null) {
            return "@" + GM_ROLE_NAME;
        }
        return guild.getRolesByName(GM_ROLE_NAME, true).stream()
                .findFirst()
                .map(Role::getAsMention)
                .orElse("@" + GM_ROLE_NAME);
    }
}
