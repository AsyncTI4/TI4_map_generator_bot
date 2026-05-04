package ti4.contest.replay.service;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CombatReplayAbilityWindowText {

    public String votesLockLine(int seconds) {
        return "-# Votes lock in " + formatDuration(seconds) + ".";
    }

    public String votesLockWhenNextContestPostsLine() {
        return "-# Votes lock when the next contest is posted.";
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "0 minutes";
        if (seconds < 60) return seconds + " " + (seconds == 1 ? "second" : "seconds");
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        String minuteText = minutes + " " + (minutes == 1 ? "minute" : "minutes");
        if (remainingSeconds == 0) return minuteText;
        return minuteText + " " + remainingSeconds + " " + (remainingSeconds == 1 ? "second" : "seconds");
    }
}
