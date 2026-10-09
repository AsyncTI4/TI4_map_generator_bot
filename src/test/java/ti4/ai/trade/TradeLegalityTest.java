package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class TradeLegalityTest extends BaseTi4Test {

    private TradeTable table;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        table.presence(table.nekro, TradeTable.NEKRO_SPOT);
        table.presence(table.sol, TradeTable.SOL_SPOT);
    }

    private static Deal deal(String... raws) {
        return new Deal(List.of(raws).stream()
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private boolean canTransact() {
        return TradeLegality.canTransact(table.game, table.nekro, table.sol);
    }

    @Test
    void neighboursMayTransactInTheActionPhase() {
        assertThat(canTransact()).isFalse();

        table.presence(table.sol, TradeTable.BESIDE_NEKRO);

        assertThat(canTransact()).isTrue();
        assertThat(TradeLegality.neighbours(table.game, table.sol, table.nekro)).isTrue();
        assertThat(TradeLegality.legalPartners(table.game, table.nekro)).containsExactly(table.sol);
    }

    // Outside the action phase the neighbour rule is lifted (the agenda phase is when debts get settled).
    @Test
    void anyoneMayTransactInTheAgendaPhase() {
        table.game.setPhaseOfGame("agendawaiting");

        assertThat(canTransact()).isTrue();
        assertThat(TradeLegality.canTransactInActionPhase(table.game, table.nekro, table.sol))
                .isFalse();
    }

    // Hacan's Guild Ships let it transact with everyone.
    @Test
    void guildShipsLiftTheNeighbourRule() {
        Player hacan = table.humanSeat("hacan", "red");
        table.presence(hacan, TradeTable.FAR_SPOT);

        assertThat(TradeLegality.canTransact(table.game, table.nekro, hacan)).isTrue();
        assertThat(TradeLegality.canTransact(table.game, hacan, table.sol)).isTrue();
    }

    // Each version of Trade Convoys lifts the rule for whoever has it in their play area, in both directions.
    @Test
    void eachConvoysNoteLiftsTheNeighbourRule() {
        for (String convoys : List.of("convoys", "sigma_trade_convoys", "viability_trade_convoys")) {
            table.sol.getPromissoryNotesInPlayArea().clear();
            table.sol.getPromissoryNotesInPlayArea().add(convoys);

            assertThat(canTransact()).as(convoys).isTrue();
            assertThat(TradeLegality.canTransact(table.game, table.sol, table.nekro))
                    .as(convoys)
                    .isTrue();
        }
    }

    // Censure blocks every transaction of the censured player, even with a neighbour.
    @Test
    void censureBlocksTransactions() {
        table.presence(table.sol, TradeTable.BESIDE_NEKRO);
        table.game.addLaw("tf-censure", "sol");

        assertThat(canTransact()).isFalse();
        assertThat(TradeLegality.legalPartners(table.game, table.nekro)).isEmpty();
    }

    // Debt is the bot's ledger, so a deal that only moves debt is always fine; anything else needs the neighbour.
    @Test
    void debtOnlyDealsAreAlwaysLegal() {
        Deal debt = deal("sendingnekro_receivingsol_SendDebt_2");
        Deal wash = deal("sendingnekro_receivingsol_Comms_2", "sendingsol_receivingnekro_Comms_2");

        assertThat(TradeLegality.isLegal(table.game, table.nekro, table.sol, debt))
                .isTrue();
        assertThat(TradeLegality.isLegal(table.game, table.nekro, table.sol, wash))
                .isFalse();
    }

    // The window is "one transaction per partner per turn" in the action phase, per agenda in the agenda phase.
    @Test
    void windowKeysFollowThePhase() {
        String turn = TradeLegality.window(table.game);
        assertThat(turn).startsWith("turn|" + AiTestGame.AI_ID + "@");

        table.game.setLastActivePlayerChange(new Date(AiTestGame.NOW + 60_000));
        assertThat(TradeLegality.window(table.game)).isNotEqualTo(turn);

        table.game.setPhaseOfGame("strategy");
        assertThat(TradeLegality.window(table.game)).isEqualTo("phase|3|strategy");

        table.game.setPhaseOfGame("agenda");
        table.game.setStoredValue("agendaCount", "1");
        table.game.setCurrentAgendaInfo("Law_For/Against_1_minister_commerce");
        String firstAgenda = TradeLegality.window(table.game);
        assertThat(firstAgenda).startsWith("agenda|3|1|");

        table.game.setStoredValue("agendaCount", "2");
        assertThat(TradeLegality.window(table.game)).isNotEqualTo(firstAgenda);
    }

    // A pending offer lives until its key changes: an IOU fee until the round ends, others until the action phase
    // (or the agenda) ends.
    @Test
    void openKeysFollowThePurpose() {
        Deal iou = deal("sendingsol_receivingnekro_SendDebt_1");
        Deal fee = deal("sendingsol_receivingnekro_TGs_1");

        assertThat(TradeLegality.openKey(table.game, Purpose.SETTLEMENT_FEE, iou))
                .isEqualTo("round|3");
        assertThat(TradeLegality.openKey(table.game, Purpose.SETTLEMENT_FEE, fee))
                .isEqualTo("action|3");
        assertThat(TradeLegality.openKey(table.game, Purpose.WASH, fee)).isEqualTo("action|3");

        table.game.setPhaseOfGame("agenda");
        assertThat(TradeLegality.openKey(table.game, Purpose.WASH, fee)).isEqualTo(TradeLegality.window(table.game));
    }

    @Test
    void oneDealPerPartnerPerWindow() {
        Player letnev = table.humanSeat("letnev", "red");
        AiTurnContext context = table.context();

        TradeLegality.useWindow(context, table.sol, Purpose.WASH);

        assertThat(TradeLegality.windowUsed(context, table.sol)).isTrue();
        assertThat(TradeLegality.windowUsed(context, letnev)).isFalse();

        table.game.setLastActivePlayerChange(new Date(AiTestGame.NOW + 60_000));
        assertThat(TradeLegality.windowUsed(table.context(), table.sol)).isFalse();
    }

    // Settlements, counters and clean-ups are replies or obligations: they do not use up the window.
    @Test
    void repliesDoNotUseTheWindow() {
        AiTurnContext context = table.context();
        for (Purpose purpose : List.of(Purpose.SETTLEMENT, Purpose.SETTLEMENT_FEE, Purpose.COUNTER, Purpose.CLEANUP)) {
            TradeLegality.useWindow(context, table.sol, purpose);
        }

        assertThat(TradeLegality.windowUsed(context, table.sol)).isFalse();
    }

    // Neighbour sets are expensive, so they are cached per game until it is saved again; the cache never touches
    // the AI's memory, which evicts its oldest keys.
    @Test
    void cachesNeighboursUntilTheGameChanges() throws ReflectiveOperationException {
        AiTurnContext context = table.context();
        assertThat(canTransact()).isFalse();

        table.test.units(table.test.place("46", TradeTable.BESIDE_NEKRO), "space", table.sol, UnitType.Destroyer, 1);
        assertThat(canTransact()).isFalse();

        table.changed();
        assertThat(canTransact()).isTrue();
        assertThat(TradeLegality.legalPartners(context.game(), context.seat())).containsExactly(table.sol);
        assertThat(entries(context.memory())).isZero();
    }

    private static int entries(AiMemory memory) throws ReflectiveOperationException {
        Field values = AiMemory.class.getDeclaredField("values");
        values.setAccessible(true);
        return ((Map<?, ?>) values.get(memory)).size();
    }
}
