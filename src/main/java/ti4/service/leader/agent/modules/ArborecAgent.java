package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ArborecAgent extends TargetedAgent {

    static final String ID = "arborecagent";

    public ArborecAgent() {
        super(ID, "Letani Ospha, the Arborec");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player replacer = use.payload();
        List<Button> systems = systemsWithShips(use.game(), replacer);
        if (systems.isEmpty()) {
            return AgentOutcome.of(use.noteToUser(replacer, " has no ships, so there is no system to use it in."));
        }
        List<Button> buttons = new ArrayList<>(systems);
        buttons.add(Buttons.red(replacer.factionButtonChecker() + "deleteButtons", "Decline"));
        return AgentOutcome.of(Message.addressedTo(
                replacer, ", please choose which system to use " + use.agentNameFor(replacer) + " in.", buttons));
    }

    private static List<Button> systemsWithShips(Game game, Player replacer) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(replacer, tile))
                .map(tile -> Buttons.green(
                        replacer.factionButtonChecker() + "arboAgentIn_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, replacer)))
                .toList();
    }
}
