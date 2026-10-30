package ti4.ai.actuation;

import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

@UtilityClass
public class AiActor {

    public static Member member(Guild guild, String seatId, String displayName) {
        Member self = guild.getSelfMember();
        User user = user(self.getUser(), seatId, displayName);
        String mention = "**" + displayName + "**";
        return Proxies.delegating(
                Member.class,
                self,
                Map.of(
                        "getId", Proxies.constant(seatId),
                        "getIdLong", Proxies.constant(Long.parseLong(seatId)),
                        "getUser", Proxies.constant(user),
                        "getEffectiveName", Proxies.constant(displayName),
                        "getNickname", Proxies.constant(displayName),
                        "getAsMention", Proxies.constant(mention),
                        "toString", Proxies.constant("AiMember[" + seatId + "]")));
    }

    static User user(User selfUser, String seatId, String displayName) {
        String mention = "**" + displayName + "**";
        return Proxies.delegating(
                User.class,
                selfUser,
                Map.of(
                        "getId", Proxies.constant(seatId),
                        "getIdLong", Proxies.constant(Long.parseLong(seatId)),
                        "getName", Proxies.constant(displayName),
                        "getGlobalName", Proxies.constant(displayName),
                        "getEffectiveName", Proxies.constant(displayName),
                        "getAsMention", Proxies.constant(mention),
                        "toString", Proxies.constant("AiUser[" + seatId + "]")));
    }
}
