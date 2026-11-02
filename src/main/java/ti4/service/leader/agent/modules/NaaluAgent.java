package ti4.service.leader.agent.modules;

import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class NaaluAgent extends TargetedAgent {

    static final String ID = "naaluagent";
    private static final String RING_REMINDER =
            " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";

    public NaaluAgent() {
        super(ID, "Z'eu, the Naalu");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player actor = use.payload();
        game.setNaaluAgent(true);
        return AgentOutcome.of(Message.addressedTo(
                actor,
                ", use buttons to resolve a tactical action from " + use.agentNameFor(actor)
                        + ". Reminder that you cannot do a tactical action in a home system this way.\n"
                        + "Please choose the ring of the map that the system you wish to activate is located in."
                        + (game.isFowMode() ? "" : RING_REMINDER),
                ButtonHelper.getPossibleRings(actor, game)));
    }
}
