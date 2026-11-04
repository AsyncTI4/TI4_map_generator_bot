package ti4.logging;

final class CronEventLog extends AbstractEventLog {

    CronEventLog(String message) {
        super(new LogOrigin(), message);
    }

    @Override
    String getChannelName() {
        return "bot-log";
    }

    @Override
    String getThreadName() {
        return "cron-log";
    }

    @Override
    String getMessagePrefix() {
        return "";
    }
}
