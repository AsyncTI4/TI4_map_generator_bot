package ti4.service.leader.agent;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;

public final class ExhaustOnlyAgent extends AgentModule<ExhaustOnlyAgent.Choice> {

    public record Choice(Optional<Player> target) {}

    public ExhaustOnlyAgent(String agentId, String displayName) {
        super(agentId, displayName);
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
