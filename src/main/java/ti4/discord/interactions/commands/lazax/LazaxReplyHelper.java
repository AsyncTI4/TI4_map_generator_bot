package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.logging.BotLogger;

final class LazaxReplyHelper {

    private static final int MAX_MESSAGE_LENGTH = 1900;

    private LazaxReplyHelper() {}

    static void replyEphemeral(SlashCommandInteractionEvent event, String message) {
        boolean firstChunk = true;
        for (String chunk : splitByLine(message)) {
            if (firstChunk) {
                event.getHook().editOriginal(chunk).queue(null, BotLogger::catchRestError);
                firstChunk = false;
                continue;
            }
            event.getHook().sendMessage(chunk).setEphemeral(true).queue(null, BotLogger::catchRestError);
        }
    }

    private static Iterable<String> splitByLine(String message) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String line : message.split("\\R", -1)) {
            if (!chunk.isEmpty() && chunk.length() + line.length() + 1 > MAX_MESSAGE_LENGTH) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
            }
            if (!chunk.isEmpty()) {
                chunk.append('\n');
            }
            chunk.append(line);
        }
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
        }
        return chunks;
    }
}
