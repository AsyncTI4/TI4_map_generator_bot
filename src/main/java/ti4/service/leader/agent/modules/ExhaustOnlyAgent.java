package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class ExhaustOnlyAgent implements AgentModule<ExhaustOnlyAgent.Choice> {

    public static final List<ExhaustOnlyAgent> ALL = List.of(
            new ExhaustOnlyAgent("saaragent", "Captain Mendosa, the Saar"),
            new ExhaustOnlyAgent("firmamentagent", "Myru Vos, the Firmament"),
            new ExhaustOnlyAgent("titansagent", "Tellurian, the Titans"),
            new ExhaustOnlyAgent("gheminaagent", "Skarvald & Torvar, the Ghemina"),
            new ExhaustOnlyAgent("nomadagentthundarian", "The Thundarian, a Nomad"),
            new ExhaustOnlyAgent("xanagent", "Noro Weba, the Xan"),
            new ExhaustOnlyAgent("nivynagent", "Suldhan Wraeg, the Nivyn"),
            new ExhaustOnlyAgent("edynagent", "Allant, the Edyn"),
            new ExhaustOnlyAgent("lanefiragent", "Vassa Hagi, the Lanefir"));

    private final String agentId;
    private final String displayName;

    public record Choice(Optional<Player> target) {}

    private ExhaustOnlyAgent(String agentId, String displayName) {
        this.agentId = agentId;
        this.displayName = displayName;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Optional<Choice> decode(Game game, Player user, String payload) {
        return Optional.of(new Choice(AgentTargets.player(game, payload)));
    }

    @Override
    public String exhaustAnnouncement(AgentUse<Choice> use) {
        String onTarget = use.payload()
                .target()
                .filter(target -> target != use.user())
                .map(target -> " on " + target.getRepresentationNoPing())
                .orElse("");
        return use.user().getRepresentation() + " has exhausted " + use.agentName() + onTarget + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return AgentOutcome.none();
    }
}
