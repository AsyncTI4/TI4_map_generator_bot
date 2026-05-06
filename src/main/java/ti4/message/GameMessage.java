package ti4.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashSet;
import java.util.Map;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public record GameMessage(
        String messageId,
        GameMessageType type,
        LinkedHashSet<String> factionsThatReacted,
        long gameSaveTime,
        @JsonInclude(JsonInclude.Include.NON_NULL) String secondaryKey) {
    public GameMessage(
            String messageId,
            GameMessageType type,
            LinkedHashSet<String> factionsThatReacted,
            long gameSaveTime,
            String secondaryKey) {
        this.messageId = messageId;
        this.type = type;
        this.factionsThatReacted =
                factionsThatReacted == null ? new LinkedHashSet<>() : new LinkedHashSet<>(factionsThatReacted);
        this.gameSaveTime = gameSaveTime;
        this.secondaryKey = secondaryKey;
    }

    @JsonCreator
    public static GameMessage create(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("type") GameMessageType type,
            @JsonProperty("factionsThatReacted") LinkedHashSet<String> factionsThatReacted,
            @JsonProperty("gameSaveTime") long gameSaveTime,
            @JsonProperty("secondaryKey") String secondaryKey,
            @JsonProperty("info") Map<String, String> legacyInfo) {
        return new GameMessage(
                messageId,
                type,
                factionsThatReacted,
                gameSaveTime,
                secondaryKey != null ? secondaryKey : getLegacySecondaryKey(type, legacyInfo));
    }

    public String asJumpLink(TextChannel channel) {
        return String.format(Message.JUMP_URL, channel.getGuild().getId(), channel.getId(), messageId);
    }

    private static String getLegacySecondaryKey(GameMessageType type, Map<String, String> legacyInfo) {
        if (type != GameMessageType.STRATEGY_CARD || legacyInfo == null) {
            return null;
        }
        String round = legacyInfo.get("round");
        String sc = legacyInfo.get("sc");
        if (round == null || sc == null) {
            return null;
        }
        return round + "::" + sc;
    }
}
