package ti4.service.leader.agent.modules.homebrew;

import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class VeylorAgent extends TargetedAgent {

    static final String ID = "veyloragent";

    public VeylorAgent() {
        super(ID, "Orrin Malvek, the Veylor");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        ActionCardHelper.drawActionCards(target, 1);
        AgendaHelper.drawAgenda(1, use.game(), target);
        return AgentOutcome.of(Message.to(target, target.getRepresentation() + " drew 1 agenda."));
    }
}
