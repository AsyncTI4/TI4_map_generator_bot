package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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
import ti4.helpers.TransactionHelper;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// Sol (a human) answers Nekro's speaker sale. Buying pays Nekro the price through the normal transaction and records
// the promise; the services that touch Discord are stand-ins that apply the same trade good move.
class SpeakerSaleButtonsTest extends BaseTi4Test {

    private static final String BUY = "speakerBuy_2_nekro";
    private static final String DECLINE = "speakerNo_2_nekro";

    private AiTestGame test;
    private Game game;
    private Player buyer;
    private final List<String> transfers = new ArrayList<>();
    private MockedStatic<MessageHelper> messages;
    private MockedStatic<ButtonHelper> buttons;
    private MockedStatic<TransactionHelper> transactions;
    private ButtonInteractionEvent event;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        game = test.game;
        buyer = test.sol;
        buyer.setTg(3);
        test.nekro.setTg(0);
        new SpeakerPromise(
                        "nekro",
                        "sol",
                        2,
                        2,
                        SpeakerPromise.Status.OFFERED,
                        System.currentTimeMillis(),
                        System.currentTimeMillis() + 600_000,
                        SpeakerPromise.NOT_DECLARED)
                .save(game);
        event = mock(ButtonInteractionEvent.class);
        messages = mockStatic(MessageHelper.class);
        buttons = mockStatic(ButtonHelper.class);
        transactions = mockStatic(TransactionHelper.class);
        transactions
                .when(() -> TransactionHelper.acceptTransactionOffer(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    transfers.addAll(test.nekro.getTransactionItemsWithPlayer(buyer));
                    buyer.setTg(buyer.getTg() - 2);
                    test.nekro.setTg(test.nekro.getTg() + 2);
                    test.nekro.clearTransactionItemsWithPlayer(buyer);
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        transactions.close();
        buttons.close();
        messages.close();
    }

    @Test
    void buyingPaysTheSellerAndRecordsThePromise() {
        SpeakerSaleButtons.buy(event, game, buyer, BUY);

        assertThat(transfers).containsExactly("sendingsol_receivingnekro_TGs_2");
        assertThat(buyer.getTg()).isEqualTo(1);
        assertThat(test.nekro.getTg()).isEqualTo(2);
        assertThat(SpeakerPromise.read(game).orElseThrow().status()).isEqualTo(SpeakerPromise.Status.ACCEPTED);
    }

    @Test
    void decliningRecordsTheRefusal() {
        SpeakerSaleButtons.decline(event, game, buyer, DECLINE);

        assertThat(transfers).isEmpty();
        assertThat(SpeakerPromise.read(game).orElseThrow().status()).isEqualTo(SpeakerPromise.Status.DECLINED);
    }

    @Test
    void aPressByAnotherPlayerChangesNothing() {
        SpeakerSaleButtons.buy(event, game, test.nekro, BUY);

        assertThat(transfers).isEmpty();
        assertThat(SpeakerPromise.read(game).orElseThrow().status()).isEqualTo(SpeakerPromise.Status.OFFERED);
    }

    @Test
    void aBuyerShortOfTradeGoodsChangesNothing() {
        buyer.setTg(1);

        SpeakerSaleButtons.buy(event, game, buyer, BUY);

        assertThat(transfers).isEmpty();
        assertThat(SpeakerPromise.read(game).orElseThrow().status()).isEqualTo(SpeakerPromise.Status.OFFERED);
    }
}
