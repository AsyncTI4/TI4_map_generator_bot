package ti4.website;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.website.model.stats.GameStatsDashboardPayload;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SequenceWriter;

@UtilityClass
public class GameStatisticsUploadService {

    private static final int MINIMUM_ROUND = 2;
    private static final int STAT_BATCH_SIZE = 200;
    private static final Duration THIRTY_DAYS_DURATION = Duration.ofDays(7);

    public static void uploadAllStats() throws IOException {
        Predicate<ManagedGame> shouldUpload = GameStatisticsUploadService::isValidToUpload;
        uploadStats("statistics", shouldUpload);
    }

    private static boolean isValidToUpload(ManagedGame managedGame) {
        return managedGame.getRound() >= MINIMUM_ROUND && !isAborted(managedGame);
    }

    private static boolean isAborted(ManagedGame managedGame) {
        return managedGame.isHasEnded() && !managedGame.isHasWinner();
    }

    public static void uploadRecentStats() throws IOException {
        Predicate<ManagedGame> shouldUpload =
                managedGame -> isValidToUpload(managedGame) && wasRecentlyUpdated(managedGame);
        uploadStats("recently_changed_statistics", shouldUpload);
    }

    private static boolean wasRecentlyUpdated(ManagedGame managedGame) {
        var lastModifiedDate = Instant.ofEpochMilli(managedGame.getLastModifiedDate());
        var thirtyDaysAgo = Instant.now().minus(THIRTY_DAYS_DURATION);

        return lastModifiedDate.isAfter(thirtyDaysAgo);
    }

    // If this becomes a resource hog again, the next step would probably be to switch to MultipartUpload
    private static void uploadStats(String fileName, Predicate<ManagedGame> gamePredicate) throws IOException {
        if (!uploadsEnabled()) return;
        String bucket = EgressClientManager.getWebProperties().getProperty("website.bucket");
        if (bucket == null || bucket.isEmpty()) {
            BotLogger.error("S3 bucket not configured.");
            return;
        }

        List<String> badGames = new ArrayList<>();
        int eligible = 0;
        int uploaded = 0;
        int currentBatchSize = 0;

        Path tempFile = Files.createTempFile(fileName, ".json");
        try (OutputStream outputStream = Files.newOutputStream(tempFile);
                SequenceWriter writer =
                        EgressClientManager.getJsonMapper().writer().writeValuesAsArray(outputStream)) {
            for (ManagedGame managedGame : GameManager.getManagedGames()) {
                if (!gamePredicate.test(managedGame)) {
                    continue;
                }

                eligible++;

                try {
                    JsonNode node = EgressClientManager.getJsonMapper()
                            .valueToTree(new GameStatsDashboardPayload(managedGame.getGame()));
                    writer.write(node);
                    uploaded++;
                    currentBatchSize++;
                    if (currentBatchSize == STAT_BATCH_SIZE) {
                        writer.flush();
                        currentBatchSize = 0;
                    }
                } catch (Exception e) {
                    badGames.add(managedGame.getName());
                    BotLogger.error(
                            String.format(
                                    "Failed to create GameStatsDashboardPayload for game: `%s`", managedGame.getName()),
                            e);
                }
            }

            writer.flush();
        }

        long fileSize = Files.size(tempFile);
        String msg = String.format(
                "# Uploading statistics to S3 (%.2f MB)... \nOut of %,d eligible games, %,d games are being uploaded.",
                fileSize / (1024.0d * 1024.0d), eligible, uploaded);
        if (eligible != uploaded) {
            msg += "\nBad games (first 10):\n- "
                    + String.join("\n- ", badGames.subList(0, Math.min(10, badGames.size())));
        }
        BotLogger.info(msg);

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key("statistics/" + fileName + ".json")
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

        EgressClientManager.getS3AsyncClient()
                .putObject(req, AsyncRequestBody.fromFile(tempFile))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        BotLogger.error(
                                String.format("Failed to upload game stats to S3 bucket %s.", bucket), throwable);
                    } else {
                        BotLogger.info(String.format("Statistics upload to bucket %s complete.", bucket));
                    }

                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        BotLogger.error("Failed to delete temporary stats file", e);
                    }
                });
    }

    private static boolean uploadsEnabled() {
        return GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false);
    }
}
