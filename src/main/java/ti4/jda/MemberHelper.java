package ti4.jda;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

@UtilityClass
public class MemberHelper {

    public static Member getMember(Guild guild, String memberId) {
        return guild.retrieveMemberById(memberId).complete();
    }

    public static List<Member> getMembersWithRoles(Guild guild, Role... roles) {
        return guild.findMembersWithRoles(roles).get();
    }

    public static boolean hasMember(Guild guild, String memberId) {
        return getMember(guild, memberId) != null;
    }
}
