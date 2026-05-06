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

    public boolean isStrategyCard(int round, int sc) {
        return type == GameMessageType.STRATEGY_CARD
                && Integer.toString(round).equals(info.get("round"))
                && Integer.toString(sc).equals(info.get("sc"));
    }

    public long getInfoAsLong(String key) {
        String value = requireInfoValue(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long info value for key '" + key + "': " + value, e);
        }
    }

    public int getInfoAsInt(String key) {
        String value = requireInfoValue(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid int info value for key '" + key + "': " + value, e);
        }
    }

    private String requireInfoValue(String key) {
        String value = info.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing info value for key '" + key + "'");
        }
        return value;
    }
}
