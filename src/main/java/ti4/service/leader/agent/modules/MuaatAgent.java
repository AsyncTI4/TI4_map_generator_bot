package ti4.service.leader.agent.modules;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.unit.CheckUnitContainmentService;

public final class MuaatAgent extends TargetedAgent {

    static final String ID = "muaatagent";

    public MuaatAgent() {
        super(ID, "Umbat, the Muaat");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player producer = use.payload();
        List<Button> systems =
                CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                use.game(), producer, UnitType.Warsun, UnitType.Flagship)
                        .stream()
                        .map(tile -> Buttons.green(
                                producer.factionButtonChecker() + "umbatTile_" + tile.getPosition(),
                                tile.getRepresentationForButtons(use.game(), producer)))
                        .toList();
        return AgentOutcome.offer(
                Message.addressedTo(
                        producer,
                        ", please choose which system to use " + use.agentNameFor(producer) + " in.",
                        systems),
                Message.addressedTo(producer, " has no war sun or flagship to produce units with."));
    }
}
