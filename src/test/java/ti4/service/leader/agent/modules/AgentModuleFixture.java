package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

/**
 * A two-player game for agent module tests: the agent's owner and a target whose Project Pi faction id
 * ("pi_hacan") contains the "_" separator that the old split("_")[1] parsing truncated.
 */
public final class AgentModuleFixture {

    public static final int DISCORD_ID_LIMIT = 100;
    public static final int DISCORD_LABEL_LIMIT = 80;

    // An id that will never get a module, for tests about agents the router does not know.
    public static final String NOT_AN_AGENT_MODULE = "notarealagent";

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
        return new AgentUse<>(game, user, payload, module.reference(module.displayName(), false), false, null);
    }

    public PlayerPickerAgent.Choice playerPicker(PlayerPickerAgent agent) {
        return agent.decode(game, user, "").orElseThrow();
    }

    public PlayerPickerAgent.Choice pickedPlayer(PlayerPickerAgent agent, Player picked) {
        return agent.decode(game, user, picked.getColor()).orElseThrow();
    }

    public <P> AgentUse<P> useAsYssarilCopy(AgentModule<P> module, P payload) {
        return new AgentUse<>(game, user, payload, module.reference(module.displayName(), true), true, null);
    }

    // Only the owner can press the button, and the router still hands the exact legacy id to the handler.
    public static void assertOwnedAgentButton(Button button, Player owner, String legacyHandlerId) {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode(button.getCustomId());
        assertThat(envelope.ownerFaction()).isEqualTo(owner.getFaction());
        assertThat(envelope.handlerId()).isEqualTo(legacyHandlerId);
        assertThat(button.getCustomId()).hasSizeLessThanOrEqualTo(DISCORD_ID_LIMIT);
        assertThat(button.getLabel()).hasSizeLessThanOrEqualTo(DISCORD_LABEL_LIMIT);
    }

    public static void assertOwnedBy(Button button, Player owner) {
        assertThat(ComponentIdEnvelope.decode(button.getCustomId()).ownerFaction())
                .isEqualTo(owner.getFaction());
        assertThat(button.getCustomId()).hasSizeLessThanOrEqualTo(DISCORD_ID_LIMIT);
    }

    // A follow-up step button: only the owner can press it, and it carries the Clever Clever flag when it should.
    public static void assertOwnedStep(Button button, Player owner, boolean viaYssaril) {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode(button.getCustomId());
        assertThat(envelope.ownerFaction()).isEqualTo(owner.getFaction());
        assertThat(AgentStepIds.parse(envelope.handlerId()).viaYssaril()).isEqualTo(viaYssaril);
        assertThat(button.getCustomId()).hasSizeLessThanOrEqualTo(DISCORD_ID_LIMIT);
        assertThat(button.getLabel()).hasSizeLessThanOrEqualTo(DISCORD_LABEL_LIMIT);
    }

    public void assertDecodesTargets(AgentModule<Player> module) {
        assertThat(module.decode(game, user, TARGET_FACTION))
                .as(module.agentId() + " with a pi_ faction")
                .contains(target);
        assertThat(module.decode(game, user, TARGET_COLOR))
                .as(module.agentId() + " with a colour")
                .contains(target);
        assertThat(module.decode(game, user, ""))
                .as(module.agentId() + " without a target")
                .isEmpty();
        assertThat(module.decode(game, user, "nobody"))
                .as(module.agentId() + " with an unknown target")
                .isEmpty();
    }
}
