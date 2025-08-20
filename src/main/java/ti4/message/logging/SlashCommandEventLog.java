package ti4.message.logging;

import net.dv8tion.jda.api.entities.Message;

final class SlashCommandEventLog extends AbstractEventLog {

    SlashCommandEventLog(LogOrigin source, Message commandResponse) {
        super(source, commandResponse.getContentDisplay());
    }

    @Override
    public String getChannelName() {
        return "bot-slash-command-log";
    }

    @Override
    public String getThreadName() {
        return "slash-command-log";
    }

    @Override
    public String getMessagePrefix() {
        return "Response: ";
    }
}
