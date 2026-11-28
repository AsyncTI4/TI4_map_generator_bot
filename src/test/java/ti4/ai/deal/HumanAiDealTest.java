package ti4.ai.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.TransactionHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.testUtils.BaseTi4Test;

/**
 * A pending offer from an AI seat (Nekro, holding Carth of Golden Sands) to a human (Sol, value 4, 0 commodities):
 * Carth replenishes Sol, Sol sends Nekro 3 commodities and Nekro sends Sol 1. The services that touch Discord are
 * replaced by small stand-ins that apply the same item rules to the two seats and record the order they ran in.
 */
class HumanAiDealTest extends BaseTi4Test {

    private AiTestGame test;
    private Game game;
    private Player ai;
    private Player human;
    private HumanAiDeal deal;
    private final List<String> order = new ArrayList<>();
    private MockedStatic<MessageHelper> messages;
    private MockedStatic<ButtonHelper> buttons;
    private MockedStatic<AgentLifecycle> agents;
    private MockedStatic<TransactionHelper> transactions;
    private ButtonInteractionEvent event;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        game = test.game;
        ai = test.nekro;
        human = test.sol;
        ai.addLeader("hacanagent");
        ai.setCommodities(1);
        ai.setTg(0);
        human.setCommoditiesBase(4);
        human.setCommodities(0);
        human.setTg(0);
        deal = new HumanAiDeal(
                "abc123",
                ai.getFaction(),
                human.getFaction(),
                "Carth of Golden Sands: 1 trade goods for you",
                game.getRound(),
                System.currentTimeMillis() + 3_600_000L,
                HumanAiDeal.Status.PENDING,
                "",
                "",
                List.of(
                        HumanAiDeal.Step.exhaust(
                                "hacanagent",
                                human.getFaction(),
                                3,
                                "uses Carth of Golden Sands to replenish your commodities"),
                        HumanAiDeal.Step.transfer(human.getFaction(), ai.getFaction(), HumanAiDeal.Item.COMMODITIES, 3),
                        HumanAiDeal.Step.transfer(
                                ai.getFaction(), human.getFaction(), HumanAiDeal.Item.COMMODITIES, 1)));
        HumanAiDealStore.save(game, deal);
        event = mock(ButtonInteractionEvent.class);
        messages = mockStatic(MessageHelper.class);
        buttons = mockStatic(ButtonHelper.class);
        agents = mockStatic(AgentLifecycle.class);
        transactions = mockStatic(TransactionHelper.class);
        agents.when(() -> AgentLifecycle.press(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    order.add("exhaust " + invocation.getArgument(0));
                    ai.getLeader("hacanagent").orElseThrow().setExhausted(true);
                    human.setCommodities(human.getCommoditiesTotal());
                    return null;
                });
        transactions
                .when(() -> TransactionHelper.acceptTransactionOffer(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<String> items = ai.getTransactionItemsWithPlayer(human);
                    order.add("transfer " + String.join(" ", items));
                    items.forEach(this::apply);
                    ai.clearTransactionItemsWithPlayer(human);
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        transactions.close();
        agents.close();
        buttons.close();
        messages.close();
    }

    private void apply(String item) {
        String[] parts = item.split("_");
        Player from = game.getPlayerFromColorOrFaction(parts[0].replace("sending", ""));
        Player to = game.getPlayerFromColorOrFaction(parts[1].replace("receiving", ""));
        int amount = Integer.parseInt(parts[3]);
        if ("Comms".equals(parts[2])) from.setCommodities(from.getCommodities() - amount);
        else from.setTg(from.getTg() - amount);
        to.setTg(to.getTg() + amount);
    }

    private HumanAiDeal stored() {
        return HumanAiDealStore.find(game, "abc123").orElseThrow();
    }

    // The embed names what the human gives and gets and what the AI does; Accept is green and Deny red, both owned by
    // the human's faction and short enough for Discord.
    @Test
    void theEmbedAndButtonsDescribeTheDeal() {
        MessageEmbed embed = HumanAiDealMessage.embed(game, deal);

        assertThat(embed.getTitle()).isEqualTo("Trade offer from " + ai.getFactionEmojiOrColor());
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).contains("You give", "You get");
        assertThat(field(embed, "You give")).contains("3 commodities");
        assertThat(field(embed, "You get")).contains("1 commodities");
        assertThat(field(embed, ai.getFactionEmojiOrColor() + " does"))
                .isEqualTo("uses Carth of Golden Sands to replenish your commodities");
        assertThat(field(embed, "This offer stands")).contains("<t:");

        List<Button> row = HumanAiDealMessage.buttons(deal);
        assertThat(row.get(0).getCustomId()).isEqualTo("FFCC_sol_aiDealAccept_abc123");
        assertThat(row.get(0).getStyle()).isEqualTo(ButtonStyle.SUCCESS);
        assertThat(row.get(1).getCustomId()).isEqualTo("FFCC_sol_aiDealDeny_abc123");
        assertThat(row.get(1).getStyle()).isEqualTo(ButtonStyle.DANGER);
        assertThat(row)
                .allSatisfy(button -> assertThat(button.getCustomId().length()).isLessThanOrEqualTo(100));
    }

    private static String field(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    // The deal survives a restart: it decodes from the game's stored value unchanged.
    @Test
    void theDealRoundTripsThroughTheGameStoredValue() {
        assertThat(HumanAiDealStore.find(game, "abc123")).contains(deal);
        assertThat(HumanAiDealStore.pendingFor(game, ai.getFaction())).contains(deal);
    }

    // Accept: Carth is exhausted on Sol first (Sol then holds 4), then one transaction moves 3 commodities to Nekro
    // and 1 to Sol. Sol ends with 1 commodity and 1 trade good; Nekro with 0 commodities and 3 trade goods.
    @Test
    void acceptRunsEveryStepInOrder() {
        HumanAiDealButtons.accept(event, game, human, "aiDealAccept_abc123");

        assertThat(order).hasSize(2);
        assertThat(order.get(0)).isEqualTo("exhaust exhaustAgent_hacanagent_sol");
        assertThat(order.get(1)).contains("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_1");
        assertThat(ai.hasUnexhaustedLeader("hacanagent")).isFalse();
        assertThat(human.getCommodities()).isEqualTo(1);
        assertThat(human.getTg()).isEqualTo(1);
        assertThat(ai.getCommodities()).isZero();
        assertThat(ai.getTg()).isEqualTo(3);
        assertThat(stored().status()).isEqualTo(HumanAiDeal.Status.ACCEPTED);
    }

    // The agent is already exhausted, so nothing runs and nothing moves.
    @Test
    void acceptThatNoLongerValidatesChangesNothing() {
        ai.getLeader("hacanagent").orElseThrow().setExhausted(true);

        HumanAiDealButtons.accept(event, game, human, "aiDealAccept_abc123");

        assertThat(order).isEmpty();
        assertThat(human.getCommodities()).isZero();
        assertThat(human.getTg()).isZero();
        assertThat(ai.getCommodities()).isEqualTo(1);
        assertThat(ai.getTg()).isZero();
        assertThat(stored().status()).isEqualTo(HumanAiDeal.Status.FAILED);
        messages.verify(() ->
                MessageHelper.sendMessageToChannel(any(), org.mockito.ArgumentMatchers.contains("no longer ready")));
    }

    // Sol already holds 2 of 4, so the replenish would create only 2 of the 3 commodities the deal needs.
    @Test
    void acceptIsRefusedWhenTheHumanIsNoLongerLowEnough() {
        human.setCommodities(2);

        HumanAiDealButtons.accept(event, game, human, "aiDealAccept_abc123");

        assertThat(order).isEmpty();
        assertThat(stored().status()).isEqualTo(HumanAiDeal.Status.FAILED);
    }

    @Test
    void denyOnlyRecordsTheAnswer() {
        HumanAiDealButtons.deny(event, game, human, "aiDealDeny_abc123");

        assertThat(order).isEmpty();
        assertThat(stored().status()).isEqualTo(HumanAiDeal.Status.DENIED);
        assertThat(human.getCommodities()).isZero();
        assertThat(ai.hasUnexhaustedLeader("hacanagent")).isTrue();
    }

    // Only the player the offer is for may answer; anyone else's press changes nothing.
    @Test
    void aPressByAnotherPlayerIsIgnored() {
        HumanAiDealButtons.accept(event, game, ai, "aiDealAccept_abc123");
        HumanAiDealButtons.deny(event, game, ai, "aiDealDeny_abc123");

        assertThat(order).isEmpty();
        assertThat(stored().status()).isEqualTo(HumanAiDeal.Status.PENDING);
        agents.verify(() -> AgentLifecycle.press(anyString(), any(), any(), any()), never());
    }

    // A deal that was answered once cannot be run a second time.
    @Test
    void anAnsweredDealCannotBeAcceptedAgain() {
        HumanAiDealButtons.accept(event, game, human, "aiDealAccept_abc123");
        order.clear();

        HumanAiDealButtons.accept(event, game, human, "aiDealAccept_abc123");

        assertThat(order).isEmpty();
    }
}
