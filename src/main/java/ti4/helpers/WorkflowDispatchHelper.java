package ti4.helpers;

import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ti4.message.logging.BotLogger;

public class WorkflowDispatchHelper {

    private static final String GITHUB_WORKFLOWS_URL =
            "https://api.github.com/repos/AsyncTI4/TI4_map_generator_bot/actions/workflows/";
    private static final String REPO_DISPATCH_TOKEN = System.getenv("REPO_DISPATCH_TOKEN");
    private static final String DEFAULT_REF = "main";

    private final String workflowFile;
    private final Map<String, String> inputs;

    /**
     * Triggers a GitHub Actions workflow via the workflow_dispatch event API.
     * <a href="https://docs.github.com/en/rest/actions/workflows?apiVersion=2022-11-28#create-a-workflow-dispatch-event">...</a>
     *
     * @param workflowFile - the workflow file name, e.g. "create-map-video.yml"
     * @param inputs       - workflow inputs key/value pairs
     */
    public WorkflowDispatchHelper(String workflowFile, Map<String, String> inputs) {
        this.workflowFile = workflowFile;
        this.inputs = inputs;
    }

    public void sendDispatch() {
        if (System.getenv("TESTING") != null || REPO_DISPATCH_TOKEN == null || REPO_DISPATCH_TOKEN.isEmpty()) return;
        try {
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            StringBuilder bodyJson = new StringBuilder("{\"ref\":\"" + DEFAULT_REF + "\"");
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
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    BotLogger.error(
                            "WorkflowDispatchHelper error: " + response.body().string());
                }
            }
        } catch (Exception e) {
            BotLogger.error("WorkflowDispatchHelper error", e);
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
