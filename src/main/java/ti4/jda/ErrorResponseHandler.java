package ti4.jda;

import java.util.function.Supplier;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

@UtilityClass
class ErrorResponseHandler {

    <T> T returnNullIfMissing(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorCode() == 404) {
                return null;
            }
            throw errorResponseException;
        }
    }

    @Getter
    enum ErrorResponseCodes {

        UNKNOWN_MEMBER(10007);

        private final int responseCode;

        ErrorResponseCodes(int responseCode) {
            this.responseCode = responseCode;
        }
    }
}
