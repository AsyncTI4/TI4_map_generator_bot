package ti4.discord.utility;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

@UtilityClass
public class DiscordRoleUtility {

    public static Role getRole(String name, Guild guild) {
        return guild.getRolesByName(name, true).stream().findFirst().orElse(null);
    }
}
