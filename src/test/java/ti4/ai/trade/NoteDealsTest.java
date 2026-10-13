package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Which promissory note deals the AI puts to another seat on its own turn. Nekro (black) and Sol (blue) are
 * neighbouring AI seats that each own and hold their five colour notes; Nekro holds Support for the Throne as hand
 * number 1, Alliance as 2, Trade Agreement 3, Political Secret 4 and Ceasefire 5.
 */
class NoteDealsTest extends BaseTi4Test {

    private static final String[] SUFFIXES = {"_sftt", "_an", "_ta", "_ps", "_cf"};

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        nekro = table.nekro;
        sol = table.sol;
        table.nekroAndSolNeighbour();
        for (int index = 0; index < SUFFIXES.length; index++) {
            nekro.addOwnedPromissoryNoteByID("black" + SUFFIXES[index]);
            nekro.setPromissoryNote("black" + SUFFIXES[index], index + 1);
            sol.addOwnedPromissoryNoteByID("blue" + SUFFIXES[index]);
            sol.setPromissoryNote("blue" + SUFFIXES[index], index + 11);
        }
        table.stock(nekro, 3, 0);
        table.stock(sol, 4, 0);
        table.points(nekro, 3);
        table.points(sol, 3);
    }

    private List<NoteDeals.Option> options() {
        return NoteDeals.best(table.context(), List.of(sol));
    }

    private static boolean gives(Deal deal, String sender, String detail) {
        return deal.notes(sender).contains(detail);
    }

    // Two level peers both gain a point by swapping Support for the Throne: that beats selling it.
    @Test
    void swapsSupportForTheThroneWithAPeer() {
        table.stock(sol, 4, 4);

        NoteDeals.Option best = options().getFirst();

        assertThat(gives(best.deal(), "nekro", "1")).isTrue();
        assertThat(gives(best.deal(), "sol", "bluefin9sftt")).isTrue();
    }

    // A leader is fed no points: no deal hands it Nekro's Support for the Throne.
    @Test
    void neverHandsTheLeaderAPoint() {
        table.points(sol, 4);
        table.stock(sol, 4, 4);

        assertThat(options()).noneMatch(option -> gives(option.deal(), "nekro", "1"));
    }

    // A Support for the Throne already face up in someone's play area cannot be asked for.
    @Test
    void asksOnlyForNotesStillInTheirOwnersHand() {
        table.humanSeat("letnev", "red").getPromissoryNotesInPlayArea().add("blue_sftt");

        assertThat(options()).noneMatch(option -> gives(option.deal(), "sol", "bluefin9sftt"));
    }

    // Without a threat to either side, a Ceasefire stays home.
    @Test
    void keepsItsCeasefireWithoutDuress() {
        table.stock(sol, 4, 4);

        assertThat(options()).noneMatch(option -> gives(option.deal(), "nekro", "5"));
    }

    // An unlocked commander makes Nekro's Alliance worth a trade good to Sol.
    @Test
    void sellsAnUnlockedAllianceForATradeGood() {
        nekro.unsafeGetLeader("nekrocommander").setLocked(false);
        table.stock(sol, 4, 1);

        assertThat(options())
                .anyMatch(option -> gives(option.deal(), "nekro", "2")
                        && option.deal().total("sol", ItemType.TRADE_GOODS) == 1
                        && option.deal().items().size() == 2);
    }

    // Selling Support for the Throne to a seat about to win is never on the table: the point would win it the game.
    @Test
    void neverSellsTheWinningPoint() {
        table.points(sol, 6);
        Deal sale = Deal.EMPTY
                .with(new DealItem("nekro", "sol", ItemType.PROMISSORY, "1"))
                .adjust("sol", "nekro", ItemType.TRADE_GOODS, 4);

        assertThat(Stinginess.vetoes(table.game, nekro, sol, sale)).contains(Stinginess.Veto.WIN);
    }
}
