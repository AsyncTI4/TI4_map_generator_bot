package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelperStats;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class AxisAgent extends TargetedAgent {

    static final String ID = "axisagent";
    private static final int COMMODITIES_FOR_HELPING = 2;

    public AxisAgent() {
        super(ID, "Shipmonger Zsknck, the Axis");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player axis = use.user();
        Player target = use.payload();
        if (target != axis) {
            ButtonHelperStats.gainComms(use.event(), use.game(), axis, COMMODITIES_FOR_HELPING, false, true);
        }
        String owner = target.factionButtonChecker();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", please choose whether you wish to place 1 cruiser or 1 destroyer in a system with your ships.",
                List.of(
                        Buttons.green(owner + "step2axisagent_cruiser", "Place 1 cruiser"),
                        Buttons.green(owner + "step2axisagent_destroyer", "Place 1 destroyer"))));
    }
}
