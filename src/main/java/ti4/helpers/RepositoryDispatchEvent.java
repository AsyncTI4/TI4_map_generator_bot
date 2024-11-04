package ti4.helpers;

import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ti4.message.BotLogger;

public class RepositoryDispatchEvent {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/AsyncTI4/TI4_map_generator_bot/dispatches";
    private static final String REPO_DISPATCH_TOKEN = System.getenv("REPO_DISPATCH_TOKEN");
    private String eventType;
    private RespositoryDispatchClientPayload payload;

    /**
     * https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#create-a-repository-dispatch-event
     * 
     * @param eventType - can be anything, as long as it's caught on github actions side
     */
    public RepositoryDispatchEvent(String eventType, Map<String, String> payloadMap) {
        this.eventType = eventType;
        this.payload = new RespositoryDispatchClientPayload(payloadMap);
    }

    public void sendEvent() {
        try {
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            StringBuilder bodyJson = new StringBuilder("{\"event_type\":\"" + eventType + "\"");
            if (payload != null && payload.isValid()) {
                bodyJson.append(",").append(payload.toJson());
            }
            bodyJson.append("}");
            RequestBody body = RequestBody.create(bodyJson.toString(), mediaType);
            Request request = new Request.Builder()
                .url(GITHUB_API_URL)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + REPO_DISPATCH_TOKEN)
                .build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                BotLogger.log("RespositoryDisptachEvent error: " + response.body().string());
            }
        } catch (Exception e) {
            BotLogger.log("RespositoryDisptachEvent error", e);
        }
    }
}
