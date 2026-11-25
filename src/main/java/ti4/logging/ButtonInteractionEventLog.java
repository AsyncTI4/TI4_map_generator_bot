package ti4.logging;

final class ButtonInteractionEventLog extends AbstractEventLog {

    ButtonInteractionEventLog(LogOrigin source) {
        super(source);
    }

    @Override
    String getChannelName() {
        return "button-log";
    }

    @Override
    String getThreadName() {
        return null;
    }

    @Override
    String getMessagePrefix() {
        return "";
    }
}
