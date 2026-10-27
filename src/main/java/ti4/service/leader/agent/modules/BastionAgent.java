package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.unit.GalvanizeService;

public final class BastionAgent extends TargetedAgent {

    static final String ID = "bastionagent";

    public BastionAgent() {
        super(ID, "Dame Briar, the Bastion");
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (activeSystem(game).isEmpty()) {
            return Optional.of("There is no active system");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose the unit that should be galvanized.",
                        GalvanizeService.getToggleGalvanizeButtons(
                                target, use.game(), activeSystem(use.game()).orElseThrow())),
                Message.addressedTo(target, " has no units that can be galvanized in the active system."));
    }
}
