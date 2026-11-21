package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Trade Convoys lets its holder transact with players who are not its neighbours. Nekro (the AI), Sol (a human),
 * Letnev (a human) and Hacan (an AI) sit two systems apart from each other, so only Hacan, with Guild Ships, can
 * trade with everyone. Hacan holds its own Trade Convoys and Trade Agreement.
 */
class ConvoysValueTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private TradeTable table;
    private Player hacan;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        hacan = table.aiSeat("hacan", "red");
        Player letnev = table.humanSeat("letnev", "yellow");
        table.presence(table.nekro, TradeTable.NEKRO_SPOT);
        table.presence(table.sol, TradeTable.SOL_SPOT);
        table.presence(hacan, TradeTable.FAR_SPOT);
        table.presence(letnev, TradeTable.FARTHER_SPOT);
        hacan.addOwnedPromissoryNoteByID("convoys");
        hacan.setPromissoryNote("convoys", 1);
        hacan.addOwnedPromissoryNoteByID("red_ta");
        hacan.setPromissoryNote("red_ta", 2);
    }

    // Sol could newly trade with Nekro and Letnev, and Nekro (an AI, which plays the note as a component action) with
    // Sol and Letnev: 0.5 each.
    @Test
    void isWorthHalfATradeGoodPerNewPartner() {
        assertThat(ConvoysValue.newPartners(table.game, table.sol)).isEqualTo(2);
        assertThat(NotesForTrade.receiveValue(table.game, table.sol, "convoys")).isCloseTo(1.0, within(EXACT));
        assertThat(NotesForTrade.receiveValue(table.game, table.nekro, "convoys"))
                .isCloseTo(1.0, within(EXACT));
    }

    // Guild Ships already let Hacan trade with everyone, so giving Convoys away costs it little: it is the note Hacan
    // gives first when a deal needs a sweetener or "any note", ahead of its Trade Agreement.
    @Test
    void isTheCheapestNoteForHacanToGive() {
        assertThat(NotesForTrade.giveCost(table.game, hacan, table.sol, "convoys"))
                .isCloseTo(ConvoysValue.GIVE_COST, within(EXACT));
        assertThat(NotesForTrade.leastHarmful(table.game, hacan, table.sol)).contains("convoys");
        assertThat(NotesForTrade.receiveValue(table.game, hacan, "convoys"))
                .isCloseTo(ConvoysValue.OWN_BACK, within(EXACT));
    }

    // Every version of the note counts.
    @Test
    void recognisesEveryVersion() {
        assertThat(ConvoysValue.isConvoys("convoys")).isTrue();
        assertThat(ConvoysValue.isConvoys("sigma_trade_convoys")).isTrue();
        assertThat(ConvoysValue.isConvoys("viability_trade_convoys")).isTrue();
        assertThat(ConvoysValue.isConvoys("red_ta")).isFalse();
    }
}
