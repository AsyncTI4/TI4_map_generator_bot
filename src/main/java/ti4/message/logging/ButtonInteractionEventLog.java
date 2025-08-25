package ti4.message.logging;

final class ButtonInteractionEventLog extends AbstractEventLog {

    ButtonInteractionEventLog(LogOrigin source) {
        super(source);
    }

    @Override
    public String getChannelName() {
        return "bot-log";
    }

    @Override
    public String getThreadName() {
        return "button-log";
    }

    @Override
    public String getMessagePrefix() {
        return "";
    }
}
