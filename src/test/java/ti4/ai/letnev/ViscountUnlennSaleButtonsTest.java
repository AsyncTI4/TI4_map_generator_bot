package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.TransactionHelper;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// A buyer pressing Buy pays Letnev one trade good and the agent is exhausted on the buyer; the services that touch
// Discord are replaced by stand-ins that apply the same item rules and record the order they ran in.
class ViscountUnlennSaleButtonsTest extends BaseTi4Test {

    private static final String POSITION = "401";

    private AiTestGame test;
    private Game game;
    private Player letnev;
    private Player buyer;
    private final List<String> order = new ArrayList<>();
    private MockedStatic<MessageHelper> messages;
    private MockedStatic<ButtonHelper> buttons;
    private MockedStatic<ButtonHelperAgents> agents;
    private MockedStatic<TransactionHelper> transactions;
    private ButtonInteractionEvent event;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        game = test.game;
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        letnev.addLeader("letnevagent");
        letnev.setTg(0);
        buyer = test.sol;
        buyer.setTg(2);
        event = mock(ButtonInteractionEvent.class);
        messages = mockStatic(MessageHelper.class);
        buttons = mockStatic(ButtonHelper.class);
        agents = mockStatic(ButtonHelperAgents.class);
        transactions = mockStatic(TransactionHelper.class);
        agents.when(() -> ButtonHelperAgents.exhaustAgent(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    order.add("exhaust " + invocation.getArgument(0));
                    letnev.getLeader("letnevagent").orElseThrow().setExhausted(true);
                    return null;
                });
        transactions
                .when(() -> TransactionHelper.acceptTransactionOffer(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<String> items = letnev.getTransactionItemsWithPlayer(buyer);
                    order.add("transfer " + String.join(" ", items));
                    buyer.setTg(buyer.getTg() - 1);
                    letnev.setTg(letnev.getTg() + 1);
                    letnev.clearTransactionItemsWithPlayer(buyer);
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

    private String buyButton(Player player) {
        return "unlennBuy_" + POSITION + "_0_" + player.getFaction();
    }

    private String declineButton(Player player) {
        return "unlennNo_" + POSITION + "_0_" + player.getFaction();
    }

    @Test
    void buyingExhaustsTheAgentOnTheBuyerAndPaysLetnev() {
        ViscountUnlennSaleButtons.buy(event, game, buyer, buyButton(buyer));

        assertThat(order).hasSize(2);
        assertThat(order.get(0)).isEqualTo("exhaust exhaustAgent_letnevagent_sol");
        assertThat(order.get(1)).contains("sendingsol_receivingletnev_TGs_1");
        assertThat(letnev.hasUnexhaustedLeader("letnevagent")).isFalse();
        assertThat(buyer.getTg()).isEqualTo(1);
        assertThat(letnev.getTg()).isEqualTo(1);
    }

    @Test
    void theSecondBuyerFindsTheAgentGone() {
        ViscountUnlennSaleButtons.buy(event, game, buyer, buyButton(buyer));
        order.clear();

        ViscountUnlennSaleButtons.buy(event, game, test.nekro, buyButton(test.nekro));

        assertThat(order).isEmpty();
        messages.verify(() -> MessageHelper.sendMessageToChannel(any(), contains("no longer on offer")));
    }

    @Test
    void aBuyerWithoutTradeGoodsChangesNothing() {
        buyer.setTg(0);

        ViscountUnlennSaleButtons.buy(event, game, buyer, buyButton(buyer));

        assertThat(order).isEmpty();
        assertThat(letnev.hasUnexhaustedLeader("letnevagent")).isTrue();
        messages.verify(
                () -> MessageHelper.sendEphemeralMessageToEventChannel(any(), contains("no longer has enough")));
    }

    @Test
    void theOfferLapsesOnceTheBuyerHasRolled() {
        game.setStoredValue("combatRoundTracker" + buyer.getFaction() + POSITION + "space", "1");

        ViscountUnlennSaleButtons.buy(event, game, buyer, buyButton(buyer));

        assertThat(order).isEmpty();
        assertThat(letnev.hasUnexhaustedLeader("letnevagent")).isTrue();
    }

    @Test
    void aPressByAnotherPlayerIsIgnored() {
        ViscountUnlennSaleButtons.buy(event, game, test.nekro, buyButton(buyer));
        ViscountUnlennSaleButtons.decline(event, game, test.nekro, declineButton(buyer));

        assertThat(order).isEmpty();
        agents.verify(() -> ButtonHelperAgents.exhaustAgent(anyString(), any(), any(), any()), never());
    }

    @Test
    void decliningOnlyRemovesThatBuyersButtons() {
        ViscountUnlennSaleButtons.decline(event, game, buyer, declineButton(buyer));

        assertThat(order).isEmpty();
        assertThat(letnev.hasUnexhaustedLeader("letnevagent")).isTrue();
        buttons.verify(() -> ButtonHelper.deleteButtonsWithPartialID(event, "_" + POSITION + "_0_sol"));
    }
}
