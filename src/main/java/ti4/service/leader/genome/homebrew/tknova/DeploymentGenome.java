package ti4.service.leader.genome.homebrew.tknova;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.genome.Genome;

public final class DeploymentGenome extends Genome<Player> {

    static final String ID = "tknovadeploymentagent";

    public DeploymentGenome() {
        super(ID, "Deployment Genome");
    }

    public static Optional<Button> startOfTurnOffer(Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return Optional.empty();
        }
        return Optional.of(useButton(holder, ID, "Deployment Genome", FactionEmojis.Nomad));
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (target.getPlanetsAllianceMode().isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " controls no planet to move ground forces to");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", use buttons to choose the planet you wish to move up to 4 ground forces to with "
                        + use.agentNameFor(target) + ".",
                ButtonHelper.getTransitDiodesPlanetButtons(use.game(), target)));
    }
}
