package ti4.message.logging;

enum LogSeverity {
    Info("bot-log-info", "### INFO\n"),
    Warning("bot-log-warning", "## WARNING\n"),
    Error("bot-log-error", "## ERROR\n");

    final String channelName;
    final String headerText;

    LogSeverity(String channelName, String headerText) {
        this.channelName = channelName;
        this.headerText = headerText;
    }
}
