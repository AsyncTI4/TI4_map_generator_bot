package ti4.discord.interactions.buttons.ids;

import org.apache.commons.lang3.StringUtils;

public final class AgentStepIds {

    public static final String YSSARIL_COPY_FLAG = "~cc";

    private AgentStepIds() {}

    public static String flagCopy(String stepId, boolean viaYssaril) {
        return viaYssaril ? stepId + YSSARIL_COPY_FLAG : stepId;
    }

    public static Parsed parse(String stepId) {
        if (stepId == null) {
            throw new IllegalArgumentException("Null agent step id");
        }
        boolean viaYssaril = stepId.endsWith(YSSARIL_COPY_FLAG);
        return new Parsed(StringUtils.removeEnd(stepId, YSSARIL_COPY_FLAG), viaYssaril);
    }

    public record Parsed(String id, boolean viaYssaril) {}
}
