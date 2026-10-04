package ti4.discord.interactions.buttons.ids;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Player;

public final class AgentButtonIds {

    public static final String PREFIX = "exhaustAgent_";
    private static final String SEPARATOR = "_";

    private AgentButtonIds() {}

    public static String format(String agentId, String... payloadSegments) {
        if (payloadSegments.length == 0) {
            return PREFIX + agentId;
        }
        return PREFIX + agentId + SEPARATOR + String.join(SEPARATOR, payloadSegments);
    }

    public static String formatOwned(Player owner, String agentId, String... payloadSegments) {
        return ComponentIdEnvelope.ownedBy(owner.getFaction()) + format(agentId, payloadSegments);
    }

    public static Parsed parse(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Blank agent button id");
        }
        if (!id.startsWith(PREFIX) && id.contains(SEPARATOR)) {
            throw new IllegalArgumentException("Unknown agent button id: " + id);
        }
        String body = StringUtils.removeStart(id, PREFIX);
        String agentId = StringUtils.substringBefore(body, SEPARATOR);
        if (agentId.isEmpty()) {
            throw new IllegalArgumentException("Agent button id without an agent: " + id);
        }
        return new Parsed(agentId, StringUtils.substringAfter(body, SEPARATOR));
    }

    public record Parsed(String agentId, String payload) {

        public boolean hasPayload() {
            return !payload.isEmpty();
        }

        public List<String> segments() {
            return hasPayload() ? Arrays.asList(payload.split(SEPARATOR)) : List.of();
        }
    }
}
