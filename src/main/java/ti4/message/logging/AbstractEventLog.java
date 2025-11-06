package ti4.message.logging;

abstract class AbstractEventLog {

    private final LogOrigin source;
    private final String message;

    public abstract String getChannelName();

    public abstract String getThreadName();

    public abstract String getMessagePrefix();

    String getLogString() {
        StringBuilder log = new StringBuilder();

        log.append(source.getOriginTimeFormatted());
        if (source.getEventString() != null) {
            log.append(source.getEventString());
        }
        if (source.getGameInfo() != null) {
            log.append(source.getGameInfo());
        }
        if (message != null && !message.isBlank()) {
            log.append(getMessagePrefix()).append(message).append("\n");
        }

        log.append("\n");
        return log.toString();
    }

    AbstractEventLog(LogOrigin source) {
        this.source = source;
        message = null;
    }

    AbstractEventLog(LogOrigin source, String message) {
        this.source = source;
        this.message = message;
    }
}
