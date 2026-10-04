package ti4.service.leader.agent.modules;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class KyroAgent extends TargetedAgent {

    public static final String ID = "kyroagent";

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Tox, the Kyro";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Player kyro = use.user();
        int commoditiesTotal = target.getCommoditiesTotal();
        target.setCommodities(target.getCommodities() + commoditiesTotal);
        ButtonHelper.resolveMinisterOfCommerceCheck(use.game(), target, use.event());
        ButtonHelperAgents.cabalAgentInitiation(use.game(), target);

        String message = target.getFactionEmojiOrColor() + " replenished commodities due to " + use.agentName() + ".";
        int infantry = commoditiesTotal - 1;
        List<Button> placeInfantry =
                Helper.getPlanetPlaceUnitButtons(kyro, use.game(), infantry + "gf", "placeOneNDone_skipbuild");
        return AgentOutcome.notifyInFog(use.game(), target, message, kyro, message)
                .and(Message.withButtons(
                        kyro,
                        kyro.getRepresentationUnfogged() + ", please choose the planet you wish to drop " + infantry
                                + " infantry upon.",
                        placeInfantry));
    }
}
