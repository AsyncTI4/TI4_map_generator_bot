package ti4.ai.trade;

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
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.TransactionHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// Sol (a human) answers Nekro's offer to promise not to attack it this round for 2 trade goods. Accepting pays Nekro
// through the normal transaction and records the promise with the systems it protects; the services that touch
// Discord are stand-ins that apply the same trade good move.
class PromiseDealButtonsTest extends BaseTi4Test {

    private AiTestGame test;
    private Game game;
    private Player sol;
    private Promise promise;
    private final List<String> transfers = new ArrayList<>();
    private MockedStatic<MessageHelper> messages;
    private MockedStatic<ButtonHelper> buttons;
    private MockedStatic<TransactionHelper> transactions;
    private ButtonInteractionEvent event;
    private Tile lodor;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        game = test.game;
        sol = test.sol;
        test.nekroHome();
        test.units(test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME)), "space", sol, UnitType.Carrier, 1);
        lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        sol.addPlanet("lodor");
        test.aiIsActive("action");
        sol.setTg(3);
        test.nekro.setTg(0);
        promise = Promise.offer(game, Promise.Term.NO_ATTACK, test.nekro, sol, test.nekro, "", "", 0, 2, 10, 600_000);
        PromiseBook.save(game, promise);
        event = mock(ButtonInteractionEvent.class);
        messages = mockStatic(MessageHelper.class);
        buttons = mockStatic(ButtonHelper.class);
        transactions = mockStatic(TransactionHelper.class);
        transactions
                .when(() -> TransactionHelper.acceptTransactionOffer(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    transfers.addAll(test.nekro.getTransactionItemsWithPlayer(sol));
                    sol.setTg(sol.getTg() - 2);
                    test.nekro.setTg(test.nekro.getTg() + 2);
                    test.nekro.clearTransactionItemsWithPlayer(sol);
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        transactions.close();
        buttons.close();
        messages.close();
    }

    private Promise stored() {
        return PromiseBook.find(game, promise.id()).orElseThrow();
    }

    @Test
    void acceptingPaysThePromiserAndRecordsWhatItProtects() {
        PromiseDealButtons.accept(event, game, sol, PromiseDeals.ACCEPT_PREFIX + promise.id());

        assertThat(transfers).containsExactly("sendingsol_receivingnekro_TGs_2");
        assertThat(sol.getTg()).isEqualTo(1);
        assertThat(stored().status()).isEqualTo(Promise.Status.ACCEPTED);
        assertThat(stored().guarded()).isNotEmpty();
    }

    @Test
    void decliningRecordsTheRefusal() {
        PromiseDealButtons.decline(event, game, sol, PromiseDeals.DECLINE_PREFIX + promise.id());

        assertThat(transfers).isEmpty();
        assertThat(stored().status()).isEqualTo(Promise.Status.DECLINED);
    }

    @Test
    void thePromiserCannotAcceptItsOwnOffer() {
        PromiseDealButtons.accept(event, game, test.nekro, PromiseDeals.ACCEPT_PREFIX + promise.id());

        assertThat(transfers).isEmpty();
        assertThat(stored().status()).isEqualTo(Promise.Status.OFFERED);
    }

    @Test
    void aBuyerShortOfTradeGoodsChangesNothing() {
        sol.setTg(1);

        PromiseDealButtons.accept(event, game, sol, PromiseDeals.ACCEPT_PREFIX + promise.id());

        assertThat(transfers).isEmpty();
        assertThat(stored().status()).isEqualTo(Promise.Status.OFFERED);
    }

    // Without Lodor, Sol's home sits two systems from Nekro's: they are not neighbours, so the trade goods cannot
    // change hands in the action phase (LRR 94.1) and accepting changes nothing.
    @Test
    void strangersCannotPayForAPromise() {
        sol.removePlanet("lodor");
        game.removeTile(lodor.getPosition());
        game.setLastModifiedDate(game.getLastModifiedDate() + 1);

        PromiseDealButtons.accept(event, game, sol, PromiseDeals.ACCEPT_PREFIX + promise.id());

        assertThat(transfers).isEmpty();
        assertThat(stored().status()).isEqualTo(Promise.Status.OFFERED);
    }

    @Test
    void anOfferFromAnEarlierRoundIsClosed() {
        game.setRound(game.getRound() + 1);

        PromiseDealButtons.accept(event, game, sol, PromiseDeals.ACCEPT_PREFIX + promise.id());

        assertThat(transfers).isEmpty();
    }

    // Agenda phase: Sol accepts Nekro's conditional offer of 2 trade goods for Sol's votes for Nekro. Nothing changes
    // hands on accepting; the promise is recorded and paid after the agenda, only if it resolves for Nekro.
    @Test
    void acceptingAConditionalVoteDealPaysNothingYet() {
        game.setPhaseOfGame("agendawaiting");
        test.nekro.setTg(2);
        game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        Promise votes = Promise.offer(
                game,
                Promise.Term.VOTE_FOR,
                sol,
                test.nekro,
                test.nekro,
                ti4.ai.agenda.PromisedVotes.agendaKey(game),
                "nekro",
                0,
                2,
                10,
                600_000);
        PromiseBook.save(game, votes);

        PromiseDealButtons.accept(event, game, sol, PromiseDeals.ACCEPT_PREFIX + votes.id());

        assertThat(transfers).isEmpty();
        assertThat(PromiseBook.find(game, votes.id()).orElseThrow().status()).isEqualTo(Promise.Status.ACCEPTED);
    }

    // Sol owes Nekro 2 trade goods for a deal that went through: pressing its pay button moves them (the stand-in
    // transaction takes 2 from Sol) and closes the
    // debt. Only the seat that owes can pay, and only once.
    @Test
    void payingWhatIsOwedMovesTheTradeGoodsOnce() {
        game.setPhaseOfGame("agendawaiting");
        Promise owed = Promise.owed(game, Promise.Term.DISCARD_AGENDA, test.nekro, sol, "minister_war", "Veto", 2, 20);
        PromiseBook.save(game, owed);

        PromiseDealButtons.pay(event, game, test.nekro, PromiseDeals.PAY_PREFIX + owed.id());
        assertThat(sol.getTg()).isEqualTo(3);

        PromiseDealButtons.pay(event, game, sol, PromiseDeals.PAY_PREFIX + owed.id());
        PromiseDealButtons.pay(event, game, sol, PromiseDeals.PAY_PREFIX + owed.id());

        assertThat(sol.getTg()).isEqualTo(1);
        assertThat(PromiseBook.find(game, owed.id()).orElseThrow().status()).isEqualTo(Promise.Status.PAID);
    }
}
