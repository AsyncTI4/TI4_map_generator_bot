package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentUse;

/**
 * A two-player game for agent module tests: the agent's owner and a target whose Project Pi faction id
 * ("pi_hacan") contains the "_" separator that the old split("_")[1] parsing truncated.
 */
public final class AgentModuleFixture {

    public static final int DISCORD_ID_LIMIT = 100;
    public static final int DISCORD_LABEL_LIMIT = 80;

    public static final String TARGET_FACTION = "pi_hacan";
    public static final String TARGET_COLOR = "blue";
    public static final String ACTIVE_SYSTEM = "101";

    public final Game game;
    public final Player user;
    public final Player target;
    public final Tile tile;

    public AgentModuleFixture(String userFaction) {
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

    public <P> AgentUse<P> use(AgentModule<P> module, P payload) {
        return new AgentUse<>(game, user, payload, module.displayName() + " agent", false, null);
    }

    public <P> AgentUse<P> useAsYssarilCopy(AgentModule<P> module, P payload) {
        return new AgentUse<>(
                game, user, payload, "Clever Clever " + module.displayName() + "/Yssaril agent", true, null);
    }

    // Only the owner can press the button, and the router still hands the exact legacy id to the handler.
    public static void assertOwnedAgentButton(Button button, Player owner, String legacyHandlerId) {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode(button.getCustomId());
        assertThat(envelope.ownerFaction()).isEqualTo(owner.getFaction());
        assertThat(envelope.handlerId()).isEqualTo(legacyHandlerId);
        assertThat(button.getCustomId()).hasSizeLessThanOrEqualTo(DISCORD_ID_LIMIT);
        assertThat(button.getLabel()).hasSizeLessThanOrEqualTo(DISCORD_LABEL_LIMIT);
    }
}
