package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static ti4.service.leader.agent.modules.AgentModuleFixture.NOT_AN_AGENT_MODULE;

import java.util.ArrayList;
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
import ti4.service.leader.LeaderAgents;
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
        assertThatThrownBy(() -> AgentLifecycle.use(game, mentak, NOT_AN_AGENT_MODULE, "", null))
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
    void aPressedAgentWithAModuleIsHandledByTheLifecycle() {
        // Exhausting needs the Spring context, so the agent is already exhausted and the press is refused.
        mentak.getLeader("mentakagent").orElseThrow().setExhausted(true);

        AgentLifecycle.press("exhaustAgent_mentakagent_pi_hacan", null, game, mentak);

        assertThat(mentak.getLeader("mentakagent").orElseThrow().isExhausted()).isTrue();
    }

    @Test
    void anAgentWithoutAModuleFallsBackToAPlainExhaust() {
        assertThat(LeaderAgents.findRegistered(game, NOT_AN_AGENT_MODULE)).isEmpty();
        assertThat(LeaderAgents.find(game, NOT_AN_AGENT_MODULE)).isInstanceOf(PlainExhaustAgent.class);
    }

    @Test
    void anAgentWithoutAModuleIsNamedFromItsLeaderCardAndFaction() {
        assertThat(AgentLifecycle.cardName(game, "mahactagent")).isEqualTo("Jae Mir Kan, the Mahact");
        assertThat(AgentLifecycle.agentName(game, "naaluagent-te", false))
                .startsWith("Z'eu")
                .endsWith(", the Naalu agent");
    }

    @Test
    void spendingACleverCleverCopyExhaustsTheYssarilAgentWithoutTheSpringContext() {
        Player yssaril = game.addPlayer("yssarilUser", "Yssaril Player");
        yssaril.setFaction(game, "yssaril");
        yssaril.setColor("blue");

        AgentLifecycle.spend(game, yssaril, "mentakagent");

        assertThat(yssaril.getLeader("yssarilagent").orElseThrow().isExhausted())
                .isTrue();
        assertThat(mentak.getLeader("mentakagent").orElseThrow().isExhausted()).isFalse();
    }

    @Test
    void temporalCommandSuiteLabelsFitInAButtonEvenForCleverCleverCopies() {
        for (boolean twilightsFall : List.of(false, true)) {
            game.setTwilightsFallMode(twilightsFall);
            for (AgentModule<?> module : LeaderAgents.all()) {
                for (String leaderId : leaderIds(module)) {
                    String label = AgentLifecycle.temporalCommandSuiteLabel(
                            game, AgentLifecycle.agentName(game, leaderId, true));
                    assertThat(label).as(leaderId).hasSizeLessThanOrEqualTo(80);
                }
            }
        }
        game.setFowMode(true);
        Player other = game.addPlayer("otherUser", "Other Player");
        assertThat(AgentLifecycle.agentNameShownTo(game, other, mentak, "mentakagent", false))
                .isEqualTo("an agent");
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

        assertThat(AgentLifecycle.agentNameForHolder(game, mentak, "saaragent")).isEqualTo("the _Captain's Genome_");
    }

    @Test
    void noGenomeIsCalledAnAgentInTwilightsFall() {
        game.setTwilightsFallMode(true);

        for (AgentModule<?> module : LeaderAgents.all()) {
            for (String leaderId : leaderIds(module)) {
                String cardName = AgentLifecycle.cardName(game, leaderId);
                if (cardName.endsWith("Genome")) {
                    assertThat(AgentLifecycle.agentName(cardName, false))
                            .as(leaderId)
                            .doesNotContain("agent");
                    assertThat(AgentLifecycle.agentName(cardName, true))
                            .as(leaderId)
                            .doesNotContain("agent");
                }
            }
        }
    }

    @Test
    void eachRebellionCardIsNamedForItself() {
        assertThat(AgentLifecycle.agentName(game, "crimsonagent", false)).isEqualTo("Ahk Ravin, the Rebellion agent");
        assertThat(AgentLifecycle.agentName(game, "redcreussagent", false))
                .isEqualTo("\"Who Knows?\", the Rebellion agent");
        game.setTwilightsFallMode(true);
        assertThat(AgentLifecycle.agentName(game, "crimsonagent", false)).isEqualTo("the _Breach Genome_");
    }

    @Test
    void stepHandlersHideTheAgentFromOtherPlayersInFogOfWar() {
        Player victim = playerWithOwnChannels();

        assertThat(AgentLifecycle.agentNameShownTo(game, victim, mentak, "mentakagent", false))
                .isEqualTo("Suffi An, the Mentak agent");
        game.setFowMode(true);
        assertThat(AgentLifecycle.agentNameShownTo(game, victim, mentak, "mentakagent", false))
                .isEqualTo("an agent");
        assertThat(AgentLifecycle.agentNameShownTo(game, mentak, mentak, "mentakagent", false))
                .isEqualTo("Suffi An, the Mentak agent");
    }

    @Test
    void theAnnouncementLeavesAPrivateThreadForEveryoneToSee() {
        // Exhausting an agent is public, even when the button sat in the presser's cards-info thread.
        Player holder = playerWithOwnChannels();
        MessageChannel cardsInfo = channel("cards-info");
        when(cardsInfo.getType()).thenReturn(ChannelType.GUILD_PRIVATE_THREAD);
        Message announcement =
                Message.addressedTo(holder, " has exhausted an agent.").forEveryone();

        assertThat(AgentLifecycle.deliveryChannel(announcement, pressIn(cardsInfo), holder))
                .isSameAs(holder.getCorrectChannel());
        MessageChannel actions = channel("actions");
        assertThat(AgentLifecycle.deliveryChannel(announcement, pressIn(actions), holder))
                .isSameAs(actions);
    }

    @Test
    void notesAboutAnotherPlayerGoToTheUserWithoutPingingThem() {
        Player target = playerWithOwnChannels();
        when(target.getFactionEmojiOrColor()).thenReturn(":hacan:");
        AgentUse<Player> use = new AgentUse<>(game, mentak, target, "Suffi An, the Mentak agent", false, null);

        Message note = use.noteToUser(target, " has nothing to offer.");

        assertThat(note.recipient()).isSameAs(mentak);
        assertThat(note.namesRecipient()).isFalse();
        assertThat(note.text()).isEqualTo(":hacan: has nothing to offer.");
        assertThat(use.noteToUser(mentak, " has nothing.").namesRecipient()).isTrue();
    }

    @Test
    void anOutcomeCanAskForTheWholePressedMessageToGo() {
        assertThat(AgentLifecycle.deletesWholeMessage("Use the agent or decline.", true))
                .isTrue();
        assertThat(AgentLifecycle.deletesWholeMessage("Use the agent or decline.", false))
                .isFalse();
        assertThat(AgentLifecycle.deletesWholeMessage("Mentak, please choose the target of your agent.", false))
                .isTrue();
    }

    private static List<String> leaderIds(AgentModule<?> module) {
        List<String> ids = new ArrayList<>(module.aliasIds());
        ids.add(module.agentId());
        return ids;
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
