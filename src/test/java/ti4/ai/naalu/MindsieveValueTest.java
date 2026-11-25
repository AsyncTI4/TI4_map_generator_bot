package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.expedition.BreakthroughValue;
import ti4.ai.expedition.ExpeditionOutlook;
import ti4.ai.expedition.ExpeditionStats;
import ti4.ai.expedition.Holdings;
import ti4.ai.strategy.CardValueScale;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Mindsieve lets Naalu follow another player's strategy card by giving them a promissory note instead of spending a
// strategy token. About 1.2 follows a round, each saving a held token (2.4) for a note (1.0), over the rounds left:
// worth more than the Hacan and Letnev priority floor early on. Human win statistics alone put it near 1.25, less than
// any expedition slice costs, so Naalu used to never claim one.
class MindsieveValueTest extends BaseTi4Test {

    private AiTestGame test;
    private Player naalu;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        test.nekro.addBreakthrough("nekrobt");
        naalu = test.addSeat("200000000000000010", "naalu", "yellow");
        naalu.addBreakthrough(MindsieveFollow.BREAKTHROUGH);
    }

    private void sevenResources(Player seat) {
        test.place("38", "201");
        seat.addPlanet("abyz");
        seat.addPlanet("mordaiii");
    }

    // Nobody has scored yet, so five rounds are left: 1.2 * (2.4 - 1.0) * 5 = 8.4.
    @Test
    void isWorthTheTokensItSavesOverTheRoundsLeft() {
        double perFollow = CardValueScale.HELD_TOKEN - MindsieveFollow.NOTE_GIVEN;

        assertThat(MindsieveFollow.breakthroughValue(test.game, naalu))
                .isCloseTo(MindsieveFollow.FOLLOWS_PER_ROUND * perFollow * 5, within(1e-9));
    }

    // It ranks with the highest-valued breakthroughs (the Hacan and Letnev floor), far above what the human win
    // statistics alone give it, and above any other faction priced only by those statistics.
    @Test
    void naaluValuesItAtLeastAsHighlyAsThePriorityBreakthroughs() {
        double value = BreakthroughValue.of(test.game, naalu);
        double statsOnly = BreakthroughValue.BASE
                + BreakthroughValue.PER_WIN_POINT * ExpeditionStats.breakthroughLiftPoints("naalu");

        assertThat(BreakthroughValue.isPriority(naalu)).isTrue();
        assertThat(value).isGreaterThanOrEqualTo(BreakthroughValue.PRIORITY_FLOOR);
        assertThat(value).isGreaterThan(statsOnly);
        assertThat(value).isGreaterThan(BreakthroughValue.of(test.game, test.nekro));
    }

    // Every expedition slice Naalu can pay for is worth taking, even the one that exhausts seven resources while turns
    // remain, and it claims at once instead of waiting to pass. Nekro, with the same planets, does not.
    @Test
    void naaluClaimsAnExpeditionSliceAtOnceWhenItCanPay() {
        sevenResources(naalu);
        sevenResources(test.nekro);

        assertThat(ExpeditionOutlook.options(test.game, naalu, Holdings.of(test.game, naalu)))
                .isNotEmpty()
                .allSatisfy(choice -> {
                    assertThat(choice.breakthrough()).isGreaterThanOrEqualTo(BreakthroughValue.PRIORITY_FLOOR);
                    assertThat(choice.breakthrough()).isGreaterThan(choice.cost());
                });
        assertThat(ExpeditionOutlook.claimNow(test.game, naalu))
                .hasValueSatisfying(choice -> assertThat(choice.net()).isPositive());
        assertThat(ExpeditionOutlook.claimNow(test.game, test.nekro)).isEmpty();
    }

    @Test
    void naaluClaimsTheTradeGoodsSliceWithThreeTradeGoods() {
        naalu.setTg(3);

        assertThat(ExpeditionOutlook.claimNow(test.game, naalu)).isPresent();
    }

    @Test
    void isWorthNothingOnceUnlocked() {
        naalu.setBreakthroughUnlocked(MindsieveFollow.BREAKTHROUGH, true);

        assertThat(MindsieveFollow.breakthroughValue(test.game, naalu)).isZero();
        assertThat(BreakthroughValue.of(test.game, naalu)).isZero();
    }

    @Test
    void isWorthNothingToASeatWithoutIt() {
        assertThat(MindsieveFollow.breakthroughValue(test.game, test.nekro)).isZero();
    }

    // Late in the game few follows remain: with the leader at 8 of 10 points, one round is left.
    @Test
    void losesValueAsTheGameNearsItsEnd() {
        double early = MindsieveFollow.breakthroughValue(test.game, naalu);
        test.game.setVp(10);
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Test points", 8));

        assertThat(MindsieveFollow.breakthroughValue(test.game, naalu)).isLessThan(early);
    }
}
