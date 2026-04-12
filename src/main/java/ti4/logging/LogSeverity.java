package ti4.logging;

import com.rollbar.api.payload.data.Level;

enum LogSeverity {
    Info("bot-log-info", "### INFO\n", Level.INFO),
    Warning("bot-log-warning", "## WARNING\n", Level.WARNING),
    Error("bot-log-error", "## ERROR\n", Level.ERROR),
    Critical("bot-log-critical", "## CRITICAL\n", Level.CRITICAL);

    final String channelName;
    final String headerText;
    final Level rollbarLevel;

    LogSeverity(String channelName, String headerText, Level rollbarLevel) {
        this.channelName = channelName;
        this.headerText = headerText;
        this.rollbarLevel = rollbarLevel;
    }

    boolean isErrorOrHigher() {
        return this == Error || this == Critical;
    }
}
