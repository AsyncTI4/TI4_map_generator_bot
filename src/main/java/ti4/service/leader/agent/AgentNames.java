package ti4.service.leader.agent;

import lombok.experimental.UtilityClass;
import ti4.game.Player;

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

    public static String offerVerb(Player owner, String agentId) {
        return holdsOnlyAYssarilCopy(owner, agentId) ? "Use Clever Clever " : "Use ";
    }

    private static boolean holdsOnlyAYssarilCopy(Player owner, String agentId) {
        return !owner.hasLeader(agentId) && owner.hasLeader(YSSARIL_AGENT);
    }
}
