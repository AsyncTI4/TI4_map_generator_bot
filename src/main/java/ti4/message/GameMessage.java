package ti4.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public record GameMessage(
        String messageId,
        GameMessageType type,
        LinkedHashSet<String> factionsThatReacted,
        long gameSaveTime,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> info) {
    public GameMessage(
            String messageId,
            GameMessageType type,
            LinkedHashSet<String> factionsThatReacted,
            long gameSaveTime,
            Map<String, String> info) {
        this.messageId = messageId;
        this.type = type;
        this.factionsThatReacted =
                factionsThatReacted == null ? new LinkedHashSet<>() : new LinkedHashSet<>(factionsThatReacted);
        this.gameSaveTime = gameSaveTime;
        this.info = info == null ? new LinkedHashMap<>() : new LinkedHashMap<>(info);
    }

    public String asJumpLink(TextChannel channel) {
        return String.format(Message.JUMP_URL, channel.getGuild().getId(), channel.getId(), messageId);
    }
}
