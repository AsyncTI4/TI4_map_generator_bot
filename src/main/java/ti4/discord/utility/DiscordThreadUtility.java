package ti4.discord.utility;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.Channel;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class DiscordThreadUtility {

    public String fitThreadName(String threadName) {
        return StringUtils.abbreviate(threadName, Channel.MAX_NAME_LENGTH);
    }
}
