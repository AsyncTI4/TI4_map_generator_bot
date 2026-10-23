package ti4.service.leader.agent;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AgentNames {

    private static final String YSSARIL_AGENT = "yssarilagent";

    public static boolean isYssarilCopy(String exhaustedLeaderId, String agentId) {
        return YSSARIL_AGENT.equalsIgnoreCase(exhaustedLeaderId) && !YSSARIL_AGENT.equalsIgnoreCase(agentId);
    }

    public static String cardName(String displayName, boolean viaYssaril) {
        if (!viaYssaril) {
            return displayName + " agent";
        }
        return "Clever Clever " + displayName + "/Yssaril agent";
    }
}
