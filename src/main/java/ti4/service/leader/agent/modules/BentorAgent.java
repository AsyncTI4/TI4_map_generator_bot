package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.StringHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class BentorAgent extends TargetedAgent {

    public static final String ID = "bentoragent";

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "C.O.O. Mgur, the Bentor";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        int blueprints = use.user().getNumberOfBluePrints();
        int oldTg = target.getTg();
        int tgGain = Math.max(0, blueprints + target.getCommodities() - target.getCommoditiesTotal());
        int commGain = blueprints - tgGain;

        ButtonHelperStats.gainComms(use.event(), use.game(), target, commGain, false, true);
        ButtonHelperStats.gainTGs(use.event(), use.game(), target, tgGain, true);

        String message = target.getFactionEmojiOrColor() + " gained " + StringHelper.pluralize(tgGain, "trade good")
                + " (" + oldTg + "->" + target.getTg() + ") and " + commGain + " commodit"
                + (commGain == 1 ? "y" : "ies")
                + " due to " + use.agentName() + ".";
        return AgentOutcome.notifyInFog(
                use.game(), target, message, use.user(), target.getRepresentation() + " has finished resolving.");
    }
}
