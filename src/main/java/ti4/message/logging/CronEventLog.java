package ti4.message.logging;

final class CronEventLog extends AbstractEventLog {

    CronEventLog(String message) {
        super(new LogOrigin(), message);
    }

    @Override
    public String getChannelName() {
        return "bot-log";
    }

    @Override
    public String getThreadName() {
        return "cron-log";
    }

    @Override
    public String getMessagePrefix() {
        return "";
    }
}
