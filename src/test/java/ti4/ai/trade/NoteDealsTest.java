package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.brain.AiTurnContext;
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
        table.game.setRound(4);
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

    private void moreSeatsWithNotes() {
        Player hacan = table.aiSeat("hacan", "red");
        Player letnev = table.humanSeat("letnev", "green");
        table.presence(hacan, TradeTable.BESIDE_NEKRO);
        table.presence(letnev, TradeTable.BESIDE_NEKRO);
        for (Player player : List.of(hacan, letnev)) {
            String colour = player.getColor();
            for (int index = 0; index < SUFFIXES.length; index++) {
                player.addOwnedPromissoryNoteByID(colour + SUFFIXES[index]);
                player.setPromissoryNote(colour + SUFFIXES[index], index + 21);
            }
        }
        table.stock(hacan, 3, 5);
        table.stock(letnev, 2, 6);
        table.stock(nekro, 3, 7);
        table.stock(sol, 4, 4);
        table.points(hacan, 2);
        table.points(letnev, 4);
    }

    private List<NoteDeals.Option> reference(List<Player> partners) {
        AiTurnContext context = table.context();
        List<NoteDeals.Option> options = new ArrayList<>();
        for (Player partner : partners) {
            double trust = Trust.of(context, partner);
            int spare = TradeBudget.spareTradeGoods(table.game, nekro, partner);
            ToIntFunction<String> floor = alias -> NoteSecrets.chases(table.game, nekro, alias)
                    ? NoteSecrets.askFloor(context.memory(), table.game, nekro, partner, alias)
                    : 1;
            for (Deal deal : NoteDeals.candidates(table.game, nekro, partner, spare, floor)) {
                if (!TradeValue.coverable(table.game, nekro, partner, deal)) continue;
                if (!TradeValue.proposable(table.game, nekro, partner, deal, trust)) continue;
                double utility =
                        TradeValue.of(table.game, nekro, partner, deal, trust).utility();
                options.add(new NoteDeals.Option(partner, deal, utility));
            }
        }
        options.sort(Comparator.comparingDouble(NoteDeals.Option::utility).reversed());
        return options;
    }

    // The memoised search must rank exactly what evaluating every deal from scratch ranks.
    @Test
    void searchMatchesEvaluatingEveryDealFromScratch() {
        moreSeatsWithNotes();
        List<Player> partners = table.game.getRealPlayers().stream()
                .filter(player -> player != nekro)
                .toList();

        List<NoteDeals.Option> expected = reference(partners);
        List<NoteDeals.Option> actual = NoteDeals.best(table.context(), partners);

        assertThat(partners).hasSizeGreaterThan(2);
        // Selling Support for the Throne for a few trade goods is vetoed, so swaps and purchases make up the list.
        assertThat(expected).hasSizeGreaterThan(8);
        assertThat(actual).hasSameSizeAs(expected);
        for (int index = 0; index < expected.size(); index++) {
            assertThat(actual.get(index).partner())
                    .isEqualTo(expected.get(index).partner());
            assertThat(actual.get(index).deal()).isEqualTo(expected.get(index).deal());
            assertThat(actual.get(index).utility())
                    .isCloseTo(expected.get(index).utility(), within(1e-9));
        }
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

    // Early on, Nekro's unlocked Alliance is worth 2 to Sol by the table: it sells for a trade good.
    @Test
    void sellsAnUnlockedAllianceForATradeGood() {
        table.game.setRound(2);
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
