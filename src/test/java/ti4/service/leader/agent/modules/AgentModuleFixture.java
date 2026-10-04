package ti4.service.leader.agent.modules;

import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentUse;

/**
 * A two-player game for agent module tests: the agent's owner and a target whose Project Pi faction id
 * ("pi_hacan") contains the "_" separator that the old split("_")[1] parsing truncated.
 */
final class AgentModuleFixture {

    static final String TARGET_FACTION = "pi_hacan";
    static final String TARGET_COLOR = "blue";
    static final String ACTIVE_SYSTEM = "101";

    final Game game;
    final Player user;
    final Player target;
    final Tile tile;

    AgentModuleFixture(String userFaction) {
        game = new Game();
        game.newGameSetup();
        game.setName("agent-module-test");
        user = game.addPlayer("agentUser", "Agent Player");
        user.setFaction(game, userFaction);
        user.setColor("red");
        target = game.addPlayer("targetUser", "Target Player");
        target.setFaction(game, TARGET_FACTION);
        target.setColor(TARGET_COLOR);
        // Tile 26 holds Lodor (3 resources / 1 influence).
        tile = new Tile("26", ACTIVE_SYSTEM);
        game.setTile(tile);
        game.setActiveSystem(ACTIVE_SYSTEM);
    }

    <P> AgentUse<P> use(AgentModule<P> module, P payload) {
        return AgentUse.of(
                module, game, user, user.getLeader(module.agentId()).orElseThrow(), module.agentId(), payload, null);
    }
}
