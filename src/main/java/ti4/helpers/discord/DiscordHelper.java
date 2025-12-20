package ti4.helpers.discord;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.Response;

@UtilityClass
public class DiscordHelper {

    public static boolean isDiscordServerError(Throwable error) {
        if (error instanceof ErrorResponseException restError) {
            Response response = restError.getResponse();
            return response.code >= 500 && response.code < 600;
        }
        return false;
    }
}
