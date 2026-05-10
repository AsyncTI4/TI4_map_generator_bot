package ti4.contest.replay.service;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.JdaService;

@UtilityClass
public class LazaxMinigameRoleHelper {

    public static final String ROLE_NAME = "Lazax Minigame";

    public String mention(MessageChannel channel) {
        Role role = findRole(channel);
        return role == null ? "@" + ROLE_NAME : role.getAsMention();
    }

    public Role findRole(MessageChannel channel) {
        if (channel instanceof GuildMessageChannel guildChannel) {
            return findRole(guildChannel.getGuild());
        }
        return JdaService.guildPrimary == null ? null : findRole(JdaService.guildPrimary);
    }

    public Role findRole(Guild guild) {
        if (guild == null) return null;
        return guild.getRolesByName(ROLE_NAME, true).stream().findFirst().orElse(null);
    }
}
