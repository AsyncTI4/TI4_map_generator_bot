package ti4.helpers.discord;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.Response;

@UtilityClass
public class DiscordHelper {

    private static final int DISCORD_UNKNOWN_MESSAGE_ERROR_CODE = 10_008;

    public static boolean isDiscordServerError(Throwable error) {
        if (error instanceof ErrorResponseException restError) {
            Response response = restError.getResponse();
            return response.code >= 500 && response.code < 600;
        }
        return false;
    }

    public static boolean isUnknownMessageError(Throwable error) {
        return error instanceof ErrorResponseException restError
                && restError.getErrorCode() == DISCORD_UNKNOWN_MESSAGE_ERROR_CODE;
    }
}
