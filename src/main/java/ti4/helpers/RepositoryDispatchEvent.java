package ti4.helpers;

import java.io.IOException;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ti4.message.logging.BotLogger;

public class RepositoryDispatchEvent {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/AsyncTI4/TI4_map_generator_bot/dispatches";
    private static final String GITHUB_WORKFLOWS_URL =
            "https://api.github.com/repos/AsyncTI4/TI4_map_generator_bot/actions/workflows/";
    private static final String REPO_DISPATCH_TOKEN = System.getenv("REPO_DISPATCH_TOKEN");
    private final String eventType;
    private final RespositoryDispatchClientPayload payload;

    /**
     * <a href="https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#create-a-repository-dispatch-event">...</a>
     *
     * @param eventType - can be anything, as long as it's caught on github actions side
     */
    public RepositoryDispatchEvent(String eventType, Map<String, String> payloadMap) {
        this.eventType = eventType;
        payload = new RespositoryDispatchClientPayload(payloadMap);
    }

    public void sendEvent() {
        if (System.getenv("TESTING") != null || REPO_DISPATCH_TOKEN == null || REPO_DISPATCH_TOKEN.isEmpty()) return;
        try {
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            StringBuilder bodyJson = new StringBuilder("{\"event_type\":\"" + eventType + "\"");
            if (payload.isValid()) {
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
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    BotLogger.error(
                            "RespositoryDisptachEvent error: " + response.body().string());
                }
            }
        } catch (Exception e) {
            BotLogger.error("RespositoryDisptachEvent error", e);
        }
    }

    /**
     * Triggers a GitHub Actions workflow via the workflow_dispatch event API asynchronously.
     * Uses non-blocking HTTP to avoid occupying JDA callback threads.
     * <a href="https://docs.github.com/en/rest/actions/workflows?apiVersion=2022-11-28#create-a-workflow-dispatch-event">GitHub API Documentation</a>
     *
     * @param workflowFile - the workflow file name, e.g. "create-map-video.yml"
     * @param inputs       - workflow inputs key/value pairs
     */
    public static void dispatchWorkflow(String workflowFile, Map<String, String> inputs) {
        if (System.getenv("TESTING") != null || REPO_DISPATCH_TOKEN == null || REPO_DISPATCH_TOKEN.isEmpty()) return;
        try {
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            StringBuilder bodyJson = new StringBuilder("{\"ref\":\"master\"");
            if (inputs != null && !inputs.isEmpty()) {
                bodyJson.append(",\"inputs\":{");
                bodyJson.append(inputs.entrySet().stream()
                        .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .orElse(""));
                bodyJson.append("}");
            }
            bodyJson.append("}");
            RequestBody body = RequestBody.create(bodyJson.toString(), mediaType);
            String url = GITHUB_WORKFLOWS_URL + workflowFile + "/dispatches";
            Request request = new Request.Builder()
                    .url(url)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + REPO_DISPATCH_TOKEN)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    BotLogger.error("RepositoryDispatchEvent.dispatchWorkflow error", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (response) {
                        if (!response.isSuccessful()) {
                            BotLogger.error(
                                    "RepositoryDispatchEvent.dispatchWorkflow error (" + response.code() + " " + response.message() + ") for " + call.request().url() + ": " + response.body().string());
                        }
                    }
                }
            });
        } catch (Exception e) {
            BotLogger.error("RepositoryDispatchEvent.dispatchWorkflow error", e);
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
