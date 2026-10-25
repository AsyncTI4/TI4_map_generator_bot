package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Which relic fragment deals the AI puts to another seat on its own turn. A fragment is worth 2 trade goods, and 3
 * when it is the third of a kind; Nekro (black) and Sol (blue) are neighbouring AI seats.
 */
class FragmentDealsTest extends BaseTi4Test {

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
        table.stock(nekro, 3, 0);
        table.stock(sol, 4, 0);
        table.points(nekro, 3);
        table.points(sol, 3);
    }

    private List<FragmentDeals.Option> options() {
        return FragmentDeals.best(table.context(), List.of(sol));
    }

    // Each side holds two of a kind and a lone fragment of the other's: swapping the lone ones completes both sets.
    @Test
    void swapsLoneFragmentsThatCompleteBothSets() {
        nekro.setCrf(2);
        nekro.setIrf(1);
        sol.setIrf(2);
        sol.setCrf(1);

        FragmentDeals.Option best = options().getFirst();

        assertThat(best.deal().fragments("nekro", "IRF")).isEqualTo(1);
        assertThat(best.deal().fragments("sol", "CRF")).isEqualTo(1);
        assertThat(best.utility()).isGreaterThan(0.5);
    }

    // A lone fragment is worth a fifth of a relic to its holder, so it is never given away; the buyer it completes
    // a set for pays more, and both come out ahead.
    @Test
    void sellsALoneFragmentThatCompletesTheBuyersSetForThree() {
        nekro.setIrf(1);
        sol.setIrf(2);
        table.stock(sol, 4, 4);

        assertThat(options())
                .noneMatch(option -> option.deal().fragments("nekro", "IRF") > 0
                        && option.deal().total("sol", ItemType.TRADE_GOODS) == 0);
        assertThat(options())
                .anyMatch(option -> option.deal().fragments("nekro", "IRF") == 1
                        && option.deal().total("sol", ItemType.TRADE_GOODS) >= 1);
    }

    // The same holds buying: the last fragment of a set is bought for a few trade goods when the seller has no use for
    // it.
    @Test
    void buysTheLastFragmentOfASetForThree() {
        nekro.setCrf(2);
        sol.setCrf(1);
        table.stock(nekro, 3, 5);

        assertThat(options())
                .anyMatch(option -> option.deal().fragments("sol", "CRF") == 1
                        && option.deal().total("nekro", ItemType.TRADE_GOODS) >= 1);
    }

    // The fragments of a complete set are the AI's relic: no deal gives one away for nothing or a single trade good.
    @Test
    void keepsTheFragmentsOfACompleteSet() {
        nekro.setCrf(3);
        sol.setIrf(2);
        sol.setHrf(2);
        table.stock(sol, 4, 4);

        assertThat(options())
                .noneMatch(option -> option.deal().fragments("nekro", "CRF") > 0
                        && option.deal().total("sol", ItemType.TRADE_GOODS) <= 1);
    }

    // Holding nothing and asked for nothing, there is nothing to propose.
    @Test
    void proposesNothingWithoutFragments() {
        assertThat(options()).isEmpty();
    }
}
