package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class RebellionAgent extends TargetedAgent {

    static final String ID = "redcreussagent";
    private static final String THUNDERS_EDGE_ID = "crimsonagent";

    public RebellionAgent() {
        super(ID, "Ahk Ravin, the Rebellion");
    }

    @Override
    public Set<String> aliasIds() {
        return Set.of(THUNDERS_EDGE_ID);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player swapper = use.payload();
        List<Button> systems = ButtonHelper.getTilesWithUnitsInTheSpaceArea(swapper, use.game()).stream()
                .map(tile -> Buttons.gray(
                        swapper.factionButtonChecker() + "redcreussAgentPart1_" + tile.getPosition(),
                        tile.getRepresentationForButtons(use.game(), swapper)))
                .toList();
        return AgentOutcome.offer(
                Message.addressedTo(
                        swapper,
                        ", please choose the first system that you wish to swap a ship between (and transport).",
                        systems),
                Message.addressedTo(swapper, " has no ships to swap."));
    }
}
