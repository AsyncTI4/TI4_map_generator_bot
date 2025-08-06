package ti4.jda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;

@UtilityClass
public class UserHelper {

    @Nullable
    public static User getUser(String id) {
        if (id == null) return null;
        return ErrorResponseHandler.returnNullIfMissing(() -> AsyncTI4DiscordBot.jda.retrieveUserById(id).complete());
    }
}
