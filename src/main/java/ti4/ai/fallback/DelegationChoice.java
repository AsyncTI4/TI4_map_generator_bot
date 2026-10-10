package ti4.ai.fallback;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.perception.AiPerception;

public record DelegationChoice(String seatId, String channelId, String messageId, int index, String checksum) {

    private static final int CHECKSUM_LENGTH = 6;

    public static DelegationChoice of(String seatId, String channelId, String messageId, int index, String customId) {
        return new DelegationChoice(seatId, channelId, messageId, index, checksum(customId));
    }

    public String customId() {
        return AiPerception.DELEGATION_PREFIX + seatId + "_" + channelId + "_" + messageId + "_" + index + "_"
                + checksum;
    }

    public boolean matches(String originalCustomId) {
        return checksum.equals(checksum(originalCustomId));
    }

    public static Optional<DelegationChoice> parse(String customId) {
        if (customId == null || !customId.startsWith(AiPerception.DELEGATION_PREFIX)) return Optional.empty();
        String[] parts =
                customId.substring(AiPerception.DELEGATION_PREFIX.length()).split("_");
        if (parts.length != 5 || !StringUtils.isNumeric(parts[3])) return Optional.empty();
        return Optional.of(new DelegationChoice(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]));
    }

    static String checksum(String customId) {
        CRC32 crc = new CRC32();
        crc.update(customId.getBytes(StandardCharsets.UTF_8));
        String hex = Long.toHexString(crc.getValue());
        return StringUtils.leftPad(hex, 8, '0').substring(0, CHECKSUM_LENGTH);
    }
}
