package ti4.discord.interactions.buttons.handlers.matchmaking;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;

@UtilityClass
class TiglQueueSubmissionLog {

    record Submission(
            String userName,
            String userId,
            List<String> requestedRanks,
            List<String> savedRanks,
            List<String> ranksOpenToGroup,
            List<String> groupMemberIds,
            Optional<String> queueError) {}

    static void post(Submission submission) {
        BotLogger.info(describe(submission));
    }

    static String describe(Submission submission) {
        String outcome =
                submission.queueError().map(error -> "rejected (" + error + ")").orElse("queued");
        return "TIGL queue submit by %s (%s): requested ranks=%s, saved ranks=%s, ranks open to the group=%s, group=%s, %s"
                .formatted(
                        submission.userName(),
                        submission.userId(),
                        submission.requestedRanks(),
                        submission.savedRanks(),
                        submission.ranksOpenToGroup(),
                        submission.groupMemberIds(),
                        outcome);
    }
}
