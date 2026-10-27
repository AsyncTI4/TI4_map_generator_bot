package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgentLifecycleTest extends BaseTi4Test {

    private Game game;
    private Player mentak;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("agent-lifecycle-test");
        mentak = game.addPlayer("mentakUser", "Mentak Player");
        mentak.setFaction(game, "mentak");
        mentak.setColor("red");
    }

    @Test
    void anExhaustedAgentIsRefused() {
        Leader agent = mentak.getLeader("mentakagent").orElseThrow();
        agent.setExhausted(true);
        int actionCards = mentak.getAcCount();

        assertThat(AgentLifecycle.refuseIfExhausted(null, mentak, agent)).isTrue();
        assertThat(AgentLifecycle.use(game, mentak, "mentakagent", "red", null)).isFalse();
        assertThat(mentak.getAcCount()).isEqualTo(actionCards);
    }

    @Test
    void aReadiedAgentIsNotRefused() {
        Leader agent = mentak.getLeader("mentakagent").orElseThrow();

        assertThat(AgentLifecycle.refuseIfExhausted(null, mentak, agent)).isFalse();
    }

    @Test
    void anUnresolvablePayloadLeavesTheAgentReadied() {
        assertThat(AgentLifecycle.use(game, mentak, "mentakagent", "", null)).isFalse();
        assertThat(mentak.getLeader("mentakagent").orElseThrow().isExhausted()).isFalse();
    }

    @Test
    void inProcessUseRequiresAModule() {
        assertThatThrownBy(() -> AgentLifecycle.use(game, mentak, "hacanagent", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesTheRealCardByItsDisplayName() {
        assertThat(AgentLifecycle.agentName("Clodho, the Ilyxum", false)).isEqualTo("Clodho, the Ilyxum agent");
    }

    @Test
    void namesAYssarilCopyAsCleverClever() {
        assertThat(AgentLifecycle.agentName("Clodho, the Ilyxum", true))
                .isEqualTo("Clever Clever Clodho, the Ilyxum/Yssaril agent");
    }

    @Test
    void theYssarilAgentStandingInForAnotherAgentIsACopy() {
        assertThat(AgentLifecycle.isYssarilCopy("yssarilagent", "augersagent")).isTrue();
    }

    @Test
    void theYssarilAgentUsedAsItselfIsNotACopy() {
        assertThat(AgentLifecycle.isYssarilCopy("yssarilagent", "yssarilagent")).isFalse();
        assertThat(AgentLifecycle.isYssarilCopy("augersagent", "augersagent")).isFalse();
    }

    @Test
    void anAgentWithAModuleIsHandledByTheLifecycle() {
        // Exhausting needs the Spring context, so the agent is already exhausted and the press is refused.
        mentak.getLeader("mentakagent").orElseThrow().setExhausted(true);

        assertThat(AgentLifecycle.resolve("exhaustAgent_mentakagent_pi_hacan", null, game, mentak))
                .isTrue();
    }

    @Test
    void anAgentWithoutAModuleIsLeftToTheLegacyChain() {
        assertThat(AgentLifecycle.resolve("exhaustAgent_hacanagent", null, game, mentak))
                .isFalse();
    }

    @Test
    void namesBlueReverieYssarilCopiesAsCopies() {
        assertThat(AgentLifecycle.isYssarilCopy("yssarilagent_br", "mentakagent"))
                .isTrue();
        assertThat(AgentLifecycle.isYssarilCopy("yssarilagent_br", "yssarilagent_br"))
                .isFalse();
    }

    @Test
    void namesGenomesWithoutTheAgentSuffix() {
        assertThat(AgentLifecycle.agentName("Captain's Genome", false)).isEqualTo("the _Captain's Genome_");
        assertThat(AgentLifecycle.agentName("Captain's Genome", true))
                .isEqualTo("the Clever Clever _Captain's Genome_");
    }

    @Test
    void usesTheTwilightsFallNameInTwilightsFallGames() {
        game.setTwilightsFallMode(true);

        assertThat(AgentLifecycle.agentNameFor(game, mentak, "saaragent")).isEqualTo("the _Captain's Genome_");
    }

    @Test
    void noGenomeIsCalledAnAgentInTwilightsFall() {
        game.setTwilightsFallMode(true);

        for (AgentModule<?> module : AgentModules.ALL) {
            String cardName = AgentLifecycle.cardName(game, module);
            if (cardName.endsWith("Genome")) {
                assertThat(AgentLifecycle.agentName(cardName, false))
                        .as(module.agentId())
                        .doesNotContain("agent");
                assertThat(AgentLifecycle.agentName(cardName, true))
                        .as(module.agentId())
                        .doesNotContain("agent");
            }
        }
    }

    @Test
    void thePresserIsNotPingedInTheChannelTheyPressedIn() {
        MessageChannel pressedChannel = channel("1");
        GenericInteractionCreateEvent press = pressIn(pressedChannel);

        assertThat(AgentLifecycle.addresseeName(mentak, pressedChannel, press, mentak))
                .isEqualTo(mentak.getRepresentationNoPing());
    }

    @Test
    void thePresserIsPingedInAnyOtherChannel() {
        GenericInteractionCreateEvent press = pressIn(channel("1"));

        assertThat(AgentLifecycle.addresseeName(mentak, channel("2"), press, mentak))
                .isEqualTo(mentak.getRepresentationUnfogged());
        // Without a pressed agent button the message lands in the player's own channel, so they are pinged.
        assertThat(AgentLifecycle.addresseeName(mentak, channel("1"), null, mentak))
                .isEqualTo(mentak.getRepresentationUnfogged());
    }

    @Test
    void anotherPlayerIsPingedEvenInThePressedChannel() {
        Player target = game.addPlayer("targetUser", "Target Player");
        target.setFaction(game, "hacan");
        target.setColor("blue");
        MessageChannel pressedChannel = channel("1");

        assertThat(AgentLifecycle.addresseeName(target, pressedChannel, pressIn(pressedChannel), mentak))
                .isEqualTo(target.getRepresentationUnfogged());
    }

    private static MessageChannel channel(String id) {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.getId()).thenReturn(id);
        return channel;
    }

    private static GenericInteractionCreateEvent pressIn(MessageChannel channel) {
        GenericInteractionCreateEvent press = mock(GenericInteractionCreateEvent.class);
        when(press.getMessageChannel()).thenReturn(channel);
        return press;
    }
}
