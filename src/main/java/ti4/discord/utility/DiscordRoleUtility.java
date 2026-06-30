package ti4.discord.utility;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

@UtilityClass
public class DiscordRoleUtility {

    public static Role getRole(String name, Guild guild) {
        return guild.getRolesByName(name, true).stream().findFirst().orElse(null);
    }

    public static boolean hasRole(Guild guild, Member member, String roleName) {
        Role role = getRole(roleName, guild);
        return role != null && member.getRoles().contains(role);
    }
}
