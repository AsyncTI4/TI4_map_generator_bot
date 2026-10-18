package ti4.discord.interactions.routing;

import org.apache.commons.lang3.StringUtils;
import ti4.image.Mapper;

public record ComponentIdEnvelope(
        String rawId,
        String ownerFaction,
        String spoofedFaction,
        boolean deleteButton,
        boolean deleteMessage,
        String handlerId) {

    private static final String OWNER_PREFIX = "FFCC_";
    private static final String SPOOF_PREFIX = "dummyPlayerSpoof";
    private static final String DELETE_BUTTON_MARKER = "deleteThisButton";
    private static final String DELETE_MESSAGE_MARKER = "deleteThisMessage";

    public static String ownedBy(String faction) {
        return OWNER_PREFIX + faction + "_";
    }

    public static String spoofedAs(String faction) {
        return SPOOF_PREFIX + faction + "_";
    }

    public static ComponentIdEnvelope decode(String rawId) {
        if (rawId == null) return new ComponentIdEnvelope(null, null, null, false, false, null);

        String ownerFaction = null;
        String spoofedFaction = null;
        String rest = rawId;
        if (rest.startsWith(OWNER_PREFIX)) {
            rest = rest.substring(OWNER_PREFIX.length());
            ownerFaction = leadingFaction(rest);
            rest = StringUtils.substringAfter(rest, ownerFaction + "_");
        } else if (rest.startsWith(SPOOF_PREFIX)) {
            rest = rest.substring(SPOOF_PREFIX.length());
            spoofedFaction = leadingFaction(rest);
            rest = StringUtils.substringAfter(rest, spoofedFaction + "_");
        }

        boolean deleteButton = rest.contains(DELETE_BUTTON_MARKER);
        boolean deleteMessage = rest.contains(DELETE_MESSAGE_MARKER);
        String handlerId = rest.replace(DELETE_BUTTON_MARKER, "").replace(DELETE_MESSAGE_MARKER, "");
        return new ComponentIdEnvelope(rawId, ownerFaction, spoofedFaction, deleteButton, deleteMessage, handlerId);
    }

    private static String leadingFaction(String idAfterPrefix) {
        String longestKnownFaction = null;
        for (int i = idAfterPrefix.indexOf('_'); i >= 0; i = idAfterPrefix.indexOf('_', i + 1)) {
            String candidate = idAfterPrefix.substring(0, i);
            if (Mapper.isValidFaction(candidate)) longestKnownFaction = candidate;
        }
        if (longestKnownFaction != null) return longestKnownFaction;
        return StringUtils.substringBefore(idAfterPrefix, "_");
    }
}
