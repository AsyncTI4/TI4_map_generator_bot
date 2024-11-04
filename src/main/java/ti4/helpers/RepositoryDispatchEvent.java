package ti4.helpers;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RepositoryDispatchEvent {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/AsyncTI4/TI4_map_genereator_bot/dispatches";
    private String eventType;
    private String githubToken;

    /**
     * @param eventType - can be anything, as long as it's caught on github actions side
     * @param githubToken - api key
     *            https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#create-a-repository-dispatch-event
     */
    public RepositoryDispatchEvent(String eventType, String githubToken) {
        this.eventType = eventType;
        this.githubToken = githubToken;
    }

    public void sendEvent() throws Exception {
        sendEvent(null);
    }

    public void sendEvent(RespositoryDispatchClientPayload payload) throws Exception {
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "token " + githubToken);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("Content-Type", "application/json; utf-8");
        connection.setDoOutput(true);

        String jsonInputString = "{\"event_type\":\"" + eventType + "\"}";

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        if (payload != null && payload.isValid()) {
            String payloadJson = payload.toJson();
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payloadJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new RuntimeException("Failed : HTTP error code : " + responseCode);
        }
    }

    public static void sendChannelBackupRequest(String channelID) { // TODO: move this wherever we want to do the thing, not here
        RepositoryDispatchEvent event = new RepositoryDispatchEvent("archive_game_channel", System.getenv("GITHUB_TOKEN"));
        RespositoryDispatchClientPayload payload = new RespositoryDispatchClientPayload(Map.of("channel_id", channelID));

    }
}
