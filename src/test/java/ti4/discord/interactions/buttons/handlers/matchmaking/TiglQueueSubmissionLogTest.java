package ti4.discord.interactions.buttons.handlers.matchmaking;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TiglQueueSubmissionLogTest {

    @Test
    void queuedSubmissionNamesRequestedSavedAndGroupRanks() {
        String message = TiglQueueSubmissionLog.describe(new TiglQueueSubmissionLog.Submission(
                "Kavadera",
                "348345471196200961",
                List.of("Commander"),
                List.of("Minister"),
                List.of("Unranked", "Minister"),
                List.of("348345471196200961", "222"),
                Optional.empty()));

        assertTrue(message.contains("Kavadera (348345471196200961)"));
        assertTrue(message.contains("requested ranks=[Commander]"));
        assertTrue(message.contains("saved ranks=[Minister]"));
        assertTrue(message.contains("ranks open to the group=[Unranked, Minister]"));
        assertTrue(message.contains("group=[348345471196200961, 222]"));
        assertTrue(message.endsWith("queued"));
    }

    @Test
    void rejectedSubmissionIncludesTheReason() {
        String message = TiglQueueSubmissionLog.describe(new TiglQueueSubmissionLog.Submission(
                "Kavadera",
                "1",
                List.of("Commander"),
                List.of("Commander"),
                List.of("Commander"),
                List.of("1"),
                Optional.of("You are already queued for a game.")));

        assertTrue(message.endsWith("rejected (You are already queued for a game.)"));
    }
}
