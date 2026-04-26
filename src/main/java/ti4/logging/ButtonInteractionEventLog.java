package ti4.logging;

final class ButtonInteractionEventLog extends AbstractEventLog {

    ButtonInteractionEventLog(LogOrigin source) {
        super(source);
    }

    @Override
    public String getChannelName() {
        return "button-log";
    }

    @Override
    public String getThreadName() {
        return null;
    }

    @Override
    public String getMessagePrefix() {
        return "";
    }
}
