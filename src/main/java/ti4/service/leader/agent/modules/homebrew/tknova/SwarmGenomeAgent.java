package ti4.service.leader.agent.modules.homebrew.tknova;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class SwarmGenomeAgent extends SelfOrTargetedAgent {

    static final String ID = "tknovaswarmagent";

    public SwarmGenomeAgent() {
        super(ID, "Swarm Genome");
    }

    public static Optional<Button> endOfTurnOffer(Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return Optional.empty();
        }
        return Optional.of(useButton(holder, ID, "Swarm Genome", FactionEmojis.Sardakk));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (target.getPlanetsAllianceMode().isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " controls no planet to place infantry on");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> planets =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(target, use.game(), "2gf", "placeOneNDone_skipbuild"));
        planets.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Done"));
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", use buttons to place 2 infantry on a planet you control with " + use.agentNameFor(target) + ".",
                planets));
    }
}
