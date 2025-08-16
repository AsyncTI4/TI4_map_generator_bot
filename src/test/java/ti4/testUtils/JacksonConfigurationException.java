package ti4.testUtils;

/**
 * If thrown, a test case has discovered an issue with how our source code is configured
 * for Jackson, our JSON serializer convering java classes into JSON files. Do not ignore
 * this exception as it likely means you will break our save/restore cycles for game state.
 */
class JacksonConfigurationException extends Exception {
    JacksonConfigurationException(String msg) {
        super(msg);
    }
}
