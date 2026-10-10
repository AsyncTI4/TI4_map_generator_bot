package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class DesperationTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private TradeTable table;

    // Nekro controls its home system, so it may score public objectives.
    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        table.test.nekroHome();
    }

    // A point is worth 6 trade goods (Negotiate Trade Routes buys one for 5), 2 more for each point past goal - 4,
    // and 2 more again once anyone is within 2 points of winning; never more than 12.
    @Test
    void victoryPointWorthRisesNearTheEnd() {
        assertThat(Desperation.vpWorth(table.game, table.nekro)).isCloseTo(6, within(EXACT));

        table.points(table.nekro, 7);
        assertThat(Desperation.vpWorth(table.game, table.nekro)).isCloseTo(8, within(EXACT));
        assertThat(Desperation.vpWorth(table.game, table.sol)).isCloseTo(6, within(EXACT));

        table.points(table.sol, 8);
        assertThat(Desperation.vpWorth(table.game, table.sol)).isCloseTo(12, within(EXACT));
        assertThat(Desperation.vpWorth(table.game, table.nekro)).isCloseTo(10, within(EXACT));
    }

    @Test
    void cappedAtTwelve() {
        table.points(table.sol, 9);

        assertThat(Desperation.vpWorth(table.game, table.sol)).isCloseTo(12, within(EXACT));
    }

    // A point that wins the game is worth far more than any trade could cost.
    @Test
    void aWinningScoreIsWorthThirty() {
        table.points(table.nekro, 9);

        assertThat(Desperation.scoreValue(table.game, table.nekro, 1)).isCloseTo(30, within(EXACT));
        assertThat(Desperation.scoreValue(table.game, table.sol, 1)).isCloseTo(8, within(EXACT));
        assertThat(Desperation.scoreValue(table.game, table.sol, -1)).isCloseTo(-8, within(EXACT));
    }

    // Trade goods gained in the strategy or agenda phase only help next round; nothing is traded for in the status
    // phase.
    @Test
    void phaseFactor() {
        assertThat(Desperation.phaseFactor(table.game)).isCloseTo(1.0, within(EXACT));
        for (String later : new String[] {"strategy", "agenda", "agendawaiting", "agendaVoting"}) {
            table.game.setPhaseOfGame(later);
            assertThat(Desperation.phaseFactor(table.game)).as(later).isCloseTo(0.5, within(EXACT));
        }
        table.game.setPhaseOfGame("statusScoring");
        assertThat(Desperation.phaseFactor(table.game)).isCloseTo(0, within(EXACT));
    }

    // Negotiate Trade Routes needs 5 trade goods: with 3 the seat is 2 short.
    @Test
    void twoShortOfTradeRoutes() {
        table.reveal("trade_routes");
        table.nekro.setTg(3);

        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).hasValue(2);
        assertThat(Desperation.term(table.game, table.nekro, 2)).isCloseTo(6, within(EXACT));
        assertThat(Desperation.term(table.game, table.nekro, 1)).isCloseTo(0, within(EXACT));
    }

    // Buying 4 trade goods is more than a trade should try for.
    @Test
    void fourShortIsTooFar() {
        table.reveal("trade_routes");
        table.nekro.setTg(1);

        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).isEmpty();
    }

    @Test
    void notShortWhenItCanAlreadyPay() {
        table.reveal("trade_routes");
        table.nekro.setTg(5);

        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).isEmpty();
    }

    // Develop Weaponry (two unit upgrades) is already scorable and worth as much, and the seat may only score one
    // public objective this round: Trade Routes would add nothing, so there is no desperation.
    @Test
    void anEquallyGoodFreeObjectiveCoversTheScoringChance() {
        table.reveal("trade_routes");
        table.nekro.setTg(3);
        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).hasValue(2);

        table.reveal("develop");
        table.nekro.addTech("dd2");
        table.nekro.addTech("cr2");

        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).isEmpty();
    }

    // Outside the action phase there is no buying for a score.
    @Test
    void onlyInTheActionPhase() {
        table.reveal("trade_routes");
        table.nekro.setTg(3);
        table.game.setPhaseOfGame("agenda");

        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).isEmpty();
    }

    // Losing trade goods the seat keeps back for a scheduled score costs that score.
    @Test
    void givingAwayReservedTradeGoodsCostsThePoint() {
        table.reveal("trade_routes");
        table.nekro.setTg(5);

        assertThat(Desperation.term(table.game, table.nekro, -2)).isCloseTo(-6, within(EXACT));
    }

    @Test
    void spendCapIsThreeQuartersOfThePoint() {
        Player nekro = table.nekro;

        assertThat(Desperation.spendCap(table.game, nekro, 1)).isCloseTo(4.5, within(EXACT));
    }
}
