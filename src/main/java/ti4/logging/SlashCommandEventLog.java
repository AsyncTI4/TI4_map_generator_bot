package ti4.logging;

import net.dv8tion.jda.api.entities.Message;

final class SlashCommandEventLog extends AbstractEventLog {

    SlashCommandEventLog(LogOrigin source, Message commandResponse) {
        super(source, commandResponse.getContentDisplay());
    }

    @Override
    String getChannelName() {
        return "slash-command-log";
    }

    @Override
    String getThreadName() {
        return null;
    }

    @Override
    String getMessagePrefix() {
        return "Response: ";
    }
}
