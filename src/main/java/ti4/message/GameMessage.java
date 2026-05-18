package ti4.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashSet;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public record GameMessage(
        String messageId,
        GameMessageType type,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) LinkedHashSet<String> factionsThatReacted,
        long gameSaveTime,
        @JsonInclude(JsonInclude.Include.NON_NULL) String key) {

    public GameMessage(String messageId, GameMessageType type, long gameSaveTime) {
        this(messageId, type, null, gameSaveTime, null);
    }

    public GameMessage(String messageId, GameMessageType type, long gameSaveTime, String key) {
        this(messageId, type, null, gameSaveTime, key);
    }

    public GameMessage(
            String messageId, GameMessageType type, LinkedHashSet<String> factionsThatReacted, long gameSaveTime) {
        this(messageId, type, factionsThatReacted, gameSaveTime, null);
    }

    public GameMessage(
            String messageId,
            GameMessageType type,
            LinkedHashSet<String> factionsThatReacted,
            long gameSaveTime,
            String key) {
        this.messageId = messageId;
        this.type = type;
        this.factionsThatReacted =
                factionsThatReacted == null ? new LinkedHashSet<>() : new LinkedHashSet<>(factionsThatReacted);
        this.gameSaveTime = gameSaveTime;
        this.key = key;
    }

    public String asJumpLink(TextChannel channel) {
        return String.format(Message.JUMP_URL, channel.getGuild().getId(), channel.getId(), messageId);
    }
}
