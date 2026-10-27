package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome.Message;
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
        assertThatThrownBy(() -> AgentLifecycle.use(game, mentak, "yinagent", "", null))
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
        assertThat(AgentLifecycle.resolve("exhaustAgent_yinagent", null, game, mentak))
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

    @Test
    void anotherPlayersButtonsStayInAPublicPressedChannel() {
        MessageChannel actions = channel("actions");
        Player target = playerWithOwnChannels();
        Message prompt = Message.addressedTo(target, ", go.", List.of(Buttons.gray("x", "X")));

        assertThat(AgentLifecycle.deliveryChannel(prompt, pressIn(actions), mentak))
                .isSameAs(actions);
        assertThat(AgentLifecycle.sentAnotherPlayerButtonsElsewhere(prompt, actions, pressIn(actions), mentak))
                .isFalse();
    }

    @Test
    void anotherPlayersButtonsLeaveThePressersPrivateThread() {
        // Agent buttons in a cards-info thread are only visible to their holder.
        MessageChannel cardsInfo = channel("cards-info");
        when(cardsInfo.getType()).thenReturn(ChannelType.GUILD_PRIVATE_THREAD);
        Player target = playerWithOwnChannels();
        Message prompt = Message.addressedTo(target, ", go.", List.of(Buttons.gray("x", "X")));

        MessageChannel delivered = AgentLifecycle.deliveryChannel(prompt, pressIn(cardsInfo), mentak);

        assertThat(delivered).isSameAs(target.getCorrectChannel());
        assertThat(AgentLifecycle.sentAnotherPlayerButtonsElsewhere(prompt, delivered, pressIn(cardsInfo), mentak))
                .isTrue();
    }

    @Test
    void inFogOfWarAnotherPlayersMessagesGoToTheirOwnChannel() {
        game.setFowMode(true);
        MessageChannel actions = channel("actions");
        Player target = playerWithOwnChannels();

        assertThat(AgentLifecycle.deliveryChannel(Message.plain(target, "hello"), pressIn(actions), mentak))
                .isSameAs(target.getCorrectChannel());
    }

    @Test
    void theUsersOwnMessagesStayWhereTheyPressed() {
        MessageChannel cardsInfo = channel("cards-info");
        when(cardsInfo.getType()).thenReturn(ChannelType.GUILD_PRIVATE_THREAD);

        assertThat(AgentLifecycle.deliveryChannel(Message.addressedTo(mentak, " hi."), pressIn(cardsInfo), mentak))
                .isSameAs(cardsInfo);
    }

    @Test
    void cardsInfoMessagesAlwaysGoToTheRecipientsThread() {
        Player target = playerWithOwnChannels();

        assertThat(AgentLifecycle.deliveryChannel(
                        Message.plain(target, "secret").inCardsInfoThread(), pressIn(channel("actions")), mentak))
                .isSameAs(target.getCardsInfoThread());
    }

    @Test
    void theConfirmationHidesTheTargetInFogOfWar() {
        Player target = playerWithOwnChannels();
        when(target.getRepresentationNoPing()).thenReturn("Hacan");

        assertThat(AgentLifecycle.sentButtonsConfirmation(target)).isEqualTo("Sent the buttons to Hacan.");
        game.setFowMode(true);
        assertThat(AgentLifecycle.sentButtonsConfirmation(target)).isEqualTo("Sent the buttons to the chosen player.");
    }

    @Test
    void offerFallsBackWhenThereIsNothingToOffer() {
        Message prompt = Message.addressedTo(mentak, ", pick one.", List.of());
        Message nothing = Message.addressedTo(mentak, " has nothing to pick.");

        assertThat(AgentOutcome.offer(prompt, nothing).messages()).containsExactly(nothing);
    }

    @Test
    void otherPlayersSeeAFogSafeAgentNameInFogOfWar() {
        Player target = playerWithOwnChannels();
        AgentUse<Player> use = new AgentUse<>(game, mentak, target, "Suffi An, the Mentak agent", false, null);

        assertThat(use.agentNameFor(target)).isEqualTo("Suffi An, the Mentak agent");
        game.setFowMode(true);
        assertThat(use.agentNameFor(target)).isEqualTo("an agent");
        assertThat(use.agentNameFor(mentak)).isEqualTo("Suffi An, the Mentak agent");
    }

    private Player playerWithOwnChannels() {
        Player target = mock(Player.class);
        TextChannel own = mock(TextChannel.class);
        when(own.getId()).thenReturn("target-own");
        ThreadChannel cardsInfo = mock(ThreadChannel.class);
        when(target.getGame()).thenReturn(game);
        when(target.getCorrectChannel()).thenReturn(own);
        when(target.getCardsInfoThread()).thenReturn(cardsInfo);
        return target;
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
