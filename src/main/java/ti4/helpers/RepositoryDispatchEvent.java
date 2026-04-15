package ti4.helpers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import tools.jackson.databind.JsonNode;

public class RepositoryDispatchEvent {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/AsyncTI4/TI4_map_generator_bot/dispatches";
    private static final String GITHUB_WORKFLOW_RUNS_URL =
            "https://api.github.com/repos/AsyncTI4/TI4_map_generator_bot/actions/workflows/%s/runs";
    private static final String REPO_DISPATCH_TOKEN = System.getenv("REPO_DISPATCH_TOKEN");
    private static final int POLL_INTERVAL_SECONDS = 15;
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

    /**
     * Triggers the video generation workflow, which will compile images from the game's bot thread
     * into a video and publish it to the chronicles thread for the game.
     *
     * @param mapId - the game/map ID (game name)
     * @param botThreadId - ID of the bot-map-updates thread used as the image source
     * @param chroniclesThreadId - ID of the thread in the chronicles channel where the video will be posted
     * @param guildId - ID of the Discord guild (server) that contains the bot thread
     */
    public static void generateVideo(String mapId, String botThreadId, String chroniclesThreadId, String guildId) {
        new RepositoryDispatchEvent(
                        "generate_video",
                        Map.of(
                                "map_id", mapId,
                                "thread_id", botThreadId,
                                "post_to_thread_id", chroniclesThreadId,
                                "guild_id", guildId))
                .sendEvent();
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
     * Polls the GitHub Actions API until the run of {@code workflowFileName} whose name contains
     * {@code runNameContains} and was created at or after {@code notBefore} completes, or until
     * {@code timeout} elapses. Matching by run name allows multiple concurrent dispatches of the
     * same workflow to each wait for their own specific run without interfering with one another.
     *
     * @param workflowFileName  the workflow file name (e.g. {@code "archive_game_channel.yaml"})
     * @param notBefore         ignore runs that started before this instant
     * @param timeout           how long to wait before giving up
     * @param runNameContains   a string that must appear in the run's {@code name} field, or
     *                          {@code null} to match any run
     * @return {@code true} if the matched run completed with conclusion {@code "success"},
     *         {@code false} if it failed, timed out, or the token is unavailable
     */
    public static boolean waitForWorkflowCompletion(
            String workflowFileName, Instant notBefore, Duration timeout, String runNameContains) {
        if (REPO_DISPATCH_TOKEN == null || REPO_DISPATCH_TOKEN.isEmpty()) return false;

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        String url =
                String.format(GITHUB_WORKFLOW_RUNS_URL, workflowFileName) + "?event=repository_dispatch&per_page=10";

        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Authorization", "Bearer " + REPO_DISPATCH_TOKEN)
                        .addHeader("Accept", "application/vnd.github+json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;

                    JsonNode root =
                            JsonMapperManager.basic().readTree(response.body().string());
                    JsonNode runs = root.get("workflow_runs");
                    if (runs == null || !runs.isArray()) continue;

                    for (JsonNode run : runs) {
                        Instant runCreated =
                                Instant.parse(run.path("created_at").asText());
                        if (runCreated.isBefore(notBefore)) continue;
                        if (runNameContains != null
                                && !run.path("name").asText().contains(runNameContains)) continue;

                        String status = run.path("status").asText();
                        if ("completed".equals(status)) {
                            return "success".equals(run.path("conclusion").asText());
                        }
                        // Matching run found but still in progress; keep polling.
                        break;
                    }
                }
            } catch (Exception e) {
                BotLogger.error("RepositoryDispatchEvent: error polling GitHub Actions for " + workflowFileName, e);
            }

            try {
                Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Polls the GitHub Actions API until the most recent run of {@code workflowFileName}
     * that was created at or after {@code notBefore} completes, or until {@code timeout} elapses.
     *
     * @param workflowFileName  the workflow file name (e.g. {@code "archive_game_channel.yaml"})
     * @param notBefore         ignore runs that started before this instant
     * @param timeout           how long to wait before giving up
     * @return {@code true} if the run completed with conclusion {@code "success"},
     *         {@code false} if it failed, timed out, or the token is unavailable
     */
    public static boolean waitForWorkflowCompletion(String workflowFileName, Instant notBefore, Duration timeout) {
        return waitForWorkflowCompletion(workflowFileName, notBefore, timeout, null);
    }
}
